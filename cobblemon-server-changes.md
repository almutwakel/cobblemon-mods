# Cobblemon Server — Local Changes

Tracks deviations of the local `cobblemon-server/` install from the upstream `Cobblemon Server-0.2.1.mrpack`. Apply the same changes to clients (or release a new mrpack) when anything affects parity.

Server pack base: **MC 1.21.1 / NeoForge 21.1.227**, mrpack `Cobblemon Server-0.2.1.mrpack`.
Server install path: `/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/`.

## Mods

### 2026-05-11 — `Cobblemon Server-0.3.4.mrpack` — switched essentials from EssentialCommands to NeoEssentials
- **Output:** `Cobblemon Server-0.3.4.mrpack` (127 MB). Supersedes 0.3.3.
- **Swap:** removed `essentials-neoforge-1.0.0.jar` (EssentialCommands v1.0.0 by Doneon — brand-new mod, only one release). Added `neoessentials-1.0.2.5+build.1074.jar` (NeoEssentials by MrWhiteFlamesYT) — more mature (50+ commands, config-driven cooldowns/costs/limits, used on production servers). Marked as server-only in the manifest (`client = "unsupported"`); the mod authors confirm it registers no client-synced items/blocks, so vanilla clients connect cleanly. https://modrinth.com/mod/neoessentials/version/woCkFyUe
- **Other 0.3.3 additions (starter-kit + collective) carry over unchanged.**

### 2026-05-11 — `Cobblemon Server-0.3.3.mrpack` — Essential Commands + Starter Kit
- **Output:** `Cobblemon Server-0.3.3.mrpack` (127 MB). Supersedes 0.3.2.
- **Added (server + client):**
  - `essentials-neoforge-1.0.0.jar` (modId `rift_essentials`) — Essential Commands by Doneon. Adds `/spawn`, `/home`, `/sethome`, `/delhome`, `/homes`, `/back`, `/warp`, `/setwarp`, `/delwarp`, `/warps`, `/tpa`, `/tpaaccept`, `/tpadeny`, `/tppos`, `/tphere`, `/msg`, `/r`, `/afk`, `/near`, `/seen`, `/nick`, `/heal`, `/feed`, `/fly`, `/god`, `/repair`, `/workbench`, `/enderchest`, plus `/essentials reload`. Config at `config/essential.commands.toml`; per-player data at `world/data/rift_essentials.dat`. https://modrinth.com/mod/essentialcommands/version/P4CE47vz
  - `starterkit-1.21.1-8.0.jar` — Starter Kit by Serilum. Auto-grants a configured kit to new players on first join. Build kits in-game via `/sk set <name>` from the op's current inventory. Config in `config/starterkit/` and `config/starterkit.json5`. https://modrinth.com/mod/starter-kit/version/tKHaJMww
  - `collective-1.21.1-8.22.jar` — required Serilum shared lib for starter-kit. https://modrinth.com/mod/collective/version/6xEh8Qbr
- **Manual followups for the kit (workflow):** op logs in, assembles the kit inventory using `/give @s cobblemon:poke_ball 10`, `/give @s minecraft:iron_pickaxe`, `/give @s cobblemon:red_apricorn 3`, `/give @s sophisticatedbackpacks:backpack`, `/gacha admin grant <self> common 1`, `/give @s` for the Ash Ketchum hat (CobbleFurnies item id), and a written book as the wiki, then runs `/sk set default` to capture as the auto-granted kit.

### 2026-05-11 — `Cobblemon Server-0.3.2.mrpack` — Xaero updated, supersedes 0.3.1
- **Output:** `Cobblemon Server-0.3.2.mrpack` (127 MB). Discards the broken 0.3.0 and the still-warning 0.3.1.
- **Changes vs 0.3.1:**
  - `xaerominimap-neoforge-1.21.1-25.3.10.jar` → `25.3.13.jar` (latest 2026-05-06 release). Stops the "outdated Xaero's Minimap" warning that pops up on client launch.
  - `xaeroworldmap-neoforge-1.21.1-1.40.11.jar` → `1.40.16.jar` (latest 2026-05-05 release).
- **Server install also updated** — same jars dropped into `cobblemon-server/mods/`. Server-side Xaero is functionally identical (no-op for the most part); kept in sync so the mrpack and server agree on versions.
- **Script change:** `scripts/build_mrpack.py` now uses a `REPLACED_PREFIXES` list to drop entries from the base 0.2.1 manifest before adding their replacements (was a hardcoded `cobblemon_ranked-neoforge` filter; now configurable).

