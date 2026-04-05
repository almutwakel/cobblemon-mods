# Cobblemon Extensions Design Spec

**Date:** 2026-04-04
**Platform:** Fabric 1.21.1, Cobblemon 1.7.3, Cobblemon Economy 0.0.17
**Language:** Kotlin
**Persistence:** JSON files
**Architecture:** Two independent Fabric mods

---

## Mod 1: `cobblemon-ranked` — ELO & Ranked Matches

### Overview

A ranked PvP battle system with ELO ratings, force-challenge mechanics, and automatic decay.

### Data Model

Player ELO records stored in `config/cobblemon-ranked/elo.json` as a map keyed by UUID:

```json
{
  "uuid-string": {
    "name": "PlayerName",
    "elo": 1200,
    "lastBattleDate": "2026-04-04",
    "wins": 0,
    "losses": 0,
    "forceLog": {
      "opponent-uuid": "2026-04-04"
    }
  }
}
```

### ELO System

- **Starting ELO:** 1200
- **Minimum ELO:** 1000 (floor)
- **K-factor:** 32 (flat, no changes based on match count)
- **Formula:** `newElo = max(1000, oldElo + 32 * (actual - expected))` where `expected = 1 / (1 + 10^((opponentElo - myElo) / 400))`, `actual = 1.0` for win, `0.0` for loss
- **Decay:** Once per calendar day, if at least one ranked battle happened on the server that day, all players who did NOT battle that day are scored as if they lost to a 1200-rated player. Decay respects the 1000 floor.

### Match Flow

1. **Challenge:** Player runs `/ranked challenge <player>`.
   - If challenger ELO < target ELO AND no force used today for this pair: **force** — target is immediately pulled into team selection.
   - If challenger ELO >= target ELO OR force already used today for this pair: **request** — target receives a chat prompt to `/ranked accept` or `/ranked decline`.
   - Target must be online and not currently in a battle. Otherwise the command fails with a message.

2. **Team Selection:** Both players are shown the PC/party selection screen to pick their team (up to 6 Pokemon).

3. **Legality Check:** Count Pokemon with the `legendary` Cobblemon label. If a player has more than 1 legendary, they auto-lose. ELO updates accordingly. Match ends.

4. **Battle Preparation:** Heal both players' selected teams fully. Record party stats (species, level, moves, ability) for potential future logging.

5. **Battle Start:** Initiate a PvP battle with level 50 cap using Cobblemon's `BattleBuilder` API. Attempt remote battle first (no teleportation). If remote PvP is not supported, teleport both players to configured arena coordinates, run the battle, then teleport them back to their original locations with ~5 seconds of invincibility.

6. **Disconnect/Flee Handling:** Subscribe to `BATTLE_FLED` event and player disconnect events. If a player flees or disconnects during a ranked battle, they auto-lose. ELO updates accordingly.

7. **Victory:** Subscribe to `BATTLE_VICTORY`. Update ELO for both players. Broadcast to server chat:
   - Winner and loser names
   - New ELO scores for both players (with +/- change)
   - Any leaderboard position changes

### Commands

| Command | Permission | Description |
|---|---|---|
| `/ranked challenge <player>` | all | Challenge a player to a ranked match |
| `/ranked accept` | all | Accept a pending challenge |
| `/ranked decline` | all | Decline a pending challenge |
| `/ranked stats [player]` | all | View ELO, wins, losses |
| `/ranked leaderboard` | all | Show top players by ELO |
| `/ranked admin setelo <player> <value>` | op (level 4) | Override a player's ELO |
| `/ranked admin decay` | op (level 4) | Manually trigger decay cycle |
| `/ranked admin force <player1> <player2>` | op (level 4) | Force a match between two players, bypassing ELO direction and daily limit |

### Config (`config/cobblemon-ranked/config.json`)

```json
{
  "startingElo": 1200,
  "minimumElo": 1000,
  "kFactor": 32,
  "levelCap": 50,
  "maxLegendaries": 1,
  "forcesPerDayPerPair": 1,
  "decayEnabled": true,
  "arenaCoords": null
}
```

`arenaCoords` is `null` for remote battles, or `{"x": 0, "y": 64, "z": 0, "world": "minecraft:overworld"}` if teleportation is needed.

---

## Mod 2: `cobblemon-market` — Dynamic Shopkeeper

### Overview

An NPC shopkeeper with supply-and-demand pricing. One config value per item (base sell price), everything else is derived.

### Data Model

**Item config** (`config/cobblemon-market/items.json`):

