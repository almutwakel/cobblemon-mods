# Cobblemon Showcase Mod — Design Spec

**Date**: 2026-04-07
**Minecraft**: 1.21.1 (Fabric)
**Dependencies**: Cobblemon 1.7.3, sgui, cobblemon-ranked (data), cobblemon-market (data), cobblemon-economy (reflection)

---

## Overview

A player profile viewer mod. Clicking a player's name in chat opens a 6-row chest GUI showing their stats, team affiliation, PVP record, economy data, badges, and last ranked team. Also introduces a team affiliation system (e.g. Team Valor, Team Instinct).

Two existing mods (cobblemon-ranked, cobblemon-market) receive modifications to support the showcase and add leaderboard commands.

---

## 1. cobblemon-showcase (New Mod)

### 1.1 Chat Integration

A mixin on `ServerPlayNetworkHandler` (or equivalent) intercepts outgoing chat messages and decorates player name references with a `ClickEvent.runCommand("/showcase <name>")` and a hover tooltip ("Click to view profile"). This makes every player name in chat clickable.

### 1.2 GUI Layout (6-row chest, 54 slots)

**Row 1 — Identity (slots 0-8)**:
- Slot 4: Player head (via `Items.PLAYER_HEAD` with skull owner data) showing player name
- Slot 6: Team affiliation item (colored wool or banner) showing team name, or gray glass pane if unaffiliated

Remaining slots: filler panes.

**Row 2 — PVP Stats (slots 9-17)**:
- Slot 13: ELO rating display (diamond sword or similar icon) with lore showing:
  - Current ELO
  - W/L record (e.g. "42W / 18L")
  - Ranked leaderboard position

Read from cobblemon-ranked's `elo.json` file.

Remaining slots: filler panes.

**Row 3 — Economy & Activity (slots 18-26)**:
- Slot 20: PokeDollar balance (gold ingot icon) — queried via CobblemonEconomy reflection
- Slot 22: Market spend (emerald icon) — total PokeDollars spent buying from market, read from cobblemon-market's player spend data
- Slot 24: Playtime (clock icon) — read from Minecraft's built-in `Stats.CUSTOM` / `Stats.PLAY_TIME`

Remaining slots: filler panes.

**Row 4 — Last PVP Team (slots 27-35)**:
- Slots 29-34: Six pokeball icons (or barrier if no pokemon in that slot) representing the player's most recent ranked battle team
- Each pokeball shows the Pokemon's name, level, and species in the lore
- Read from cobblemon-ranked's `teams/<uuid>.json`

Remaining slots: filler panes.

**Rows 5-6 — Badges (slots 36-53)**:
- Up to 18 badge slots
- Each badge displays as a nether star (or configurable item) with the badge name and description in lore
- Empty slots show gray glass pane
- Read from `config/cobblemon-showcase/badges/<uuid>.json`

### 1.3 Team Affiliation System

Players join a team via `/team <name>`. Teams are purely cosmetic/social — they appear in the showcase GUI and can be used for future features.

**Rules**:
- Team names are defined in the config file. Any name not in the list is rejected.
- 24-hour cooldown between switches (configurable). Cooldown is stored per-player.
- Admins (permission level 4) can set any player's team without cooldown via `/team set <player> <name>`.

### 1.4 Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/showcase <player>` | All | Open a player's profile GUI |
| `/team <name>` | All | Join a team (subject to cooldown) |
| `/team set <player> <name>` | OP (4) | Admin: set a player's team, bypasses cooldown |

### 1.5 Data Files

**Owned by showcase mod:**

`config/cobblemon-showcase/config.json`:
```json
{
  "teams": ["Valor", "Instinct", "Mystic"],
  "teamSwitchCooldownHours": 24,
  "badgesDir": "config/cobblemon-showcase/badges"
}
```

`config/cobblemon-showcase/players/<uuid>.json`:
```json
{
  "team": "Valor",
  "lastTeamSwitch": "2026-04-07T12:00:00"
}
```

