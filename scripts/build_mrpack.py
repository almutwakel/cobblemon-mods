#!/usr/bin/env python3
"""Build Cobblemon Server-0.3.7.mrpack.

Seeds from 0.3.5 and adds cobblemon-carrots (carrot-based healing overhaul). No new
Modrinth mods in this version — pure in-house override additions.
"""
import json, os, shutil, subprocess, urllib.request
from pathlib import Path

WORK = Path('/tmp/mrpack-work')
SRC_PACK = Path('/Users/almutwakel/Documents/Projects/minecraft/Cobblemon Server-0.3.22.mrpack')
OUT_VERSION = '0.3.23'
OUT_PATH = Path('/Users/almutwakel/Documents/Projects/minecraft/Cobblemon Server-0.3.23.mrpack')
SERVER_MODS = Path('/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/mods')

# No new Modrinth entries in 0.3.6. (When bumping a future version that adds Modrinth mods,
# append entries here; the script asserts they aren't already in the seed pack.)
# (slug, version_id, env_client, env_server, expected_filename)
NEW_MODRINTH = []

# In-house jars to copy into overrides/mods/. cobblemon-carrots is new in 0.3.6; the others
# refresh in place (same filename, updated content).
IN_HOUSE_JARS = [
    ('cobblemon-market-1.0.0.jar',   SERVER_MODS / 'cobblemon-market-1.0.0.jar'),
    ('cobblemon-ranked-1.0.0.jar',   SERVER_MODS / 'cobblemon-ranked-1.0.0.jar'),
    ('cobblemon-gacha-1.0.0.jar',    SERVER_MODS / 'cobblemon-gacha-1.0.0.jar'),
    ('cobblemon-bridge-1.0.0.jar',   SERVER_MODS / 'cobblemon-bridge-1.0.0.jar'),
    ('cobblemon-carrots-1.0.0.jar',  SERVER_MODS / 'cobblemon-carrots-1.0.0.jar'),
]

# ── Helpers ──────────────────────────────────────────────────────────────
def http_get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'cobblemon-mods-mrpack-builder/0.3.5'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()

def fetch_version_meta(slug, version_id):
    data = json.loads(http_get(f'https://api.modrinth.com/v2/version/{version_id}').decode())
    return next(f for f in data['files'] if f.get('primary'))

def file_entry(filename, primary, env_client, env_server):
    return {
        'path': f'mods/{filename}',
        'hashes': primary['hashes'],
        'env': {'client': env_client, 'server': env_server},
        'downloads': [primary['url']],
        'fileSize': primary['size'],
    }

# ── Step 1: extract the 0.3.4 pack as the seed ───────────────────────────
if WORK.exists():
    shutil.rmtree(WORK)
WORK.mkdir(parents=True)
subprocess.run(['unzip', '-q', str(SRC_PACK), '-d', str(WORK)], check=True)
print(f'Seeded {WORK} from {SRC_PACK.name}')

# ── Step 2: load + update the manifest ──────────────────────────────────
manifest_path = WORK / 'modrinth.index.json'
manifest = json.load(open(manifest_path))
manifest['versionId'] = OUT_VERSION

# Sanity: refuse to re-add a slug whose target filename is already present
already_present = {f['path'].split('/')[-1] for f in manifest['files']}
for slug, vid, ec, es, expected_fn in NEW_MODRINTH:
    if expected_fn in already_present:
        raise RuntimeError(f'{expected_fn} already in 0.3.4 manifest — drop from NEW_MODRINTH')

print('Fetching Modrinth metadata...')
for slug, vid, ec, es, expected_fn in NEW_MODRINTH:
    primary = fetch_version_meta(slug, vid)
    if primary['filename'] != expected_fn:
        raise RuntimeError(f'{slug} expected {expected_fn} got {primary["filename"]}')
    print(f'  + {primary["filename"]:60s} sha1={primary["hashes"]["sha1"][:8]}…')
    manifest['files'].append(file_entry(primary['filename'], primary, ec, es))