### 2026-05-11 — `Cobblemon Server-0.3.1.mrpack` adds `cobblemon-economy` (Fabric, via Connector)
- **Output:** `Cobblemon Server-0.3.1.mrpack` (127 MB) supersedes the broken 0.3.0.
- **Why 0.3.0 was broken:** the upstream Cobblemon Economy 0.0.17 Fabric jar was on the server but never made it into the client pack. Server registers `cobblemon-economy:shopkeeper` entity_type, `shopkeeper_spawn_egg` item, and `quest_board` block+item; the registry-sync on join rejected vanilla clients with "unknown keys" for all four. Added as a Modrinth-indexed Fabric jar in 0.3.1.
- **Audit done:** ran `comm -23 <(ls cobblemon-server/mods/) <(unzip -p ... + overrides)` — `cobblemon-economy-0.0.17.jar` was the only server jar missing from the 0.3.0 mrpack. No other gaps.
- **Script fix:** `scripts/build_mrpack.py` now uses `zip` CLI instead of `shutil.make_archive` to tolerate the pre-1980 timestamps the upstream mrpack carries.

### 2026-05-11 — Built `Cobblemon Server-0.3.0.mrpack` for client testing
- **Output:** `Cobblemon Server-0.3.0.mrpack` (127 MB) at repo root. Built by `/tmp/build_mrpack.py` from the previous 0.2.1 manifest.
- **Index changes vs 0.2.1:**
  - Removed `cobblemon_ranked-neoforge-1.4.2.jar` (Modrinth) — replaced by in-house build via `overrides/mods/`.
  - Added 13 Modrinth entries (the 5 QOL mods + 3 gameplay mods + their 3 deps + chat_heads as client-only + cloth-config as client-only Cobbreeding dep).
  - Added 3 in-house jars to `overrides/mods/`: `cobblemon-market-1.0.0.jar`, `cobblemon-ranked-1.0.0.jar`, `cobblemon-gacha-1.0.0.jar`.
- **Test path:** Prism → Edit instance → Version → Reinstall from zip → pick 0.3.0 → launch → connect to local server.
- **Note:** mrpack is not committed (the `.mrpack` extension is in `.gitignore` and the file is 127 MB). Re-build with `/tmp/build_mrpack.py` if needed (script generates manifests dynamically from Modrinth API).

### 2026-05-11 — Required-dep jars for Watut + Unchained + Cobblemon Linkie
- **Added to `cobblemon-server/mods/`** (pure libraries, no commands of their own):
  - `coroutil-neoforge-1.21.0-1.3.8.jar` — required by Watut. https://modrinth.com/mod/coroutil/version/1.21.0-1.3.8
  - `timcore-neoforge-1.7.3-1.32.0.jar` — required by Cobblemon Unchained and Cobblemon Linkie. https://modrinth.com/mod/cobblemon-tim-core/version/1.7.3-neoforge-1.32.0
  - `counter-neoforge-1.7.3-1.9.0.jar` — provides modId `cobbled_counter`, required by Cobblemon Unchained. https://modrinth.com/mod/cobblemon-counter/version/1.7.3-neoforge-1.9.0
- **Reason:** first restart after the QOL/gameplay batch crashed with ModLoadingException for each of these. They were transitive deps not surfaced by the parent mods' Modrinth listings; surfaced by the boot error. Player pack needs them too.

### 2026-05-11 — Added 3 Cobblemon gameplay mods (Cobbleworkers, Cobbreeding, Unchained)
- **Added to `cobblemon-server/mods/`** (all `side = "BOTH"`; clients need them too):
  - `cobbleworkers-neoforge-2.0.2+1.7.0.jar` — Cobbleworkers by Accieo. Pasture blocks can give Pokémon utility jobs. https://modrinth.com/mod/cobbleworkers/version/2.0.2%2B1.7.0
  - `Cobbreeding-neoforge-2.2.1.jar` — Cobbreeding by Ludichat/Fuzuki. Adds Pokémon breeding to Cobblemon. https://modrinth.com/mod/cobbreeding/version/2.2.1
  - `unchained-neoforge-1.7.3-1.7.1.jar` — Cobblemon Unchained. Unlocks held-item/level/etc. constraints on Cobblemon legendaries/mythicals. https://modrinth.com/mod/cobblemon-unchained/version/1.7.3-neoforge-1.7.1
