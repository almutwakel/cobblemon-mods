# Cobblemon Server 0.3.6 — What's New

**Download**: `Cobblemon Server-0.3.6.mrpack` (127 MB)
**Install path**: drag into Prism → reinstall existing instance, worlds & settings preserved.

---

## Headline features since 0.3.4

### Carrot healing overhaul (NEW in 0.3.6)
- **Right-click a Pokémon with a carrot** → heals 60 HP (Super Potion equivalent). One carrot per heal.
- **Shift-right-click a fainted Pokémon with 4+ carrots** → revive to 60 HP. Costs 4 carrots (3 revive + 1 heal).
- **Poké Healer blocks** now charge for full-party heals:
  - Pooled HP-deficit math — no carrot overflow waste, more efficient than feeding mons one at a time.
  - Fainted mons cost 3 carrots each (1 less than reviving from inventory).
  - Click `[CONFIRM]` in chat to pay (carrots + any shortfall in $ at $5/carrot).
- **Existing potions/revives still work** — carrots are just cheaper and farmable.

### Gym leaders + Elite Four + Champion (in 0.3.5)
- 23 custom trainer JSONs with ShepskyDad-inspired competitive teams, Run-and-Bun AI.
- Gyms 1–10 main ladder (Clay → Morty), 11–18 rotating, 19 Oak (Kanto-only, very difficult), 20–23 Elite Four, 24 Champion (two Mega Rayquazas).

### Quest system + action-bar HUD (in 0.3.5)
- 24+ vanilla-advancement-based quests visible in L-tree.
- Per-player action-bar HUD shows your current main-chain quest. Toggle with `/quests hud off`.
- `/quests list` shows full tree progress.
- Rewards: eggs from the gacha pool and gacha keys.

### Cobbleloots party-EXP loot balls (in 0.3.5)
- Loot balls spawn in newly-generated chunks (Poké/Great/Ultra tiers only).
- Right-click → distributes 100/800/3000 Cobblemon EXP equally across your party.
- Single-grab — first player to claim consumes for everyone.

### Alpha Project + Fight or Flight (in 0.3.5)
- Wild Alpha Pokémon (bigger, more IVs, herd spawns) ~1% per chunk gen.
- Alphas aggro on sight and attack players. Still catchable.
- Non-Alpha wild mons remain passive unless attacked.

### Radical Cobblemon Trainers + Recobbled AI (in 0.3.5)
- 1500+ trainer templates available. Recobbled "rb" AI handles weather, hazards, setup, gimmicks.
- Trainer fights downlevel your team for fairness (`cobblemon_bridge.adjust_level.<N>` entity tag).

### In-house mods refreshed (in 0.3.5–0.3.6)
- **cobblemon-bridge** — tag-driven Cobblemon hooks (level scaling, gym defeats, quest awards).
- **cobblemon-gacha** — Cobbreeding-egg integration with HA + 2 perfect IVs, pedestal monument rewards, `/gacha admin giveegg`.
- **cobblemon-market** — income threshold quest awards on sell.
- **cobblemon-ranked** — first-PvP-win + ELO threshold quest awards.

### Dynamax disabled server-wide
- `multipleMegas` enabled so Champion's two Mega Rayquazas both Mega Evolve.

---

## How to update (existing players)

1. Open Prism Launcher.
2. Right-click your Cobblemon Server instance → **Edit** → **Version** → **Reinstall from zip**.
3. Pick `Cobblemon Server-0.3.6.mrpack`. Worlds/configs preserved.
4. Launch — Prism downloads the new + changed mods (~minutes).

---

## Known issues

- Mega Rayquaza form may not visually trigger if Cobblemon's `form: "mega"` syntax differs from what we set. Base Rayquaza still functions.
- `dynamaxcannon` removed from Lance's Eternatus (we disabled Dynamax); replaced with Dragon Pulse + Life Orb.

## Server commands new players should know

- `/quests` — show your current quest
- `/quests list` — show all quests + progress
- `/quests hud off` — disable the on-screen HUD if you find it annoying
- `/spawn` — return to spawn
- `/sethome <name>` / `/home <name>` / `/tpa <player>` — NeoEssentials warp commands
- `/market` — open the market UI
- `/ranked challenge <player>` — start a ranked PvP match
- `/gacha odds <common|rare|ultra>` — preview gacha loot tables
