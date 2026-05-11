import json, hashlib, os, shutil, urllib.request, sys
from pathlib import Path

WORK = Path('/tmp/mrpack-work')
OUT_VERSION = '0.3.3'
OUT_PATH = Path('/Users/almutwakel/Documents/Projects/minecraft/Cobblemon Server-0.3.3.mrpack')
SERVER_MODS = Path('/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/mods')
CLIENT_ONLY_MODS = Path('/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/client-only-mods')

# (modrinth_slug, version_id, env_client, env_server, expected_filename)
NEW_MODRINTH = [
    # 5 QOL mods (client+server, except chat_heads)
    ('chat-heads',            '8oDa7chj', 'required',    'unsupported', 'chat_heads-0.15.1-neoforge-1.21.jar'),
    ('what-are-they-up-to',   'uWr2aTW9', 'required',    'required',    'watut-neoforge-1.21.0-1.2.7.jar'),
    ('sophisticated-backpacks','ZMuJ1TI7','required',    'required',    'sophisticatedbackpacks-1.21.1-3.25.44.1736.jar'),
    ('sophisticated-core',    'FkvJPfcC', 'required',    'required',    'sophisticatedcore-1.21.1-1.4.38.1847.jar'),
    ('chatbubbles',           'zWKm3FWD', 'required',    'required',    'chatbubbles-1.0.1.jar'),
    ('cobblemon-linkie',      '7zgm9lJd', 'required',    'required',    'cobblemonlinkie-neoforge-1.7.3-1.1.0.jar'),
    # 3 Cobblemon gameplay mods
    ('cobbleworkers',         'G5XYidrt', 'required',    'required',    'cobbleworkers-neoforge-2.0.2+1.7.0.jar'),
    ('cobbreeding',           'xt8IiPEN', 'required',    'required',    'Cobbreeding-neoforge-2.2.1.jar'),
    ('cobblemon-unchained',   'I5oNveU5', 'required',    'required',    'unchained-neoforge-1.7.3-1.7.1.jar'),
    # transitive deps
    ('coroutil',              'H2YXCYUY', 'required',    'required',    'coroutil-neoforge-1.21.0-1.3.8.jar'),
    ('cobblemon-tim-core',    'QQO61rRS', 'required',    'required',    'timcore-neoforge-1.7.3-1.32.0.jar'),
    ('cobblemon-counter',     'aJArPPZ7', 'required',    'required',    'counter-neoforge-1.7.3-1.9.0.jar'),
    # cobblemon-economy is a Fabric mod; loaded on both server and client via Sinytra Connector.
    # Server registers shopkeeper entity_type / spawn_egg item and quest_board block+item, which
    # get synced on join — clients without it fail at registry sync.
    ('cobblemon-economy',     'CfJu3YAf', 'required',    'required',    'cobblemon-economy-0.0.17.jar'),
    # Xaero minimap + world map — upstream 0.2.1 shipped older builds that warn "outdated" at every
    # client launch. Replace with current Modrinth versions; old entries are filtered below.
    ('xaeros-minimap',        'CklXEjmp', 'required',    'required',    'xaerominimap-neoforge-1.21.1-25.3.13.jar'),
    ('xaeros-world-map',      'XwL25au3', 'required',    'required',    'xaeroworldmap-neoforge-1.21.1-1.40.16.jar'),
    # Server commands: /spawn, /home, /sethome, /tpa, /warp, /msg, /back, /afk, etc.
    ('essentialcommands',     'P4CE47vz', 'required',    'required',    'essentials-neoforge-1.0.0.jar'),
    # First-join starter kit. Auto-grants the kit named "default" to new players.
    ('starter-kit',           'tKHaJMww', 'required',    'required',    'starterkit-1.21.1-8.0.jar'),
    # Collective: shared Serilum lib required by starter-kit (and many other Serilum mods).
    ('collective',            '6xEh8Qbr', 'required',    'required',    'collective-1.21.1-8.22.jar'),
]

# Prefixes to drop from the base 0.2.1 manifest (replaced by entries above).
REPLACED_PREFIXES = [
    'mods/cobblemon_ranked-neoforge',   # upstream ranked, replaced by in-house override
    'mods/xaerominimap-neoforge-',      # outdated, replaced by 25.3.13 above
    'mods/xaeroworldmap-neoforge-',     # outdated, replaced by 1.40.16 above
]
# cloth_config is client-only (Cobbreeding declares side=CLIENT). Look it up.
# In-house mods to add as overrides
IN_HOUSE_JARS = [
    ('cobblemon-market-1.0.0.jar',    SERVER_MODS / 'cobblemon-market-1.0.0.jar'),
    ('cobblemon-ranked-1.0.0.jar',    SERVER_MODS / 'cobblemon-ranked-1.0.0.jar'),
    ('cobblemon-gacha-1.0.0.jar',     SERVER_MODS / 'cobblemon-gacha-1.0.0.jar'),
]

