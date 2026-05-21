# Cobblemon Server — Local Changes

Tracks deviations of the local `cobblemon-server/` install from the upstream `Cobblemon Server-0.2.1.mrpack`. Apply the same changes to clients (or release a new mrpack) when anything affects parity.

Server pack base: **MC 1.21.1 / NeoForge 21.1.227**, mrpack `Cobblemon Server-0.2.1.mrpack`.
Server install path: `/Users/almutwakel/Documents/Projects/minecraft/cobblemon-server/`.

## Mods

### 2026-05-21 — Quest rewards overhauled: eggs and keys only, full gym/E4/champion coverage

- **Stripped all item rewards** (exp candies, balls, ability patches/capsules, rare candies, master balls, bells, bone meal) from every quest mcfunction. Each quest now grants exactly one reward: an egg or a key.
- **Egg tier assignments:**
  - **Common:** craft_pokeball, catch_pokemon, farm_carrots, first_pvp_win, join_colony, reach_income_100.
  - **Uncommon:** reach_elo_1100, reach_elo_1200, reach_income_1000.
  - **Rare:** reach_elo_1300, reach_elo_1500, reach_income_10000.
  - **Ultra Rare:** reach_elo_2000, reach_income_100000.
  - No shiny or HA flags on any reward — HA is species-specific in the egg pool CSV, not a reward modifier.
- **Key tier assignments (gyms/E4/champion):**
  - **Rare Key:** Gyms 1–9, all 9 rotating gyms, Elite Four #1–3.
  - **Ultra Key:** Gym 10 (Morty), Elite Four #4 (Dragon Boss), Champion.