manifest['files'].sort(key=lambda f: f['path'].lower())
json.dump(manifest, open(manifest_path, 'w'), indent=4)

# ── Step 3: refresh + add in-house overrides ─────────────────────────────
overrides_mods = WORK / 'overrides' / 'mods'
overrides_mods.mkdir(parents=True, exist_ok=True)
for jar_name, src in IN_HOUSE_JARS:
    if not src.exists():
        raise RuntimeError(f'In-house jar missing: {src}')
    dst = overrides_mods / jar_name
    shutil.copy2(src, dst)
    print(f'  override: {jar_name:42s} ({dst.stat().st_size} bytes)')

# ── Step 3b: client-side defaults (options.txt, sodium config) ──────────
# Modrinth packs install overrides/ contents into the instance dir. For options.txt,
# the launcher only copies it on a *fresh* install — existing players keep their settings.
CLIENT_OVERRIDES = Path('/Users/almutwakel/Documents/Projects/minecraft/client-overrides')
if CLIENT_OVERRIDES.exists():
    for src in CLIENT_OVERRIDES.rglob('*'):
        if not src.is_file():
            continue
        rel = src.relative_to(CLIENT_OVERRIDES)
        dst = WORK / 'overrides' / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        print(f'  client:   {str(rel):42s} ({dst.stat().st_size} bytes)')

# ── Step 3c: server-side configs + datapacks ───────────────────────────
# Fresh server installs need our customized configs (starter kit, market lineup, etc.)
# and the quest/gym datapacks. We ship into `overrides/` (shared) rather than a separate
# `server-overrides/` because most launchers handle the shared folder uniformly; clients
# don't read server-side configs so they're harmless extras for client installs.
SERVER_ROOT = Path('/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server')
SERVER_PATHS = [
    # (relative source path, optional rename)
    'config/starterkit/kits/Default.txt',
    'config/starterkit.json5',
    'config/neoessentials/config.json',
    'config/neoessentials/tablist.json',
    'config/cobblemon-market/items.json',
    'config/cobblemon-carrots/config.json',
    'config/cobblemonalphas/config.json',
    'config/cobbreeding/main.json',
    'server.properties',
]
# Whole-tree copies (entire directory structure shipped)
SERVER_TREES = [
    'config/cobblemon-gacha',
    'world/datapacks/server-quests',
    'world/datapacks/server-gyms',
]

for rel in SERVER_PATHS:
    src = SERVER_ROOT / rel
    if not src.exists():
        print(f'  server SKIP (missing): {rel}')
        continue
    dst = WORK / 'overrides' / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f'  server:   {rel:50s} ({dst.stat().st_size} bytes)')

for rel in SERVER_TREES:
    src = SERVER_ROOT / rel
    if not src.exists():
        print(f'  server SKIP (missing tree): {rel}')
        continue
    dst = WORK / 'overrides' / rel
    if dst.exists(): shutil.rmtree(dst)
    shutil.copytree(src, dst)
    file_count = sum(1 for _ in dst.rglob('*') if _.is_file())
    print(f'  server:   {rel:50s} ({file_count} files)')

# ── Step 4: zip ─────────────────────────────────────────────────────────
if OUT_PATH.exists(): OUT_PATH.unlink()
subprocess.run(['find', str(WORK), '-exec', 'touch', '{}', '+'], check=True)
subprocess.run(
    ['zip', '-r', '-q', str(OUT_PATH), 'modrinth.index.json', 'overrides'],
    cwd=str(WORK), check=True,
)
print(f'\nWrote {OUT_PATH} ({OUT_PATH.stat().st_size:,} bytes)')
print(f'Total files in new manifest: {len(manifest["files"])}')
print(f'Total in-house overrides: {len(list(overrides_mods.iterdir()))}')