```json
{
  "cobblemon:rare_candy": { "baseSellPrice": 2000 },
  "cobblemon:ultra_ball": { "baseSellPrice": 300 },
  "cobblemon:great_ball": { "baseSellPrice": 100 },
  "cobblemon:poke_ball": { "baseSellPrice": 30 },
  "cobblemon:revive": { "baseSellPrice": 500 }
}
```

**Market state** (`config/cobblemon-market/state.json`):

```json
{
  "cobblemon:rare_candy": {
    "priceFactor": 1.0,
    "transactions": [
      { "type": "sell", "timestamp": 1712188800000 },
      { "type": "buy", "timestamp": 1712192400000 }
    ]
  }
}
```

**Global config** (`config/cobblemon-market/config.json`):

```json
{
  "spreadBase": 3.0,
  "spreadExtra": 4.0,
  "recoveryRatePerHour": 0.01,
  "factorFloor": 0.10,
  "factorCeiling": 1.00,
  "sellDecay": 0.98,
  "buyGrowth": 1.02,
  "transactionWindowSize": 50
}
```

### Pricing Engine

**Current prices:**
- Sell price (shop pays player): `P_sell = B * f`
- Buy price (player pays shop): `P_buy = B * f * spread`

**Price factor updates:**
- On player sells to shop: `f = max(f * sellDecay, factorFloor)`
- On player buys from shop: `f = min(f * buyGrowth, factorCeiling)`

**Passive recovery** (each real-time hour):
- `f = f + recoveryRatePerHour * (factorCeiling - f)`
- Asymptotic recovery toward 1.0. Faster when far, slower when close.

**Dynamic spread:**
- `skew = sells / (sells + buys)` over last `transactionWindowSize` transactions
- If fewer than 2 transactions exist, `skew = 0.5`
- `spread = spreadBase + spreadExtra * (2 * |skew - 0.5|)^2`
- Balanced activity (skew ~0.5): spread ~3x. Fully one-sided: spread ~7x.

### NPC Shopkeeper

- Admin spawns NPC with `/market npc create <name>` at their current location
- NPC is a villager-type entity with a custom name tag
- Right-click opens a chest-style GUI showing all configured items with current buy/sell prices
- Players must physically go to the NPC to transact

### Transaction Flow

1. Player right-clicks NPC, GUI opens showing all items with current buy/sell prices.
2. Player selects an item, chooses buy or sell.
3. Player selects quantity (buttons: 1, 5, 10, custom). GUI shows **per-unit price breakdown** by simulating the iterative factor changes:
   ```
   Buy 5 Rare Candies:
     #1: 6,000  #2: 6,120  #3: 6,242  #4: 6,367  #5: 6,494
     Total: 31,223 PokeDollars
   ```
4. Player confirms. All units execute as individual transactions (each updating `f` and logging to rolling window) in a single batch action.
5. GUI stays open and refreshes prices in-place after the batch completes.
6. System validates: player has enough PokeDollars (buy) or items in inventory (sell) for the full batch before executing.

### Commands

| Command | Permission | Description |
|---|---|---|
| `/market prices` | all | Show current buy/sell prices for all items |
| `/market history <item>` | all | Show recent price movement for an item |
| `/market npc create <name>` | op (level 4) | Spawn shopkeeper NPC at current location |
| `/market npc remove` | op (level 4) | Remove nearest shopkeeper NPC |
| `/market admin setfactor <item> <value>` | op (level 4) | Override an item's price factor |
| `/market admin reload` | op (level 4) | Reload config from disk |

---

## Shared Technical Details

### Dependencies

Both mods depend on:
- Fabric Loader 0.16.5+
- Fabric API for MC 1.21.1
- Cobblemon 1.7.3
- Cobblemon Economy 0.0.17

Neither mod depends on the other.

### Kotlin + Cobblemon Interop

Both mods are written in Kotlin for clean Cobblemon API access (no `.INSTANCE` boilerplate, native extension functions). Cobblemon events are subscribed via `CobblemonEvents.EVENT_NAME.subscribe(Priority.NORMAL) { event -> ... }`.

### Persistence

Both use JSON files in their respective `config/` directories. Files are loaded on server start and saved after each state change (debounced to avoid excessive writes). For 20 players, file sizes are trivial.

### Currency

Both mods interact with PokeDollars via Cobblemon Economy's API for balance queries and updates.

---

## Future Work (Out of Scope)

- **Tournament system:** Double-elimination brackets seeded by ELO, standard competitive rules
- **Betting system:** Odds based on ELO differential, house odds, spectator pools for tournament semi-finals+
- **Wager matches:** PokeDollar stakes on ranked battles
- **Banned moves list:** Configurable competitive move bans
- **Additional shop items:** Expand default item catalog