**Read-only (populated externally):**

`config/cobblemon-showcase/badges/<uuid>.json`:
```json
{
  "badges": [
    {"id": "boulder", "name": "Boulder Badge", "description": "Defeated Brock"},
    {"id": "cascade", "name": "Cascade Badge", "description": "Defeated Misty"}
  ]
}
```

**Read from other mods:**

- `config/cobblemon-ranked/elo.json` — ELO, wins, losses
- `config/cobblemon-ranked/teams/<uuid>.json` — last PVP team (new, see section 2)
- `config/cobblemon-market/state.json` — player spend data (extended, see section 3)

### 1.6 Dependencies

- **cobblemon-ranked**: soft dependency — if not present, PVP stats and team show as "N/A"
- **cobblemon-market**: soft dependency — if not present, market spend shows as "N/A"
- **cobblemon-economy**: soft dependency (reflection) — if not present, balance shows as "N/A"
- **sgui**: hard dependency (included in JAR)

---

## 2. cobblemon-ranked Modifications

### 2.1 Save Last PVP Team

After `resolveMatch()` completes (a ranked battle finishes), save both players' teams to disk.

**File**: `config/cobblemon-ranked/teams/<uuid>.json`
```json
{
  "team": [
    {"species": "Charizard", "level": 50, "nickname": null},
    {"species": "Blastoise", "level": 50, "nickname": "Shellshock"},
    {"species": "Venusaur", "level": 50, "nickname": null}
  ],
  "timestamp": "2026-04-07T15:30:00"
}
```

The team data is captured from the `List<Pokemon>` used in the battle. For each Pokemon, store species name, level, and optional nickname.

### 2.2 Leaderboard Command Enhancement

`/ranked leaderboard` currently broadcasts the top 5 after each match. Enhance it:

- Show top 10 (configurable) with rank, name, ELO, and W/L record
- If the executing player is not in the top N, append their own rank at the bottom separated by "..."
- Example output:
  ```
  [Ranked] === ELO Leaderboard ===
    1. PlayerA: 1450 (32W/12L)
    2. PlayerB: 1380 (28W/15L)
    ...
    10. PlayerJ: 1150 (10W/8L)
    ---
    14. You: 1095 (5W/7L)
  ```

Add `leaderboardSize` to the ranked config (default 10).

---

## 3. cobblemon-market Modifications

### 3.1 Per-Player Market Spend Tracking

Track total PokeDollars each player spends on purchases. Increment the counter in `executeTransaction()` when a buy completes.

**Storage**: Add a `playerSpend` map to the market store, persisted in `config/cobblemon-market/player_spend.json`:
```json
{
  "e959ce38-13e4-47cc-a680-c34c78c1e25a": {
    "name": "SixthSense",
    "totalSpend": 15000
  }
}
```

Separate file from `state.json` to keep concerns clean.

### 3.2 Market Wealth Leaderboard

New command: `/market leaderboard`

- Query PokeDollar balance for all known players via CobblemonEconomy reflection
- "Known players" = any player UUID that appears in the player spend file (i.e. anyone who has used the market at least once)
- Sort by balance descending, show top 10 (configurable)
- If the executing player is not in the top N, append their own rank at the bottom
- Example output:
  ```
  [Market] === Wealth Leaderboard ===
    1. PlayerA: 50,000 PokeDollars
    2. PlayerB: 32,000 PokeDollars
    ...
    10. PlayerJ: 5,000 PokeDollars
    ---
    14. You: 2,500 PokeDollars
  ```

Add `leaderboardSize` to the market config (default 10).

---

## 4. Build & Deployment

- **cobblemon-showcase**: New mod, same build setup as existing mods (Architectury Loom, Fabric 1.21.1, Kotlin, sgui)
- All three JARs deployed to the same mods folder
- No compile-time dependencies between the three mods — all cross-mod data is read from JSON files or via reflection