def http_get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'cobblemon-mods-mrpack-builder/0.3.0'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()

def fetch_version_meta(slug, version_id):
    url = f'https://api.modrinth.com/v2/version/{version_id}'
    data = json.loads(http_get(url).decode('utf-8'))
    primary = next(f for f in data['files'] if f.get('primary'))
    return primary  # has filename, url, hashes{sha1,sha512}, size

# Find cloth_config version that matches Cobbreeding's "[15,)" range on NeoForge 1.21.1
def fetch_cloth_config_version():
    url = 'https://api.modrinth.com/v2/project/cloth-config/version?loaders=%5B%22neoforge%22%5D&game_versions=%5B%221.21.1%22%5D'
    data = json.loads(http_get(url).decode('utf-8'))
    # Pick the newest version >= 15
    for v in data:
        if v['version_number'].split('-')[0].split('+')[0].split('.')[0].isdigit():
            major = int(v['version_number'].split('-')[0].split('+')[0].split('.')[0])
            if major >= 15:
                primary = next(f for f in v['files'] if f.get('primary'))
                return primary, v
    raise RuntimeError('No suitable cloth-config version found')

def file_entry(filename, primary, env_client, env_server):
    return {
        'path': f'mods/{filename}',
        'hashes': primary['hashes'],
        'env': {'client': env_client, 'server': env_server},
        'downloads': [primary['url']],
        'fileSize': primary['size'],
    }

# Load existing manifest
manifest = json.load(open(WORK / 'modrinth.index.json'))
# Drop entries replaced by this build (upstream cobblemon_ranked, outdated Xaero, etc.)
manifest['files'] = [
    f for f in manifest['files']
    if not any(f['path'].startswith(prefix) for prefix in REPLACED_PREFIXES)
]
# Bump version
manifest['versionId'] = OUT_VERSION

# Add Modrinth entries
print('Fetching Modrinth metadata...')
for slug, vid, ec, es, expected_fn in NEW_MODRINTH:
    primary = fetch_version_meta(slug, vid)
    print(f'  + {primary["filename"]:60s} sha1={primary["hashes"]["sha1"][:8]}…')
    manifest['files'].append(file_entry(primary['filename'], primary, ec, es))

# Add cloth_config (Cobbreeding's client-side dep)
print('Fetching cloth-config (client-only Cobbreeding dep)...')
cloth_primary, cloth_v = fetch_cloth_config_version()
print(f'  + {cloth_primary["filename"]:60s} (cloth_config {cloth_v["version_number"]})')
manifest['files'].append(file_entry(cloth_primary['filename'], cloth_primary, 'required', 'unsupported'))

# Sort the file list for diff readability
manifest['files'].sort(key=lambda f: f['path'].lower())

# Write updated manifest
with open(WORK / 'modrinth.index.json', 'w') as f:
    json.dump(manifest, f, indent=4)

# Copy in-house jars into overrides/mods/
overrides_mods = WORK / 'overrides' / 'mods'
overrides_mods.mkdir(parents=True, exist_ok=True)
# Remove any in-house jars from a prior 0.2.1 (cobblemon-npc stays — it's still in overrides)
for jar_name, src in IN_HOUSE_JARS:
    dst = overrides_mods / jar_name
    if not src.exists():
        raise RuntimeError(f'In-house jar missing: {src}')
    shutil.copy2(src, dst)
    print(f'  override: {jar_name} ({dst.stat().st_size} bytes)')

# Repackage. We use the `zip` CLI instead of shutil.make_archive because the upstream mrpack's
# files carry pre-1980 timestamps that Python's zipfile rejects, but `zip` accepts them.
import subprocess
if OUT_PATH.exists():
    OUT_PATH.unlink()
# Touch every file so timestamps are post-1980, then zip from inside the work dir.
subprocess.run(['find', str(WORK), '-exec', 'touch', '{}', '+'], check=True)
subprocess.run(
    ['zip', '-r', '-q', str(OUT_PATH), 'modrinth.index.json', 'overrides'],
    cwd=str(WORK), check=True,
)
print(f'Wrote {OUT_PATH} ({OUT_PATH.stat().st_size} bytes)')
print(f'Total files in new manifest: {len(manifest["files"])}')
