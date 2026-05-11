# Cobblemon Gacha — Design Spec

**Date:** 2026-05-11
**Status:** Approved
**Module:** `cobblemon-gacha`

## Goal

Add a third server-only mod, `cobblemon-gacha`, that gives players gacha lootboxes. Keys are earned from daily activity (login, ranked battles) or granted by admins for gym/milestone progression. Players right-click physical crates at spawn to consume a key and watch a CS:GO-style rolling animation that lands on a reward from a weighted loot table. High-tier and jackpot pulls are announced to the whole server with a tier banner and a firework.

## Scope

**In scope:**
- 3 key tiers: Common, Rare, Ultra
- 3 crates, one per tier, each at a configurable world coordinate
- Daily key grants: 1 Common Key on login, 1 Common Key on first ranked battle win
- Admin grant command for all tiers (covers gym/milestone progression for now)
- Right-click crate with matching key → rolling animation → reward delivery
- Right-click crate without matching key → read-only odds preview GUI
- Server-wide announcements with tier tag on High/Jackpot pulls
- Sound + firework on Jackpot
- CSV → JSON loot table migration on first boot
- Placeholder ItemStacks for unimplemented rewards (Pokémon eggs, monument vouchers, blank Ultra rows)

**Out of scope (for v1):**
- Automatic gym/milestone detection (admin-grant only)
- Custom block or item registration (vanilla items + DataComponents tagging instead)
- Client-side resource pack (server is the single source of truth)
- Per-player key inventory cap (key items live in the player's normal inventory)

## Architecture

NeoForge 21.1.x, Minecraft 1.21.1, Kotlin 2.2.20, KotlinForForge 5.x, ModDevGradle. Server-only, `displayTest = IGNORE_ALL_VERSION`. Pattern mirrors `cobblemon-market` and `cobblemon-ranked`: per-player JSON store, Gson persistence, Brigadier commands, NeoForge event subscriptions, Cobblemon event hook for ranked.

Mod ID: `cobblemon_gacha`. Persistence dir: `config/cobblemon-gacha/`.

## Module layout

```
cobblemon-gacha/
├── build.gradle.kts            # ModDevGradle, KotlinForForge, deps on Cobblemon
├── gradle.properties
└── src/main/kotlin/com/cobblemongacha/
    ├── CobblemonGacha.kt                     # @Mod entry, event registration
    ├── config/
    │   ├── GachaConfig.kt                    # crate coords (3 tiers), animation tuning
    │   └── LootTableLoader.kt                # CSV→JSON migration + JSON loader
    ├── data/
    │   ├── LootTable.kt                      # data classes: LootTable, LootEntry, Tier
    │   ├── KeyTier.kt                        # enum: COMMON, RARE, ULTRA
    │   ├── PlayerGachaData.kt                # lastLoginGrantDate, lastRankedGrantDate
    │   └── PlayerGachaStore.kt               # per-player JSON persistence
    ├── item/
    │   ├── KeyItems.kt                       # build vanilla-item-based key stacks
    │   └── PlaceholderItems.kt               # build placeholder stacks for TBD rewards
    ├── interaction/
    │   ├── CrateInteractionHandler.kt        # right-click handler; routes to roll vs odds
    │   └── KeyGrantHooks.kt                  # login event + BATTLE_VICTORY hook
    ├── gui/
    │   ├── RollMenu.kt                       # 9x1 vanilla MenuType, slot-animation logic
    │   └── OddsMenu.kt                       # read-only preview
    ├── reward/
    │   ├── RewardRoller.kt                   # weighted-random pick from LootTable
    │   └── RewardGranter.kt                  # ItemStack delivery, ground-drop fallback
    ├── announce/
    │   └── PullAnnouncer.kt                  # broadcasts + firework + sound
    └── commands/
        └── GachaCommands.kt                  # admin grant/reload/setcrate, odds, version, force

src/test/kotlin/com/cobblemongacha/
├── reward/RewardRollerTest.kt
└── config/LootTableLoaderTest.kt

src/main/resources/
├── META-INF/neoforge.mods.toml
├── tables/common.csv                          # bundled defaults, copied to config/ on first boot
├── tables/rare.csv
└── tables/ultra.csv
```

## Key & placeholder items (vanilla palette)

No custom item registration. Every "gacha item" is a vanilla ItemStack with `DataComponents.CUSTOM_NAME`, `DataComponents.LORE`, and `DataComponents.CUSTOM_DATA` set.

**Keys:**
| Tier   | Vanilla item               | Custom name                |
|--------|----------------------------|----------------------------|
| Common | `minecraft:trial_key`      | `§eCommon Key`             |
| Rare   | `minecraft:ominous_trial_key` | `§5Rare Key`            |
| Ultra  | `minecraft:nether_star`    | `§6Ultra Key`              |

Each key has `custom_data` (CompoundTag) containing `{ gacha_key: "common" | "rare" | "ultra" }`. The CrateInteractionHandler matches that tag, not the vanilla item id — so admins can substitute different vanilla bases later by editing the key-builder without breaking detection.

**Placeholder ItemStacks for unimplemented rewards:**
| Reward kind                       | Vanilla item             | Name pattern                            |
|-----------------------------------|--------------------------|-----------------------------------------|
| Pokémon egg (any tier)            | `minecraft:egg`          | `§a<Egg-tier label> (Placeholder)`     |
| Monument fragment / voucher       | `minecraft:filled_map`   | `§6<Voucher label> (Placeholder)`      |
| Other TBD reward (blank CSV rows) | `minecraft:knowledge_book` | `§7TBD Ultra Reward #<n> (Placeholder)` |

All placeholders carry `lore` explaining they will be swapped for real items in a future update, and `custom_data { gacha_placeholder: true, placeholder_id: "<unique>" }` so they're recognizable in inventories and can be auto-replaced later by a migration command.

## Loot tables

**Source.** Three CSVs shipped inside the jar at `resources/tables/{common,rare,ultra}.csv`. The bundled content matches the CSVs at `~/Downloads/loot_tables.csv/` provided by the user. On first boot, if `config/cobblemon-gacha/tables/<tier>.json` is missing, the loader parses the bundled CSV and writes JSON. Subsequent edits live in JSON; CSVs are reference only.

**JSON schema (per tier):**
```json
{
  "tier": "common",
  "totalWeightPct": 100.0,
  "entries": [
    {
      "lootTier": "Floor",
      "label": "20 Poké Balls",
      "weightPct": 18.0,
      "items": [
        { "id": "cobblemon:poke_ball", "count": 20 }
      ]
    },
    {
      "lootTier": "Jackpot",
      "label": "1 Rare Key",
      "weightPct": 0.5,
      "items": [
        { "gachaKey": "rare", "count": 1 }
      ]
    },
    {
      "lootTier": "High",
      "label": "Common egg placeholder (no guaranteed IVs)",
      "weightPct": 0.0,
      "items": [
        { "placeholder": "pokemon_egg", "label": "Common Pokémon Egg", "count": 1 }
      ]
    }
  ]
}
```

A single `LootEntry` may pack multiple items (e.g., a "Competitive Ready-Kit" bundling 3 Vitamins + 2 Mints — multiple stacks delivered together).

**Item types in JSON:**
- `{ id, count, name?, lore?, components? }` — vanilla or modded item by registry id.
- `{ gachaKey: <tier>, count }` — emit a Common/Rare/Ultra Key ItemStack (for jackpot-key entries).
- `{ placeholder: <kind>, label, count }` — emit a placeholder stack (egg / voucher / knowledge_book).

**Parsing CSV row → LootEntry:**
- `Tier` column populates `lootTier`. Blank Tier cell means "same tier as previous row".
- `Item` column populates `label` AND is parsed into `items[]` using a small DSL: leading integer → count, then known substrings map to known item ids ("Poké Ball", "Ultra Ball", "Rare Candy" …). Unknown items → log a warning, generate a `knowledge_book` placeholder with the label.
- `Chance %` → `weightPct` (strip `%` sign).
- `Notes` column → appended to `lore` on the resulting items so players can hover-inspect why a reward exists.

**Ultra Key unfinished rows.** The provided Ultra CSV has six rows with empty `Item` cells summing to ~29% (per the original CSV). The loader treats each blank-name row as a TBD placeholder entry whose item is `knowledge_book` named `§7TBD Ultra Reward #<n>`. The Ultra CSV's `TOTAL` reads 98.0% — the loader normalises actual weights by dividing each row by the observed sum, so the table integrates to 1.0 regardless of CSV total drift. Admins are expected to edit `tables/ultra.json` to fill in the blanks once decided; the format makes this trivial.

## Key earning

**Daily login grant.** Subscribe to `PlayerEvent.PlayerLoggedInEvent`. Compare `PlayerGachaData.lastLoginGrantDate` to `LocalDate.now().toString()`. If different: insert 1 Common Key via `RewardGranter`, update the date field, save store. Message: `§e[Gacha] Daily login bonus: +1 Common Key`.

**Daily ranked grant.** Subscribe to `CobblemonEvents.BATTLE_VICTORY` (same upstream event the ranked mod uses; Cobblemon's event bus supports multiple subscribers). For each `PlayerBattleActor` on the winning side, compare `lastRankedGrantDate` to today; if different and the battle had at least two distinct player actors (i.e., PvP, not vs wild), grant 1 Common Key and update the date.

**Admin grant.** `/gacha admin grant <player> <common|rare|ultra> [count]`. No date check; just builds N keys and delivers them. Sent to the target with a system message.

## Crate interaction

**Right-click flow** (handler on `PlayerInteractEvent.RightClickBlock`, server-side only):

```
1. block coord ∈ {commonCoord, rareCoord, ultraCoord}?  no → ignore
2. determine crateTier from matched coord
3. EventResult.CANCEL  (prevent the underlying vanilla block from opening)
4. heldItem.custom_data.gacha_key == crateTier?
     yes → consume 1 from stack → RewardRoller.roll(crateTier) → RollMenu.openFor(player, tier, reward)
     no  → OddsMenu.openFor(player, crateTier)
```

The crate's underlying block can be anything vanilla (chest, ender chest, beacon, anvil); only the coordinate is checked. Empty hand or wrong-tier key both fall through to the odds preview — this is the desired UX (a Common Key on the Rare crate previews Rare odds, not opens the Common box).

**Setting a crate.** `/gacha admin setcrate <common|rare|ultra>` writes the block the op is currently looking at (within reach) into `config/cobblemon-gacha/config.json`. Crates persist across restarts. Unconfigured crates simply do nothing (no error spam on stray right-clicks).

## Reward rolling

`RewardRoller.roll(tier, random = Random.Default)`:
1. Pull cached `LootTable` for the given tier.
2. Build cumulative weights from `entries.filter { it.weightPct > 0 }`.
3. Pick `random.nextDouble() * totalWeight`, binary-search for the bucket, return the `LootEntry`.

Deterministic with an injected `Random(seed)` — drives unit tests.

## Reward delivery

`RewardGranter.grant(player, entry)`:
1. Materialize `entry.items[]` into one or more `ItemStack`s using the JSON-item DSL (vanilla id, gachaKey, or placeholder).
2. For each stack: try `player.inventory.add(stack)`. If false (inventory full), spawn an `ItemEntity` at the player's feet.
3. Return the list of materialized stacks for the announcer.

## Rolling GUI animation

`RollMenu` uses `MenuType.GENERIC_9x1` (no custom MenuType registration). Lifecycle:

1. `RollMenu.openFor(player, tier, decidedReward)`. The reward is committed before the animation begins — the animation is purely cosmetic.
2. Open the menu server-side; player sees an empty 9-slot row with tier-colored stained-glass panes in slots 0 and 8 (white for Common, red for Rare, black for Ultra).
3. A scheduled-task ticker updates slot 4 (center) with a rolling sequence of candidate `ItemStack`s sampled uniformly from the loot table. Tick intervals follow a deceleration curve: `2, 2, 3, 3, 4, 5, 7, 10, 15` ticks between updates. Total run ≈ 4 seconds, ≈ 22 candidate flashes.
4. The final candidate IS the decided reward (so the visual settle matches what the player receives).
5. On settle: hold for 20 ticks (1 second). Play `entity.player.levelup` at the player for Jackpot pulls, `block.note_block.pling` for non-jackpot. For Jackpot, spawn a `FireworkRocketEntity` at the crate position with tier-colored explosion (white/red/purple).
6. Close the menu, call `RewardGranter.grant`, then `PullAnnouncer.broadcast`.

**Robustness.** The reward is committed at roll time and lives in the `RollMenu`'s state. Grant happens exactly once, on the first of: animation settle, `containerClose` (player closes menu early), or `PlayerLoggedOutEvent` while the menu is open. At all three of those points the ServerPlayer's inventory is still mutable, so the items go into the standard inventory and persist via vanilla player-save. No queue, no migration logic. The announcement fires at the same instant as the grant. The key has already been consumed by the time the menu opens, so there's no risk of "consumed key, no reward". If the player dies during the animation, the grant happens on close → items land in the respawned inventory (or drop to ground on death if they're still in the world inventory at death time — same as any other reward).

## Odds preview GUI

`OddsMenu.openFor(player, tier)`:
- Uses `MenuType.GENERIC_9x1` if the table has ≤ 9 entries, else `GENERIC_9x3`. Tables larger than 27 entries are truncated with a final "more entries available — see /gacha odds <tier>" book item.
- Each slot holds the entry's representative `ItemStack` (first item from `entry.items`), with lore appended:
  - `§7Tier: §f<Floor|Mid|High|Jackpot>`
  - `§7Chance: §a<weightPct rounded to 1 decimal>%`
  - Notes from the CSV if present
- Slots are read-only via `Slot.mayPickup = false`, `mayPlace = false`. Title: `§e[<Common|Rare|Ultra> Box] §7Possible Rewards`.

## Announcements

`PullAnnouncer.broadcast(player, tier, entry, materializedItems)`:
- Default line: `§7[Gacha] §a<Player> §7opened a §f<Tier> Box §7and got §f<entry.label>`.
- If `entry.lootTier == High`: append ` §6(HIGH)`.
- If `entry.lootTier == Jackpot`: replace whole line with `§e[Gacha] §6★ JACKPOT! §a<Player> §6got §f<entry.label> §6from a <Tier> Box ★`.
- For Jackpot: also spawn the firework (above) and broadcast a second blank-line for breathing room.
- All other pulls: a single line, no fanfare.

Broadcast via `server.playerList.broadcastSystemMessage(component, overlay = false)`.

## Persistence

**`config/cobblemon-gacha/config.json`**
```json
{
  "crates": {
    "common": { "x": 0, "y": 64, "z": 0, "dim": "minecraft:overworld" },
    "rare":   null,
    "ultra":  null
  },
  "animationTicks": [2, 2, 3, 3, 4, 5, 7, 10, 15],
  "jackpotHoldTicks": 20
}
```
Unset crates are `null` until `/gacha admin setcrate` is run.

**`config/cobblemon-gacha/players.json`**
```json
{
  "<uuid>": {
    "name": "SixthSense",
    "lastLoginGrantDate": "2026-05-11",
    "lastRankedGrantDate": "2026-05-10"
  }
}
```

**`config/cobblemon-gacha/tables/{common,rare,ultra}.json`** — schema above.

Writes are debounced through the same pattern as `MarketStore`: mutate the in-memory map, call `save()` after each grant or pull. Gson with `setPrettyPrinting()`.

## Commands

- `/gacha` / `/gacha help` — list user commands; admin extras gated on op level 4
- `/gacha odds <common|rare|ultra>` — opens the OddsMenu for that tier
- `/gacha version` — print mod version
- `/gacha admin grant <player> <common|rare|ultra> [count]` — issue keys
- `/gacha admin setcrate <common|rare|ultra>` — set the crate coord to the block the op is targeting
- `/gacha admin clearcrate <common|rare|ultra>` — unset
- `/gacha admin reload` — re-read `config.json` and `tables/*.json`
- `/gacha admin force <player> <common|rare|ultra>` — roll once for the given player without consuming a key (testing aid; still triggers the GUI, announcement, and delivery)
- `/gacha admin migratePlaceholders` — scans player inventories for items with `gacha_placeholder=true` and replaces them based on a separately-edited migration map. (Stub for v1; documented for v2 once real eggs ship.)

## Testing

**Unit tests (no Minecraft runtime needed):**

`LootTableLoaderTest`:
- Feed it the three real CSV strings as fixtures.
- Assert that Common parses to N entries with the right tiers, weights, and items.
- Assert that Ultra's blank rows yield `knowledge_book` placeholders, named "TBD Ultra Reward #1..6".
- Assert that the loader's normalized total weight is `1.0` regardless of CSV total drift.

`RewardRollerTest`:
- With `Random(seed = 0)`, roll 100,000 times against a fixture table; assert per-entry counts within ±5% of expected weight.
- Assert that 0%-weight entries (unfilled placeholders) are never picked.
- Assert that the same seed produces the same roll sequence (determinism).

**Live testing via RCON** (matches the workflow used for market/ranked):
- `/gacha admin force <player> common` — single pull, no daily wait.
- `/gacha odds <tier>` — visually verify the preview GUI from chat-readable output.
- `/gacha admin grant <player> rare 5` — give keys for end-to-end play.
- Stage one of each crate at known coords; `/gacha admin setcrate` to bind them; right-click to verify the full flow.

## Risks / open issues

- **Cobblemon battle event coupling.** If a Cobblemon update renames `BATTLE_VICTORY` or changes the actor model, ranked-key grants silently break. Mitigation: defensive try/catch with a warn-once log, same pattern as `EconomyBridge`.
- **Synthetic-class issue under Sinytra Connector.** Avoid Kotlin's `sortedByDescending` and similar inline sorts in the hot path; use explicit `Comparator { a, b -> ... }` SAMs. This bit us in the ranked mod's leaderboard.
- **Animation under lag.** If TPS drops, the animation drags but the reward is still committed. Acceptable trade-off; we do not advance the deceleration curve in wall-clock time.
- **Placeholder backfill.** Once real Pokémon eggs ship in a future Cobblemon version, `/gacha admin migratePlaceholders` needs implementing. v1 just emits the stubs.