- **Client-pack additional dependency:** Cobbreeding declares `cloth_config` as a `side = "CLIENT"` required dep. The server doesn't need it (server skips client-only deps), but the next mrpack must bundle `cloth_config` for NeoForge 1.21.1 alongside Cobbreeding or the client will fail to load. https://modrinth.com/mod/cloth-config
- **Reason:** Server admin ask — these are popular Cobblemon gameplay extensions (breeding, item-unlocks, pasture-block worker assignments).

### 2026-05-11 — Added 5 QOL mods (client+server) and 1 client-only QOL mod
- **Added to `cobblemon-server/mods/`** (require client install via mrpack — registry sync would reject vanilla clients without them):
  - `watut-neoforge-1.21.0-1.2.7.jar` — "What Are They Up To" by Corosus. Shows nearby players' state (typing, in GUI, idle) as in-world icons. https://modrinth.com/mod/what-are-they-up-to/version/uWr2aTW9
  - `sophisticatedbackpacks-1.21.1-3.25.44.1736.jar` — Sophisticated Backpacks by P3pp3rF1y. Tiered backpacks with upgrades. https://modrinth.com/mod/sophisticated-backpacks/version/1.21.1-3.25.44.1736
  - `sophisticatedcore-1.21.1-1.4.38.1847.jar` — required dep of Sophisticated Backpacks. https://modrinth.com/mod/sophisticated-core
  - `chatbubbles-1.0.1.jar` — Renders chat messages as floating bubbles above players' heads. https://modrinth.com/mod/chatbubbles
  - `cobblemonlinkie-neoforge-1.7.3-1.1.0.jar` — Cobblemon Linkie. Lets players link Pokémon directly in chat. https://modrinth.com/mod/cobblemon-linkie
- **Stashed in `cobblemon-server/client-only-mods/`** (client-only, NOT installed on the server):
  - `chat_heads-0.15.1-neoforge-1.21.jar` — Chat Heads by dzwdz. Shows player heads next to chat lines. `side = "CLIENT"` + `displayTest = "IGNORE_ALL_VERSION"`, so it doesn't need to be on the server at all; we keep the jar in the repo for the next mrpack rebuild. https://modrinth.com/mod/chat-heads/version/0.15.1
- **Player pack action required:** the next `Cobblemon Server-x.x.x.mrpack` must include all 6 jars (the 5 client+server ones plus chat_heads). Clients that try to join without updating will be rejected at the registry-sync phase (same failure mode as the gacha menu types — see entry below).
- **Reason:** general QoL ask from server admin; no gameplay-blocking changes, all are additive.

### 2026-05-11 — Added `cobblemon-gacha-1.0.0.jar` (in-house gacha lootbox mod)
- **Added:** `cobblemon-gacha-1.0.0.jar` to `mods/`. Server-only NeoForge mod with `displayTest = "IGNORE_ALL_VERSION"` — does NOT require client install (uses vanilla `ChestMenu` for both the rolling animation and the odds preview, so no custom registry entries are synced to clients).
- **Reason:** Adds three tiers of lootboxes (Common, Rare, Ultra) earned from daily login + daily ranked battle wins, plus admin grants. See `docs/superpowers/specs/2026-05-11-cobblemon-gacha-design.md` and `docs/superpowers/plans/2026-05-11-cobblemon-gacha.md`.
- **Caveats:** First boot of this version generates `config/cobblemon-gacha/{config.json, players.json, tables/*.json}`. The 0-weight rows in the bundled Ultra CSV become "TBD" placeholder entries; admins should fill them in via `tables/ultra.json` once decided. Earlier iteration of the mod registered a custom `MenuType` and was rejected by clients without the gacha jar — that registration was dropped (commit `80946e9`); current jar uses vanilla `ChestMenu` so any client can connect.

### 2026-05-10 (undated, pre-existing in pack) — `cobblemon-npc-0.1.0.jar`
- **Already present** in `mods/` from the Society Sunlit pack baseline (no `displayTest` declared — defaults to `MATCH_VERSION`). Requires clients to also have it on disk; the upstream mrpack ships this on the client side.
- **What it does:** profession-themed Pokémon battle teams for Minecolonies citizens — see startup log line `cobblemon-npc: loaded 43 profession pools` / `25 gym leader themes`. Hooked into Minecolonies for the gym-leader progression mentioned in `install-client.md`.
- **Caveat:** ships with optional CobbleDollars support which logs `cobblemon-npc: CobbleDollars not loaded — rewards disabled at runtime` on boot. We use Cobblemon Economy instead; npc-mod rewards stay disabled, which is fine.

