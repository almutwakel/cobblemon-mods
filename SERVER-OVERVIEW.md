# Server Overview — what changed from upstream

This is a high-level "what the server is" document, not a changelog. For the chronological
trail see `cobblemon-server-changes.md`. Baseline is upstream **`Cobblemon Server-0.2.1.mrpack`**
(MC 1.21.1 / NeoForge 21.1.227). Current pack is **`Cobblemon Server-0.3.24.mrpack`**.

---

## New in-house mods (5)

| Mod | Purpose |
|---|---|
| **cobblemon-bridge** | Tag-driven hook layer between Cobblemon events and the rest of the server. Owns the level cap, gym progression, wild balance, E4 gauntlet, command aliases, egg-by-defeats logic, and reflection bridges to RCTmod + Cobreeding + cobblemon-economy. |
| **cobblemon-gacha** | Lootbox crates (Common/Rare/Ultra). Keys come from daily login, ranked battles, and gym defeats. Used by quest rewards via `/gacha admin` subcommands. |
| **cobblemon-market** | Dynamic-pricing stock market — items have a base price + elasticity, and prices move with every trade. Healer and quest economy plug into this. |
| **cobblemon-ranked** | Per-player ELO PvP ladder with `/challenge`, `/accept`, `/stats`, leaderboard. Decay applies after inactivity. |
| **cobblemon-carrots** | Healing overhaul — carrots are medicinal. Right-click a Pokémon → +30 HP. Poké Healer block requires carrots + money. Auto-heal is disabled (carrots are the heal mechanic). |

## Modrinth mods added on top of baseline

- **Cobblemon ecosystem**: Cobbleworkers (pokemon automation), Cobbreeding (egg breeding), Unchained (catch/IV/HA streak bonuses), Cobbleloots (loot balls dropping Pokémon EXP), Cobblemon Alpha Project + Fight or Flight Reborn (currently disabled — alphas off until we build proper alpha behavior), Cobblemon Recobbled (gym leader AI), Cobblemon Economy, LegendaryMonuments
- **Trainer framework**: RCTmod (Radical Cobblemon Trainers) + RCT API — 1500+ wild trainers + the trainer-spawner block backbone we use for gym leaders
- **QoL / utility**: NeoEssentials (homes, warps, tablist, tpa), Counter (Cobblemon stat tracker), Sophisticated Backpacks, MineColonies + dependencies, StarterKit, Waystones, Chipped, Terralith, Serene Seasons, Domum Ornamentum

---

## Game systems

### Level cap (cobblemon-bridge)
`cap = 20 + 5 × mainline_gyms_beaten`, max 70 after gym 10. Beating Elite Four (gym 23) = uncapped. Rotating gyms (11–18) don't contribute. The cap affects:
- **Wild spawn levels** — over-cap spawns get randomized to `[cap-5, cap]`, and the species de-evolves to fit (a L40 Blastoise becomes L14 Squirtle, not "L14 Blastoise"). Legendaries exempt.
- **Gym leader downleveling** — each gym JSON has `adjustPlayerLevels: true`, so your team scales DOWN to the gym's level for the fight.
- **Trade cap** — can't receive an over-cap mon via trade. Legendaries/E4-complete players exempt.

