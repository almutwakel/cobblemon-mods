# server-quests datapack

Vanilla advancement-driven quest system with a per-player action-bar HUD.

## How players interact

- **Action bar** shows the current quest in the linear chain (one line, refreshed every 1.5s).
- **L key** opens the vanilla advancements tree — all quests live under the "Server Progression" tab.
- **Opt out:** `/trigger cq_hud_toggle` flips the action bar HUD on/off. Off-players still get
  chat updates on every grant.

## Quest layout

### Linear chain (drives the action-bar HUD)

```
root
 └─ craft_pokeball     (vanilla: inventory_changed for cobblemon:poke_ball)
     └─ catch_pokemon  (Cobblemon: cobblemon:catch_pokemon)
         └─ farm_carrots                (vanilla: inventory_changed for 32+ carrots)
             └─ beat_gym_1              (impossible — cobblemon-bridge GymDefeatHook)
                 └─ first_pvp_win       (impossible — cobblemon-ranked applyMatchResult)
                     └─ reach_elo_1100  (impossible — cobblemon-ranked ELO threshold)
```

### Side tracks (silent — appear in L tree, don't drive HUD)

```
root
 ├─ reach_income_100   (goal frame)   ─┐
 │  └─ reach_income_1000  (task)       │ awarded by cobblemon-market
 │     └─ reach_income_10000 (goal)    │ TradeOps after sell deposit
 │        └─ reach_income_100000 (challenge)
 │
 ├─ first_pvp_win
 │  └─ reach_elo_1100 (task)                    ─┐
 │     └─ reach_elo_1200 (goal)                  │ awarded by cobblemon-ranked
 │        └─ reach_elo_1300 (goal)               │ applyMatchResult after ELO update
 │           └─ reach_elo_1500 (goal)            │
 │              └─ reach_elo_2000 (challenge)
 │
 └─ join_colony  (impossible — Minecolonies adapter TODO; manual grant for now)
```

## In-house mods that grant impossible-trigger advancements

| Advancement | Mod | Where |
|---|---|---|
| `beat_gym_N` | cobblemon-bridge | `battle/GymDefeatHook.kt` — stash on EntityInteract, apply on BATTLE_VICTORY |
| `first_pvp_win` | cobblemon-ranked | `battle/RankedBattle.kt` `applyMatchResult` |
| `reach_elo_<N>` | cobblemon-ranked | same; checks threshold crossings on winner ELO |
| `reach_income_<N>` | cobblemon-market | `economy/TradeOps.kt` `sellBatch` post-deposit |
| `join_colony` | (none — uses Minecolonies' vanilla triggers directly) | `minecolonies:place_supply` OR `minecolonies:create_build_request` (datapack-only) |

## Thresholds

Both threshold lists are centralized in the in-house mod (one constant per mod). To add a new
threshold:
1. Add the value to the `ELO_THRESHOLDS` / `INCOME_THRESHOLDS` list in the mod.
2. Add a matching `reach_<kind>_<N>.json` advancement (parent off the previous one).
3. Add a matching reward `.mcfunction`.

To promote a milestone to be the new "current goal":
- Swap `"frame": "goal"` → `"frame": "task"` in its JSON.
- Beef up the reward function (real bundle + chat fanfare).
- Add an entry to the HUD cascade so it shows in the action bar.

## Adding a new linear-chain quest

1. New advancement JSON parenting off the previous chain head.
2. New reward `.mcfunction`.
3. Add a line to `function/quests/hud/tick_player.mcfunction` — the cascade requires "all
   previous quests done AND this one not done" via the `advancements={...}` selector.
4. If the trigger is impossible, add the award call in the appropriate in-house mod.

## Reload

`/reload` after editing — datapack hot-reloads. Player advancement progress is preserved.
