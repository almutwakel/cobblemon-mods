# Server Migration Checklist (incremental — destination has existing world + player data)

**Scenario:** The destination server is already live and has its own world, player inventories,
Cobblemon parties, ranked ELO records, market state, etc. We're rolling out the **new** content
we've built on the test server (`cobblemon-server/`) without touching any of the destination's
existing data.

The mods themselves are handled by deploying the latest `Cobblemon Server-0.3.x.mrpack`.
Everything below is the **per-install state** delta between test and destination.

---

## Sacred files on the destination — NEVER copy over from test

| Path | Why preserve |
|---|---|
| `world/` (whole folder) | destination's chunks, blocks, player coords, ender chests |
| `world/playerdata/*.dat` | destination's player inventories |
| `world/cobblemonplayerdata/` | destination's Pokémon parties + PCs |
| `world/legendarymonuments/` (or wherever it lives) | quest progress |
| Cobblemon Economy SQLite (look for `*.db` under `world/` or `config/cobblemon-economy/`) | player PokéDollar balances |
| `ops.json`, `whitelist.json`, `banned-players.json`, `usercache.json` | destination's admin lists |
| `config/cobblemon-market/state.json` if it exists there | destination's market stock state |
| `config/cobblemon-market/players.json` if present | destination's spend history |
| `config/cobblemon-ranked/elo.json` if present | destination's ELO records |
| `server.properties`, `user_jvm_args.txt`, `run.sh` | destination's tuned values |

**Mod-config schema migration risk** (read before deploying):
- The market mod's `state.json` schema **changed mid-session** from factor-based (`priceFactor`)
  to stock-based (`stock`). If the destination is running the old factor version of
  cobblemon-market, dropping the new 1.0.0 jar will:
  - Read old `state.json` with Gson → `stock` defaults to 0.0 for every item → first-day
    prices are insanely high until `setstock` is run.
  - Old `priceFactor` and `transactions` fields are silently ignored.
  - **Mitigation:** before swapping jars, either (a) accept the wipe and have an op run
    `/market admin setstock <item> 100` for each tier, or (b) hand-edit `state.json` to add a
    `stock` field per item before first boot. The data fields the new mod cares about are
    `stock: Double` and `priceHistory: List<PriceTick>` — see
    `cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/ItemState.kt`.
- The ranked mod's `elo.json` schema has been stable across versions; no migration needed.
- The gacha mod is **new** to the destination — no schema concerns at first install, but:
  - If you ever drop a new gacha jar onto a server that already ran an older gacha build, **delete
    `config/cobblemon-gacha/tables/*.json` first**. Otherwise the on-disk JSONs (frozen at the
    parser version that wrote them) keep emitting old `Placeholder("pokemon_egg")` rewards instead
    of the new Cobbreeding egg / pedestal mappings. The mod regenerates from the bundled CSV on
    first boot when the JSONs are missing.
  - The egg-pool data lives at `config/cobblemon-gacha/egg_pools.json` (first-boot migrated from
    a bundled CSV). Editable by admins to add/remove species per rarity tier.

---

## Files we ARE bringing over from test → destination

### 1. The in-house mod jars
Build the three jars from this repo (or extract from the mrpack's `overrides/mods/`):

```bash
export JAVA_HOME=/path/to/jdk-21
for mod in cobblemon-market cobblemon-ranked cobblemon-gacha; do
  (cd "$mod" && ./gradlew build --no-daemon)
done
# Then SCP the built jars to <destination>:/server/mods/
```

For each, if the destination already has an older version of the same mod, **stop the server,
remove the old jar, drop in the new one**. Don't load two versions side by side.

### 2. The mrpack-managed mods (from `Cobblemon Server-0.3.4.mrpack`)
Anything new in the destination's mod set that isn't already there. Diff:

```bash
ssh <destination> 'ls /server/mods/*.jar' | sort > /tmp/dest_mods.txt
ls cobblemon-server/mods/*.jar | xargs -n1 basename | sort > /tmp/our_mods.txt
comm -23 /tmp/our_mods.txt /tmp/dest_mods.txt   # mods on test but not on destination
comm -13 /tmp/our_mods.txt /tmp/dest_mods.txt   # mods on destination but not on test (KEEP)
```

For each jar in the first column, SCP it to the destination. For each in the second column,
ignore — destination has its own additions we don't track.

### 3. NOT bringing over: our local mod state
Don't copy `cobblemon-server/config/cobblemon-{market,ranked,gacha}/*` from test. The
destination either has its own (preserve) or doesn't (the new mods auto-generate defaults
on first boot).