### 2026-05-10 — Replaced upstream `cobblemon_ranked` with our build, including `displayTest` redeploy
- **Replaced** earlier in-house `cobblemon-ranked-1.0.0.jar` with a rebuilt jar that includes `displayTest = "IGNORE_ALL_VERSION"` in its `neoforge.mods.toml`. The earlier deployed jar pre-dated that fix; clients without ranked installed could still connect because the pack ships ranked on the client too, but the flag makes the intent explicit.

### 2026-05-09 — Added `cobblemon-economy-0.0.17.jar` (Fabric, via Connector)
- **Added:** `cobblemon-economy-0.0.17.jar` to `mods/`. It's a Fabric mod, loaded under Sinytra Connector (same path as LegendaryMonuments). Verified clean load and `/cobeco bal <player>` command works.
- **Reason:** Currency backend for the in-house `cobblemon-market` mod (server-side balance API access via reflection).

### 2026-05-10 — Replaced upstream `cobblemon_ranked` with our build; added `cobblemon_market`
- **Disabled:** `cobblemon_ranked-neoforge-1.4.2.jar` (moved to `mods-disabled/`). It collides on mod ID with our in-house ranked mod.
- **Added:** `cobblemon-market-1.0.0.jar` (in-house dynamic-pricing market).
- **Added:** `cobblemon-ranked-1.0.0.jar` (in-house ranked, NeoForge port from Fabric).
- **Reason:** Server-side rollout of the locally-developed mods (NeoForge port of the original Fabric versions — see `docs/superpowers/specs/2026-05-09-neoforge-port-design.md` and `docs/superpowers/plans/2026-05-09-neoforge-port.md`).
- **Caveats:** `/market` features depend on `cobblemon-economy` (loaded above). `/market open` replaces the previously-planned NPC shopkeeper system (deferred). Persistence uses the original `config/cobblemon-market/` and `config/cobblemon-ranked/` directory names so existing JSON state on disk is preserved across the port.

### 2026-05-08 — LegendaryMonuments: kept on 7.8 Fabric
- **No change** to the mrpack default `LegendaryMonuments-7.8.jar`.
- **Reason for the note:** Per the mod author, **every** Legendary Monuments build is a Fabric jar — the few "neoforge"-tagged Modrinth releases (4.x, 7.0-UNSTABLE, 7.1-NEOFORGE-CONNECTOR) are older Fabric jars relabeled, all of them require Sinytra Connector to run on NeoForge. Switching to a "NeoForge" build only buys a content downgrade; it does not remove the Connector dependency.
- **About the startup warning:** `Skipping jar. File LegendaryMonuments-7.8.jar is a Fabric mod and cannot be loaded` is emitted by NeoForge's vanilla mod scanner before Connector takes over. Connector then loads the jar as a Fabric mod (verified: `[legendarymonuments] App data storage initialized` appears later in the log). The warning is cosmetic — leave it.

## Excluded mods (client-only, intentionally not in server `mods/`)
The mrpack marks these `env.server = unsupported`; they are absent from the server install and present on Prism clients only:
- `ImmediatelyFast-NeoForge-1.6.10+1.21.1.jar`
- `athena-neoforge-1.21-4.0.4.jar`
- `entityculling-neoforge-1.10.1-mc1.21.1.jar`
- `fast-ip-ping-v1.0.11-mc1.21.1-neoforge.jar`
- `sodium-neoforge-0.6.13+mc1.21.1.jar`

## Configuration changes (vs. NeoForge defaults / mrpack overrides)

### `server.properties`
- `motd=Cobblemon Server - Local Test`
- `online-mode=false` (local testing — accepts any client UUID; required for offline-UUID ops)
- `enforce-whitelist=false`
- `view-distance=8`, `simulation-distance=8`
- `spawn-protection=0`
- `enable-command-block=true`
- `allow-flight=true`
- All other values normalized by NeoForge on first launch.

### `user_jvm_args.txt`
- `-Xmx6G -Xms2G` (mrpack default leaves `-Xmx` commented).

### `run.sh`
- Java path hardcoded to `/opt/homebrew/opt/openjdk@21/bin/java` (Homebrew keg-only install; not on default `PATH`).
- Added `nogui` argument.

### `ops.json`
- `sixthsense` added at level 4 with offline-mode UUID `82afe94e-0bbd-3de9-833e-4c57d9d022f7`.
  - UUID is `UUID.nameUUIDFromBytes("OfflinePlayer:sixthsense")` — only valid while `online-mode=false`.
