# Server Migration Checklist

When moving from the local test server (`/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/`) to a permanent host, this is what needs to happen beyond installing the mods.

The mods themselves are handled by deploying `Cobblemon Server-0.3.x.mrpack` (or its successor) on the host. Everything below is **per-install state** that doesn't ship in the pack.

## 1. Build artifacts (the three in-house mods)

The in-house mods live in this repo's `cobblemon-{market,ranked,gacha}/` directories. Their jars need to be built and dropped into the new server's `mods/`:

```bash
export JAVA_HOME=/path/to/jdk-21
for mod in cobblemon-market cobblemon-ranked cobblemon-gacha; do
  (cd "$mod" && ./gradlew build --no-daemon)
  cp "$mod/build/libs/${mod}-1.0.0.jar" /path/to/new-server/mods/
done
```

Or unzip the mrpack — the in-house jars ship in `overrides/mods/` and Prism extracts them on install. If you're not using Prism on the server, manually copy them out of the mrpack zip.

## 2. server.properties (mirror current values)

Currently customised (see `cobblemon-server-changes.md` for full list):

- `motd=Cobblemon Server` (drop the "Local Test" suffix on production)
- `online-mode=true` (flip from `false` — production needs auth)
- `enforce-whitelist=<whatever you want>`
- `view-distance=8`, `simulation-distance=8`
- `spawn-protection=<set non-zero on prod>`
- `enable-command-block=true`
- `allow-flight=true`
- `enable-rcon=true` + `rcon.password=<NEW STRONG PASSWORD>` + `rcon.port=25575` (the current `test-rcon-pw` was for local dev — must change)

## 3. Ops list

Currently in `ops.json`: `sixthsense` at level 4 (offline-UUID `82afe94e-0bbd-3de9-833e-4c57d9d022f7`).
On production with `online-mode=true`, you'll need to re-add yourself using your **online** UUID — Mojang-issued one, looked up via `https://api.mojang.com/users/profiles/minecraft/<name>`. The offline-mode UUID is invalid.

Easy path: log in to the new server once, then run `/op <yourname>` from the console.

## 4. JVM args + run script

Currently `user_jvm_args.txt`: `-Xmx6G -Xms2G`. Bump `-Xmx` to ~70-80% of host RAM (so a 16 GB box runs `-Xmx12G`).

`run.sh` has a hardcoded Java path (`/opt/homebrew/opt/openjdk@21/bin/java`). On the new host, point it at the host's Java 21 install.

## 5. In-world configuration (the tedious part — must be redone if the world is new)

Anything that references **world coordinates** doesn't survive a world reset. With a fresh world, all of this has to be set up in-game:

### NeoEssentials — `/spawn`, warps
1. Stand at the spawn location, `/spawn set` (NeoEssentials syntax may differ — check config).
2. Stand at the arena, `/setwarp arena`.
3. Add other named warps as you build (shops, dungeons, monuments).
4. Tune `config/neoessentials.toml` for cooldowns / max-homes / costs if desired.

### Cobblemon Gacha — crate coords
1. Place three blocks at spawn (any vanilla block — chest/ender chest/beacon is typical).
2. Stand looking at the Common crate block → `/gacha admin setcrate common`.
3. Repeat for `rare` and `ultra`. Coords get written to `config/cobblemon-gacha/config.json`.
4. Verify with `/gacha admin force <you> common` — a roll should fire.

### Starter Kit — assemble + capture
1. Clear your inventory, then build the kit:
   - `/give @s cobblemon:poke_ball 10`
   - `/give @s minecraft:iron_pickaxe`
   - `/give @s cobblemon:red_apricorn 3`
   - `/give @s sophisticatedbackpacks:backpack` (basic tier)
   - `/gacha admin grant <you> common 1` (mod-specific, not vanilla `/give`)
   - Ash Ketchum hat — look up the CobbleFurnies item id via JEI (search "Ash")
   - Wiki book — `/give @s written_book[written_book_content={title:"Server Wiki",author:"Server",pages:[{raw:"Welcome..."}]}]`
2. `/sk set default` to capture the current inventory as the kit named `default`.
3. Test by logging in as a fresh account or wiping a test player's `playerdata/<uuid>.dat`.

### Cobblemon Market — crate / shopkeeper
The market mod itself doesn't need world-coord setup — `/market buy|sell <item> <qty>` works from anywhere. If you want a physical shopkeeper at spawn, that's an additional design we haven't built.

### Cobblemon Ranked
No world setup needed — ELO is per-UUID. Daily decay runs on a server-tick timer.

### Cobblemon Economy
Persists in its own SQLite (`world/cobblemon_economy.db` or similar — verify path on first boot). If you want to carry over balances from the test server, copy that file. Otherwise everyone starts at 0 PokeDollars.

## 6. World data (carries with the world folder)

If you copy the `world/` folder from the test server to production, this comes along:
- Player inventories, ender chests, advancements
- ELO history (`config/cobblemon-ranked/elo.json`)
- Market stock state (`config/cobblemon-market/state.json`)
- Gacha player grant timestamps (`config/cobblemon-gacha/players.json`)
- Cobblemon party/PC storage (in `world/cobblemonplayerdata/`)
- Legendary Monuments progress (in `world/legendarymonuments/` or similar)
- Loaded chunks, structures, etc.

If you start with a fresh world, all of the above resets and you'll re-do section 5.

## 7. Client mrpack distribution

The mrpack URL in `install-client.md` currently points at a Google Drive folder. On migration:
1. Upload `Cobblemon Server-0.3.x.mrpack` (latest) to wherever you host the pack (Drive, Modrinth as a private project, your own webhost).
2. Edit `install-client.md` to point at the new URL.
3. Edit the **Server Address** in step 3 of the install instructions to the new server's public IP/hostname.

## 8. Backups (set up before any real-player traffic)

The test server has no backup configured. On production set up at minimum:
- Daily snapshot of `cobblemon-server/world/` (e.g., rsync or `restic`).
- Daily snapshot of `cobblemon-server/config/cobblemon-{market,ranked,gacha}/` (the in-house mod state).
- Versioned backup of `ops.json`, `whitelist.json`, `server.properties`.

## 9. Firewall + port

Open TCP 25565 (Minecraft) on the host. Don't expose 25575 (RCON) publicly — leave it bound to localhost or behind a VPN. The current RCON password must be rotated before going public.