- **New gym numbering scheme** for `cobblemon_bridge.gym_id.<N>` entity tags:
  - 1–10: main gyms (Clay, Gardenia, Korrina, Byron, Blaine, Roxie, Crasher Wake, Sabrina, Drayden, Morty).
  - 11–19: rotating gyms (Viola, Cheren, Lt. Surge, Grant, Skyla, Brycen, Valerie, Marnie, Professor Oak).
  - 20–23: Elite Four (#1 Weather Wars, #2 Hyper Offense, #3 Full Stall, #4 Dragon Boss).
  - 24: Champion.
- **23 new advancement JSONs** created for gyms 2–10, rotating gyms, E4, and Champion. Tree structure: main gyms chain sequentially (1→2→…→10); rotating gyms and E4 branch from gym 10; E4 chains sequentially to Champion.
- **23 new reward mcfunctions** created for the same. Uses `gacha admin grant @s <rare|ultra> 1` for keys and `gacha admin giveegg @s <tier>` for eggs.
- **Datapack now git-tracked** at `datapacks/server-quests/` (was previously only in the gitignored `cobblemon-server/world/datapacks/` directory). The tracked copy is the source of truth; deploy by copying to the server's `world/datapacks/`.
- **Total quest count:** 38 (was 15). Breakdown: 6 early-game, 5 ELO ladder, 4 income, 1 colony, 10 main gyms, 9 rotating gyms, 4 E4, 1 champion, minus 2 that existed before (beat_gym_1, reach_elo_2000) = 23 net new.

### 2026-05-16 — Added `/quests` player command (cobblemon-bridge)
- **New Brigadier command** in `commands/QuestCommand.kt`. Subcommands:
  - `/quests` or `/quests current` — shows the player's current main-chain quest with title + description.
  - `/quests list` — full quest tree grouped into Main / Income / Ranked Ladder / Other sections with `§a✓` / `§e▶` / `§7○` markers per quest. Titles read from each advancement's display block at runtime, so editing a quest's JSON title updates the command output automatically.
  - `/quests hud on|off|toggle` — manages the on-screen action-bar HUD via the `cq_hud_off` tag (same tag the datapack's `/trigger cq_hud_toggle` flips, so both interfaces converge on one state).
  - `/quests help` — subcommand list.
- **Wired via** `RegisterCommandsEvent` listener in `CobblemonBridge.init`. Quest ID groupings are hardcoded constants in `QuestCommand.kt`; new quests added later need a one-line append.
- **Bridge jar rebuilt + redeployed** (`cobblemon-bridge-1.0.0.jar`). Needs server restart for the new command to register.

### 2026-05-16 — Cobbleloots config cleanup + Minecolonies quest unblocked
- **Cobbleloots config** (`config/cobbleloots.json`): added 19 unused tier ids to `data_pack_disabled_loot_balls` (azure/citrine/dive/dusk/heal/lure/luxury/master/nest/net/premier/pumpkin/quick/rainbow/roseate/safari/slate/timer/verdant). Only poké/great/ultra spawn from chunk-gen + spawning + fishing now — those are the three the bridge `give_party_exp` adapter maps to 100/800/3000 party EXP.
- **`server:join_colony`** advancement upgraded to use Minecolonies' OWN registered advancement triggers — no in-house code needed. The criteria are `minecolonies:place_supply` (player places a Supply Camp = founds a colony) OR `minecolonies:create_build_request` (player issues a build request = is an active citizen). `requirements: [["founded", "active_member"]]` makes it an OR. Verified by inspecting `com.minecolonies.api.advancements.AdvancementTriggers` bytecode — both triggers register under the `minecolonies:` namespace and accept the default `{}` condition. Pure datapack edit; no Kotlin.

### 2026-05-16 — `/gacha admin giveegg` command (clean knob for quest-reward eggs)
- **New admin command** `gacha admin giveegg <player> <pool> [shiny] [ha]` in cobblemon-gacha (`commands/GachaCommands.kt` + new private `adminGiveEgg`). Pool is `common` / `uncommon` / `rare` / `ultra_rare`. Reuses `CobblemonGacha.eggPools.pick()` and dispatches Cobbreeding's `givepokemonegg` with `min_perfect_ivs=2` baseline + optional flags. Mirrors `RewardGranter.dispatchEgg` so a quest reward egg is identical to a gacha pull egg.
- **Usage from mcfunctions:** `gacha admin giveegg @s rare shiny` from any reward function (op-2 permission is enough). Promoting a pool's species list later means editing `egg_pools.csv` — every quest that grants from that pool benefits.
- **Demonstrated in:** `beat_gym_1.mcfunction` (now grants a shiny rare-pool egg as a bonus) and `reach_elo_2000.mcfunction` (shiny + HA ultra-rare egg).

### 2026-05-14 — Quest system fully wired (16 advancements, action-bar HUD, 3 in-house mod hooks)
- **Datapack** at `world/datapacks/server-quests/` now has 16 advancement JSONs + 15 reward mcfunctions covering the full quest list:
  - **Linear chain** (drives the action-bar HUD): `craft_pokeball` → `catch_pokemon` → `farm_carrots` → `beat_gym_1` → `first_pvp_win` → `reach_elo_1100`.
  - **Side track — income** (silent in HUD, visible in L tree): `reach_income_100` → `_1000` → `_10000` → `_100000`. Current goal is `_1000` (task frame, real reward); others are `goal` / `challenge` frames with milestone-tier rewards.
  - **Side track — ELO** (silent in HUD): `reach_elo_1100` (task, current goal) → `_1200` → `_1300` → `_1500` → `_2000` (challenge with Master Ball payout).
  - **Side track — colony**: `join_colony` (impossible; TODO — needs Minecolonies event-bus adapter).
- **Triggers:** vanilla `minecraft:inventory_changed` (Poké Ball, 32+ carrots), `cobblemon:catch_pokemon` (note: the actual trigger id is `catch_pokemon`, not `caught_pokemon`), `minecraft:impossible` for all in-house-awarded ones.
- **HUD machinery (pure mcfunction):**
  - `tick.json` registers `server:quests/hud/tick` (every tick).
  - Throttles to ~1.5s via per-player `cq_hud_tick` scoreboard counter.
  - `tick_player.mcfunction` cascade matches the first incomplete linear-chain quest and pushes a one-line `title @s actionbar`.
  - Parallel side quests intentionally don't appear in the HUD — they pop toasts on grant and live in the L-key tree.
- **Opt-out:** `/trigger cq_hud_toggle` flips a `cq_hud_off` tag; HUD tick skips tagged players. Off-players still get `tellraw` chat updates on every grant (reward function runs regardless).
- **In-house mod awards (new code, ~80 LOC across 3 mods + 1 datapack):**
  - **cobblemon-bridge** — added `BridgeTags.GYM_ID` (`cobblemon_bridge.gym_id.<N>`, range 1..30), `battle/GymDefeatHook.kt` (stash on `EntityInteract`, apply on `BATTLE_VICTORY` for the winning player — awards `server:beat_gym_<N>`), and `quests/QuestAdvancements.kt` helper. Stash TTL is 5min to cover long gym fights. Tests up to 15 in the bridge.
  - **cobblemon-ranked** — in `applyMatchResult`, after ELO update, awards `server:first_pvp_win` to the winner + awards `server:reach_elo_<N>` for any thresholds the winner just crossed up. Centralized `ELO_THRESHOLDS = [1100, 1200, 1300, 1500, 2000]` constant. Skips silently when player offline (e.g. console-simulated matches) or when datapack not loaded.
  - **cobblemon-market** — `economy/QuestRewards.kt`. After a successful sell deposit, computes balance before/after and awards `server:reach_income_<N>` for any newly-crossed threshold in `[100, 1000, 10000, 100000]`. Only deposits can cross UP, so only the sell path needs the check.
- **All three mod jars rebuilt and redeployed.** Datapack auto-loads with `world/datapacks/server-quests/` on next `/reload` or server restart.

### 2026-05-13 — Cobbleloots 2.3.0 + bridge `give_party_exp` hook (loot balls → Pokémon party EXP)
- **Added** `cobbleloots-neoforge-2.3.0.jar` (modId `cobbleloots`, by ResistorCat). Naturally-spawning loot ball entities in newly-generated chunks. https://modrinth.com/mod/cobbleloots/version/2.3.0
- **Side:** `BOTH` — must be in the next mrpack so vanilla clients pass registry sync (the mod registers `cobbleloots:loot_ball` entity, `cobbleloots:loot_ball` item).
- **Datapack** at `world/datapacks/server-lootballs/`: overrides bundled `poke.json`, `great.json`, `ultra.json` so they (a) point at an empty loot table (`server:empty`, also defined in the pack) — no item drops, (b) set `xp: 0` — no vanilla XP. Only Poké/Great/Ultra are overridden; the other 19 bundled tiers (azure/citrine/dusk/etc.) still ship with their defaults. **TODO after first boot:** add the unused tier IDs to `data_pack_disabled_loot_balls` in `config/cobbleloots.json5` so only Poké/Great/Ultra spawn in the world.
- **Extended `cobblemon-bridge` 1.0.0** (jar rebuilt, redeployed) with:
  - **New tag hook `cobblemon_bridge.give_party_exp.<N>`** — right-click any entity carrying this tag distributes N Cobblemon Pokémon EXP equally across the player's party slots (split with remainder on slot 0), suppresses the vanilla entity interact (no loot UI, no vanilla XP), despawns the entity. `SidemodExperienceSource("cobblemon_bridge")` so the source is auditable in Cobblemon logs.
  - **Cobbleloots adapter** — `EntityJoinLevelEvent` listener detects entities with registry id `cobbleloots:loot_ball`, reflectively calls `getLootBallDataId()` to read the tier, and stamps `cobblemon_bridge.give_party_exp.<N>` per the mapping: `poke=100` (≈ Exp Candy XS), `great=800` (≈ S), `ultra=3000` (≈ M). The other 19 tiers are intentionally unmapped — they'll spawn but won't grant party EXP until disabled via Cobbleloots config.
  - Adapter is gated by `ModList.isLoaded("cobbleloots")` so cobblemon-bridge stays usable without Cobbleloots installed. No compile-time dep on Cobbleloots — the reflection call doesn't pull in their classes.
- **End-to-end flow:** chunk gens → Cobbleloots spawns a `cobbleloots:loot_ball` entity → adapter sees `EntityJoinLevelEvent`, reads tier, adds `give_party_exp.100/800/3000` tag → player right-clicks → bridge `GivePartyExpHook` fires, distributes EXP across party, despawns, sound + chat confirmation → single-grab, gone for everyone.

### 2026-05-13 — Added `cobblemon-bridge` 1.0.0 (tag-driven Cobblemon hooks; first hook: battle level scaling)
- **New in-house mod** at `cobblemon-bridge/`. Built like the other in-house mods (Kotlin 2.2.20, KotlinForForge 5.11, NeoForge 21.1.227). Jar lives at `cobblemon-server/mods/cobblemon-bridge-1.0.0.jar`.
- **Design rule:** every hook is gated by a `cobblemon_bridge:<hook>/<arg>` tag on an entity (vanilla `Tags: [String]` NBT array). The mod listens to public Cobblemon / NeoForge events and applies the hook when it sees the tag.
- **Why tags:** `/tag` is vanilla and works without custom commands; tags persist on entities; datapack functions / loot tables can stamp tags on summon for free.
- **First hook — `adjust_level`:** entity tag `cobblemon_bridge.adjust_level.<N>` (where `<N>` is 1-100) sets `BattleFormat.adjustLevel = N` for the battle started by interacting with that entity. Dots are used as separators because vanilla `/tag` rejects `/` and `:` (the StringReader scoreboard-tag parser only accepts `[0-9a-zA-Z_+\-.]`). Closes the gap RCT documents but doesn't actually wire (RCT 0.18.1-beta declares `BattleRules.adjustPlayerLevels` but the field is never consumed; the bridge fills in the missing hop by mutating the Cobblemon format directly).
- **Detection flow:** `PlayerInteractEvent.EntityInteract` reads the target's tag set and stashes the desired level keyed by player UUID (5-second TTL). `CobblemonEvents.BATTLE_STARTED_PRE` consumes the stash and mutates `battle.format.adjustLevel` before Cobblemon's engine commits.
- **Usage:** as op, point at an NPC (Cobblemon built-in, RCT, or any tagged entity), run `/tag <selector> add cobblemon_bridge:adjust_level/50`. Next time a player initiates a battle with that NPC, both teams fight at level 50; the player's stored Pokémon are unchanged.
- **Future hooks (planned):** progression flags (`battle_unique/<id>` to mark first-defeat for a player), full heal after battle, reward grants — all gated by their own `cobblemon_bridge:*` tags. No per-mod config files; tags are the only knob.

### 2026-05-12 — Added Cobblemon Fight or Flight Reborn 0.10.7 (alphas-only aggression)
- **Added to `cobblemon-server/mods/`:** `fightorflight-neoforge-0.10.7.jar` (modId `fightorflight`, by rufia + LyquidQrystal). Makes Cobblemon Pokémon hostile under various conditions. Alpha Project does not make Alphas attack on its own — this is the mod that does. https://modrinth.com/mod/cobblemon-fight-or-flight-reborn/version/DrweIBly
- **Deps:** MC 1.21.1, NeoForge 21.1+, Cobblemon 1.7.0+, Architectury 13.0.8+ — all satisfied. Cloth Config is bundled as a JIJ (inside the jar's `META-INF/jars/`).
- **Side:** `BOTH` — must be in the next mrpack alongside Alpha Project; otherwise vanilla clients will fail to connect.
- **Config (server: `config/fightorflight.json5`, generated on first boot by AutoConfig).** To get "only Alphas attack on sight, all other wild mons stay passive unless hit," after first boot edit these keys:
  - `always_aggro_aspects = ["alpha"]` — Pokémon carrying the `alpha` aspect become unconditionally hostile. The Alpha Project mod stamps that aspect on every Alpha it spawns.
  - `do_pokemon_attack_unprovoked = false` — non-Alpha wild mons won't initiate combat. They'll still defend themselves if hit (controlled by `do_pokemon_attack`, default true).
  - Leave `do_pokemon_attack = true` (master toggle — required for any attacking).
  - Leave `aggressive_pokemon_catchable = true` so players can still throw balls at angry Alphas without the mod blocking the catch.
- **Other knobs worth knowing (defaults usually fine):**
  - `minimum_attack_level` (default 1) — only mons at/above this level can attack.
  - `minimum_attack_damage` / `maximum_attack_damage` — clamps the damage a wild Pokémon can deal. Useful if a level-50 Alpha is one-shotting players.
  - `always_aggro = ["species_id", ...]` — force-aggressive species list (independent of aspect).
  - `never_aggro = [...]` — opt-out list.
  - `do_player_pokemon_attack_other_players` (default false) — leave off unless you want PvP-style sandbox combat.
- **One-time setup after first boot:** stop server, open `config/fightorflight.json5`, find the `always_aggro_aspects` line (likely an empty `[]` by default) and change to `["alpha"]`, find `do_pokemon_attack_unprovoked` and set to `false`, save, restart. Or do it in-game if `/fightorflight reload` is exposed (not verified — config edit + restart is the safe path).

### 2026-05-12 — Added Cobblemon Alpha Project 1.4.1
- **Added to `cobblemon-server/mods/`:** `cobblemonalphas-1.4.1.jar` (modId `cobblemonalphas`, by Cudzer + TheEternalDragon). Adds wild-spawning Alpha Pokémon (larger, stronger, IV-loaded — Legends-Arceus-style). https://modrinth.com/mod/cobblemon-alpha-project/version/1.4.1
- **Side:** `BOTH` — client pack MUST bundle this jar too. Add to the next mrpack or vanilla clients will fail registry sync on join.
- **Hard deps satisfied:** NeoForge 21+, MC 1.21.1+, Architectury 13.0.8+ (already present at 13.0.8 from Cobbreeding install). Cobblemon itself is *not* a declared mod-deps entry but the mod is designed for Cobblemon 1.7.3 (which we run).
- **Config (defaults retained — wiki link: https://github.com/Cudzer/cobblemonalphas/wiki/Configuration):**
  - `doAlphaSpawning = true` — master switch.
  - `alphaSpawnChance = 0.01` — 1% chance per spawn attempt.
  - `secondsBetweenSpawns = 300` — 5-min interval between attempts.
  - `spawnAttempts = 10` — how many tries per interval.
  - `requiredPlayerAmount = 1` — at least 1 player online for spawning.
  - `minimumSpawnDistance = 30`, `maximumSpawnDistance = 60` — radius from a player.
  - `alphaSizeModifier = 2.0` — Alphas render 2× normal size.
  - `maximumBestIvs = 3` — Alphas get up to 3 perfect IVs.
  - `doHerdSpawning = true` — also spawns the mon's natural herd.
  - `shinyOdds = 4096` — vanilla shiny odds for Alphas.
  - `spawnAnnouncementMessage = "An Alpha Pokemon has spawned near somebody!"`
  - `showCoordinatesInAnnouncement = false` — keep off so the announcement is a teaser rather than a beacon.
- **Tuning notes (if defaults feel wrong after testing):** halve `alphaSpawnChance` to 0.005 if Alphas show up too often; bump `maximumBestIvs` to 5 and drop `shinyOdds` to 1024 if Alphas should feel like a bigger reward; flip `showCoordinatesInAnnouncement = true` to make hunting them easier.

### 2026-05-12 — `cobblemon-gacha` 1.0.0 → eggs now grant HA + 2 perfect IVs; announce surfaces species
- **Rebuilt** `cobblemon-server/mods/cobblemon-gacha-1.0.0.jar`. No version bump.
- **PokemonProperties passed to `/givepokemonegg`:** all eggs now include `min_perfect_ivs=2` (Cobblemon picks 2 of the 6 stats randomly and sets them to 31). `shiny=true` and `ha=yes` are appended when the source `ItemSpec.CobbreedingEgg` requested them.
- **Hidden Ability now actually grants HA:** previously the gacha pool filter only ensured we picked from HA-capable species, but no flag was passed to the egg → ability roll defaulted to vanilla odds (~all common ability). Now `ha=yes` is sent so the hatched Pokémon has the hidden ability for certain. Detected the Cobblemon property key via `HiddenAbilityPropertyType` bytecode (accepts `ha` / `hiddenability` with `yes`/`true`).
- **Announce now reveals the rolled species + HA tag:** `RewardGranter.grant()` returns `GrantResult(stacks, labelOverride)`. When an egg is in the entry, `labelOverride` reads `§e✦ Shiny §fPikachu Egg §d(Hidden Ability)` and replaces the generic CSV label (`Shiny Egg`) in the server-wide pull message. Non-egg entries pass `null` and announce uses `entry.label` as before.
- **Display stack tooltip** mirrors the announce label — also shows `(HA)` suffix in the inventory item name.

### 2026-05-12 — `cobblemon-gacha` 1.0.0 → real egg & monument rewards (no more placeholders)
- **Rebuilt** `cobblemon-server/mods/cobblemon-gacha-1.0.0.jar` from the local module. No version bump (still 1.0.0); jar replaced in place.
- **What changed inside the mod:**
  - Added `ItemSpec.CobbreedingEgg(pool, shiny, requireHiddenAbility)` — at grant time, picks a species from a 4-tier pool (Common / Uncommon / Rare / Ultra Rare) and dispatches `/givepokemonegg <player> <species> [shiny=true]` to Cobbreeding. The display stack the announcer sees is a vanilla `minecraft:egg` renamed to the species + shiny tag. The real egg is created server-side by Cobbreeding's command.
  - Added `ItemSpec.RandomItem(ids: List<String>, count: Int)` — picks one id uniformly at random from a list at grant time. Used to map "Legendary Monument" / "Fragment" / "Voucher" labels in the loot CSV to a random pedestal block from the **LegendaryMonuments** mod (all 18 pedestal ids weighted equally per admin call).
  - Added `EggPools` / `EggPoolLoader` — first-boot CSV→JSON migration similar to `LootTableLoader`. Bundled CSV is the same `egg_hatch_pool.csv` from Downloads (86 species, 4 tiers). Admins can edit `config/cobblemon-gacha/egg_pools.json` after first boot.
  - Bundled resource: `src/main/resources/egg_pools.csv`.
- **Loot-table parser routing in `LootTableLoader.parseItemLabel`:**
  - `*egg*` labels (except "Lucky Egg" / "Bee egg" — those stay vanilla) now route through `routeEgg()`. Pool is inferred from label keywords (high-tier / larvitar / beldum / bagon → ultra_rare; mid-tier / uncommon → uncommon; rare → rare; common → common; bare "Shiny Egg" with no tier word → rare). `shiny=true` if "shiny" in label. `requireHiddenAbility=true` if "hidden ability" in label.
  - `voucher` / `fragment` / `monument` labels route to `RandomItem(PEDESTAL_IDS, count=1)`.
- **Server-side state delete:** removed `config/cobblemon-gacha/tables/` (backed up to `config/cobblemon-gacha/backup-pre-egg/tables-2026-05-12/`). First boot regenerates the JSONs from the bundled CSV parser; otherwise the on-disk JSONs would still carry the old `Placeholder("pokemon_egg")` entries and the new routing wouldn't take effect.
- **Client impact:** none — gacha is still server-only (vanilla `ChestMenu` for the roll/odds GUIs, vanilla `minecraft:egg` for the display stack). No client mrpack change.
- **Migration note for the destination server:** if the destination already has a `tables/` JSON dir from an older gacha build, it must be removed in the same way (or the schema-discriminator branches won't kick in). Egg pools auto-generate from the bundled CSV on first boot.

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