---

## In-game setup work on the destination (one-time, after mods are deployed)

After the destination reboots with the new mods, an op needs to do these. None of this touches
existing player data:

### NeoEssentials warps (new to destination)
1. Stand at spawn: `/setwarp spawn` (or whatever the NeoEssentials syntax is — check
   `config/neoessentials.toml` for command names).
2. Stand at arena: `/setwarp arena`.
3. Add other named warps as desired (shops, monuments, hubs).
4. Tune `config/neoessentials.toml` cooldowns/costs if defaults don't fit.

### Gacha crate coords (new to destination)
1. Place three vanilla blocks somewhere at spawn (chest / ender chest / beacon — type doesn't
   matter, only the coord).
2. Look at the Common crate, `/gacha admin setcrate common`. Repeat for `rare`, `ultra`.
3. Verify: `/gacha admin force <yourname> common` → should fire the rolling GUI.

### Starter Kit (new to destination)
1. Clear an op's inventory.
2. Fill with the starter loadout:
   - `/give @s cobblemon:poke_ball 10`
   - `/give @s minecraft:iron_pickaxe`
   - `/give @s cobblemon:red_apricorn 3`
   - `/give @s sophisticatedbackpacks:backpack`
   - `/gacha admin grant <yourname> common 1`
   - Ash Ketchum hat — JEI-search "Ash" to find the CobbleFurnies item id, then `/give @s <id>`
   - Wiki book — `/give @s written_book[written_book_content={title:"Server Wiki",author:"Server",pages:[{raw:"Welcome..."}]}]`
3. `/sk set default` to capture as the auto-granted kit.
4. Existing players already on the destination **won't retroactively get the kit** — Starter
   Kit only fires for genuinely-new joins. That's by design (no surprise inventory items for
   established players).

### Cobblemon Gacha — bootstrap player records
For existing destination players to start receiving daily login keys, no action needed — the
mod creates `players.json` entries lazily on each player's next login.

---

## Distribution: tell players about the new pack

1. Upload `Cobblemon Server-0.3.x.mrpack` to wherever the pack is hosted.
2. Update `install-client.md` with the new pack URL and the destination's server address.
3. Tell players: in Prism → right-click instance → Edit → Version → Reinstall from zip → pick
   the new mrpack. Worlds and settings are preserved; only mods get swapped.

---

## What to expect when the destination restarts

- **Mod loader scan:** all new jars discovered, the boot log should say `Cobblemon Gacha
  initializing...`, `Cobbleworkers Launching ...`, `NeoEssentials` ready, etc. Same set of
  init lines as the test server's run.
- **Auto-generated config dirs** for any new mod (gacha, NeoEssentials, starter-kit). Old
  configs you didn't bring over are untouched.
- **Recipe-load errors** for `cobbreeding:fried_egg_*` — these reference `farmersdelight`
  which isn't installed. Cosmetic, ignore.
- **Tag-not-available warnings** for cobbleworkers during initial datapack load. Self-recovers.

If the destination's `cobblemon-market` was the pre-stock-rework version, expect a few minutes
of high prices on first boot until an op runs `/market admin setstock <item> 100` for each
item — see the schema-migration note above.

---

## Backup before any of this

Snapshot the destination's `world/`, `config/`, and the Cobblemon Economy SQLite **before**
swapping any jars. The mod-schema migration on market is the only thing here that can lose
data, but a snapshot is cheap insurance.

---

## Future tooling (not yet built)

- `scripts/migration_diff.sh <destination-host>`: SSH to the destination, list its `mods/`,
  diff against our `cobblemon-server/mods/`, print the symmetric difference and warn on
  any pre-rework `cobblemon-market` state shape. Nice-to-have before a real cutover.