**What we tried first and dropped**: wild-battle downleveling (clamping your team's `effectedPokemon` to cap in wild fights). Felt overconstraining — players couldn't use their fully-leveled team to mop up over-cap encounters. Disabled in 0.3.19. Only gym fights downlevel now.

### Quest system (server-quests datapack + cobblemon-bridge `/quests` command)
~40 advancements forming a mainline + side branches. HUD shows the lowest uncompleted mainline step on the action bar; `/quests` and `/quests list` provide chat views. Side branches (ELO ladder, income ladder, Minecolony) hang off mainline nodes but don't appear in the HUD.

Reward delivery is a tag-based dispatcher (`_finalize.mcfunction`). Each quest-complete: print the chat header → 1 second pause → run the actual gacha/give command. **Without that pause** the gacha pull's broadcast races the quest-complete announce and players see them out of order.

### Gym progression (cobblemon-bridge)
24 gym slots. Mainline 1–10 (Clay → Morty) at L15→L60 in +5 steps. Rotating 11–18 (Viola, Cheren, etc.) flat at L60 — optional, don't gate cap. Oak (19) at L70. Elite Four (20–23) at L65. Champion (24) at L70.

**Gating** (`GymPrereqHook`): right-clicking a gym leader is cancelled if you haven't beaten the prereq. Gym 2–10 require gym N−1. Gym 11–23 all unlock after gym 10. Gym 24 requires gym 23.

**Elite Four gauntlet** (`E4GauntletHook`): gyms 20→23 must be beaten consecutively. Winning auto-teleports the next E4 trainer to you and starts the battle (via RCTmod's `TrainerMob.startBattleWith` via reflection). Losing/fleeing/disconnecting resets you to E4-1.

### Wild Pokémon spawning
Vanilla Cobblemon spawn pipeline is untouched (same biome pools, same rates, same density). After spawn, the bridge re-tunes anything significantly over cap: pick random level in `[cap-5, cap]`, walk back through `LevelUpEvolution.requirements` to a species that fits the new level. Legendaries skip the clamp entirely.

**What we tried first**: cancelling over-cap spawns. Bad — low-cap players saw zero spawns in high-tier biomes. Switched to clamp-not-cancel in 0.3.10. Then user noticed L18 Blastoise looked silly → added de-evolution walking in 0.3.12.

### Eggs (cobblemon-gacha + cobblemon-bridge)
Quest/login rewards grant Cobreeding pokemon eggs. The natural Cobreeding playtime-based hatch is **disabled** by bumping each egg's `TIMER` data-component to ~999M on first inventory scan (via reflection bridge to `PokemonEgg.Companion`).

Instead, eggs hatch by defeating wild Pokémon. Thresholds: common = 5, uncommon = 10, rare = 15, ultra = 20. Only the **leftmost tagged egg** progresses per defeat. Chat shows progress (`Egg progress: 3/5 defeats toward your Pikachu egg`); on hit threshold the egg's TIMER is set to 1, Cobreeding's next inventoryTick hatches it.

### Market (cobblemon-market)
Dynamic pricing on 6 items (Poké/Great/Ultra Ball, Rare Candy, Revive, Carrot). Each has `baseBuyPrice`, `baseSellPrice`, `baseStock`, `elasticity`. Trades move stock and prices; hourly restock pulls back toward baseStock. The Poké Healer block uses `TradeOps.buyForConsumption(...)` to buy carrots from the market at live price when the player is short — same dynamic pricing as `/buy` but the items vanish into the heal instead of landing in the inventory.

### Carrot healing (cobblemon-carrots)
- Right-click Pokémon with a carrot → +30 HP, consumes 1 carrot
- Poké Healer block opens a chat prompt: full party heal + revive at $5 base per missing carrot (or live market price). Charges money for any carrots short.
- Cobblemon's auto-heal disabled (`healPercent: 0.0`). Fainted mons revive at 20% HP after 8 minutes (`defaultFaintTimer: 480`). Alive mons must use carrots to heal.

### Ranked PvP (cobblemon-ranked)
`/challenge` → `/accept` → battle → ELO updates. Decay after inactivity. `/ranked leaderboard`. First-ranked-win-per-day grants a Common Key from gacha.

### Command aliases (cobblemon-bridge)
Layer of short commands so players don't have to remember which mod owns what:
- Market: `/buy /sell /prices`
- Ranked: `/challenge /accept /decline /stats`
- Economy: `/money /balance /pay`
- Homes: `/sethome /home` (no-arg variants forward to NeoEssentials)

NeoEssentials's own `/balance /pay /baltop /eco` are **disabled** in NE config so they don't clash with the cobbledollar economy.

---

## Behavior tweaks (config-level)

| Setting | Value | Was |
|---|---|---|
| `difficulty` | peaceful | easy |
| `gamerule keepInventory` | true | false |
| `function-permission-level` | 4 | 2 (needed for reward functions calling `gacha admin`) |
| `allowUnsafeCommands` (NeoEssentials) | true | false (it was blocking `/pokespawn name=value` syntax) |
| Cobblemon `healPercent` | 0.0 | 0.05 |
| Cobblemon `defaultFaintTimer` | 480s | 300s |
| `battleVictoryReward` (cobblemon-economy) | $5 | $100 |
| `capture_event_base_reward` | $2 | $100 |
| `startingBalance` | $0 | $1000 |
| `economyEnabled` (NeoEssentials) | false | true |
| Alphas (`doAlphaSpawning`) | false | true |
| Unchained streak `lockToPlayer` | true | false (was server-wide before) |
| NeoEssentials tablist `refreshInterval` | 60t (3s) | 20t (1s) |

## Datapacks

- **server-quests** — 372 files: the quest tree, ~40 advancement JSONs, ~50 reward mcfunctions, the HUD ticker, `_finalize` reward dispatcher, plus override stubs that suppress every non-server advancement (vanilla MC, Cobblemon, Mega Showdown, RCTmod) so only the `Server Progression` tab shows in the advancement GUI. MineColonies tabs intentionally kept.
- **server-gyms** — 49 files: trainer JSONs for all 24 gym leaders. Hand-tuned EVs/IVs/natures (special attackers get atk IV 0, walls get bold/calm, etc.).
- **server-lootballs** — Cobbleloots empty.json fallback (drops 1-3 carrots so loot balls always give *something*).

## Network / admin

- `run.sh` echoes the LAN IP at startup. `server-ip` is empty (binds all interfaces, DHCP-safe).
- mrpack includes `server-overrides/`: bundled in 0.3.20+ so fresh server installs auto-deploy the datapacks + configs.
- Client overrides include `options.txt` with: FOV 80, brightness 80%, fast graphics + clouds, GUI scale 5, Cobblemon party-summary keybind = `P`, social interactions = `\`.

---

## Insights worth knowing

**Cobreeding eggs use a `TIMER` data component, not NBT.** Reading/writing it requires the new MC 1.20.5+ component system. The reflection bridge resolves `PokemonEgg.Companion.getTIMER()` once at boot.

**RCTmod trainers aren't Cobblemon NPCs.** `BattleBuilder.pvn()` won't work for them. Use `TrainerMob.startBattleWith(player)` via reflection instead — that's what the E4 gauntlet auto-chain uses.

**Cobblemon's BATTLE_VICTORY event doesn't expose the opposing entity** for `TrainerBattleActor` — only the trainer name. To attribute a victory to a specific gym, we stash the gym_id at `EntityInteract` time keyed by player UUID, then consume it on victory. Same pattern used by `GymDefeatHook` and `E4GauntletHook`.

**Quest-reward chat ordering is broken without a delay.** `gacha admin giveegg` triggers a broadcast that lands on the same tick as the quest-complete tellraw. Looks wrong in chat. The `_finalize.mcfunction` dispatcher runs 20 ticks (1s) after the per-quest reward function, which fixes the order.

**Advancement-tab override is non-obvious.** Setting `"hidden": true` on a root advancement does NOT remove its tab. Replacing the root with `{"criteria":{"never":{"trigger":"minecraft:impossible"}}}` (no `display` field, never-trigger criterion) DOES — and children orphan to nothing. That's the pattern used to hide every vanilla MC + Cobblemon + Mega Showdown + RCTmod tab.

**NeoForge's `PlayerInteractEvent.EntityInteract` at HIGHEST priority is the right place to gate gym battles.** Cancelling there stops RCTmod's battle-start handler before it runs. Cobblemon's `BATTLE_STARTED_PRE` is too late — the actor list doesn't expose the entity anymore.

**The market makes the healer prices feel alive.** Buy 100 carrots from `/market`, prices barely move. Sell 100 carrots, prices crash. The healer "missing carrots" cost reflects this in real time, so when a player drains the market it self-balances back.
