# Cobblemon Server — Local Changes

Tracks deviations of the local `cobblemon-server/` install from the upstream `Cobblemon Server-0.2.1.mrpack`. Apply the same changes to clients (or release a new mrpack) when anything affects parity.

Server pack base: **MC 1.21.1 / NeoForge 21.1.227**, mrpack `Cobblemon Server-0.2.1.mrpack`.
Server install path: `/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/`.

## Mods

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
