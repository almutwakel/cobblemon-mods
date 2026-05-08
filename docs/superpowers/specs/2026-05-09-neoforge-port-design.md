# Cobblemon Market & Ranked — NeoForge 1.21.1 Port Design

**Date:** 2026-05-09
**Target:** Minecraft 1.21.1, NeoForge 21.1.227, Java 21, Kotlin 2.2.20
**Currency:** Cobblemon Economy 0.0.17 (loaded server-side via Sinytra Connector)
**Scope:** Two independent mods — `cobblemon-market` and `cobblemon-ranked` — ported from Fabric to pure NeoForge, with feature parity (custom GUIs rebuilt on vanilla menus; NPC shopkeeper deferred).

## 1. Goals & Non-Goals

### Goals
- Replace Fabric loader integration with native NeoForge across both mods.
- Preserve all gameplay features documented in `docs/superpowers/specs/2026-04-04-cobblemon-extensions-design.md` except where explicitly downscoped below.
- Rebuild every chest-style GUI on vanilla `MenuType` / `AbstractContainerMenu` so we don't depend on `eu.pb4:sgui` (Fabric-only).
- Keep loader-agnostic logic (`PricingEngine`, `EloCalculator`, configs, persistence) unchanged.
- Keep all existing JUnit5 unit tests intact.
- Keep JSON persistence file paths and formats unchanged so any in-flight test data on disk continues to work.

### Non-Goals
- No multi-loader (Architectury) build. Pure NeoForge only; Fabric jars no longer produced.
- No CobbleDollars integration. We stay on Cobblemon Economy because the existing reflection bridge keeps working under Connector and the closed-source CobbleDollars API has no stable bridge (`CobbleDollars Bridge` ships only a Fabric build).
- No NPC shopkeeper in the initial port. `ShopkeeperManager.kt` and the entity-spawn commands are removed; access is via `/market open`. NPCs can be reintroduced as a follow-on.
- No new mods or features beyond what existed pre-port (the inherited Fabric build's `M` working tree is the baseline).

## 2. Build System

Switch each mod from Architectury Loom + Fabric platform to **ModDevGradle** (NeoForge's current officially supported build plugin).

**Removed plugins:** `dev.architectury.loom`, `architectury-plugin`.
**Added plugin:** `net.neoforged.moddev` 2.x.

Repositories:
- `https://maven.neoforged.net/releases` (NeoForge artifacts)
- `https://artefacts.cobblemon.com/releases` (unchanged — Cobblemon NeoForge jar lives here too)
- `https://thedarkcolour.github.io/KotlinForForge/` (Kotlin for Forge)
- `mavenCentral()`

Dependencies (per mod):
- `neoForge` version `21.1.227`
- `com.cobblemon:neoforge:1.7.3+1.21.1` — replaces `com.cobblemon:fabric:...`. Cobblemon's Kotlin API surface is identical across loaders, so source-level call sites are unchanged.
- `kotlinforforge:5.11.0` — replaces `fabric-language-kotlin`. Already in the server pack.
- Drop `eu.pb4:sgui:1.6.1+1.21.1` entirely (Fabric-only).
- Drop the `fabricApi.module(...)` entries (`fabric-command-api-v2`, `fabric-lifecycle-events-v1`, `fabric-events-interaction-v0`).

`gradle.properties` keeps the existing `minecraft_version=1.21.1`, `mod_version`, `maven_group`. The `loader_version` and `fabric_version` keys are removed; a `neoforge_version=21.1.227` and `kotlin_for_forge_version=5.11.0` are added.

Java/Kotlin compiler config (`VERSION_21`, `JvmTarget.JVM_21`) carries over unchanged.

## 3. Manifest

`src/main/resources/fabric.mod.json` is replaced by `src/main/resources/META-INF/neoforge.mods.toml`:

```toml
modLoader = "kotlinforforge"
loaderVersion = "[5,)"
license = "All Rights Reserved"

[[mods]]
modId = "cobblemon_market"          # NF requires snake_case
version = "${file.jarVersion}"
displayName = "Cobblemon Market"
description = "Dynamic-pricing shopkeeper for Cobblemon"

[[dependencies.cobblemon_market]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_market]]
modId = "cobblemon"
type = "required"
versionRange = "[1.7.1,)"
ordering = "AFTER"
side = "BOTH"

[[dependencies.cobblemon_market]]
modId = "cobblemon_economy"          # Connector normalises to underscore
type = "optional"                    # soft dep — we reflect lazily
versionRange = "[0.0.16,)"
ordering = "AFTER"
side = "BOTH"
```

`cobblemon_ranked` gets the same skeleton minus the `cobblemon_economy` block (ranked has no economy hook).

Note the mod ID changes: `cobblemon-market` → `cobblemon_market`, `cobblemon-ranked` → `cobblemon_ranked`. Internal `MOD_ID` constants update accordingly. Persistence directories stay on the old names (`config/cobblemon-market/`, `config/cobblemon-ranked/`) so existing JSON state is preserved — the directory name is independent of the mod ID and we want zero migration.

## 4. Entry Point

```kotlin
@Mod(CobblemonMarket.MOD_ID)
class CobblemonMarket(modBus: IEventBus, container: ModContainer) {
    init {
        // existing init body, with FabricLoader.configDir replaced by FMLPaths.CONFIGDIR.get()
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MarketCommands.register(event.dispatcher)
    }

    private fun onServerTick(event: ServerTickEvent.Post) { /* hourly recovery */ }

    companion object {
        const val MOD_ID = "cobblemon_market"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
        // existing static state (config, items, marketStore, playerSpendStore)
    }
}
```

The Kotlin-for-Forge `@Mod` annotation is on a class (not an `object`); the constructor is invoked once by NeoForge during mod loading. Static state lives in the companion. This is the standard KFF idiom.

`cobblemon-ranked` mirrors this pattern.

## 5. Fabric → NeoForge API translation

| Concern | Fabric (current) | NeoForge (port) |
|---|---|---|
| Mod entrypoint | `ModInitializer.onInitialize()` | `@Mod` class constructor (KFF) |
| Command registration | `CommandRegistrationCallback.EVENT.register { d, _, _ -> ... }` | `NeoForge.EVENT_BUS` listener for `RegisterCommandsEvent` |
| Server tick | `ServerTickEvents.END_SERVER_TICK.register { ... }` | `NeoForge.EVENT_BUS` listener for `ServerTickEvent.Post` |
| Player join/leave | `ServerPlayConnectionEvents` (ranked, if used) | `PlayerEvent.PlayerLoggedInEvent` / `PlayerLoggedOutEvent` on game bus |
| Server lifecycle | `ServerLifecycleEvents.SERVER_STARTED` | `ServerStartedEvent` on game bus |
| Config dir | `FabricLoader.getInstance().configDir` | `FMLPaths.CONFIGDIR.get()` |
| Mod discovery | `FabricLoader.getInstance().getModContainer("...")` | `ModList.get().getModContainerById("...")` (only used in CE bridge — see §7) |
| Kotlin support | `fabric-language-kotlin` | `kotlinforforge` |

All Cobblemon API calls — `CobblemonEvents`, `BattleBuilder`, `Pokemon`, `PCStore`, `PartyStore`, `Species`, etc. — remain literally unchanged. Cobblemon's published Kotlin API is loader-agnostic; the per-loader integration lives in their `:fabric` / `:neoforge` artifacts and we just swap which one we depend on.

## 6. Custom GUIs on vanilla menus

The existing `ShopGui`, `TransactionGui`, and `TeamSelectionGui` are rewritten on top of vanilla `AbstractContainerMenu` + `MenuProvider`. No external GUI library.

Pattern:

```kotlin
class ShopMenu(
    containerId: Int,
    inventory: Inventory,
    private val ctx: ShopContext,
) : AbstractContainerMenu(MENU_TYPE.get(), containerId) {

    private val displayInv = SimpleContainer(SLOT_COUNT)

    init {
        // place display slots (read-only, click-to-select)
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            addSlot(DisplaySlot(displayInv, row * COLS + col, 8 + col * 18, 18 + row * 18))
        }
        // player inventory + hotbar slots placed below for visual completeness
        repaint(ctx.snapshot())
    }

    override fun stillValid(player: Player) = true
    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        // intercept clicks; route to controller; never modify the underlying SimpleContainer client-side
    }

    companion object {
        val MENU_TYPE: DeferredHolder<MenuType<*>, MenuType<ShopMenu>> = REGISTRAR.register("shop") { ... }
    }
}
```

Why this pattern works:
- Server-driven: `ServerPlayer.openMenu(MenuProvider)` opens a synchronised inventory. The client renders it via the registered `MenuType` — for chest-style menus we use the existing vanilla chest screen, no client mod needed.
- Click handling lives in `clicked(...)` server-side. We never write to the displayed `SimpleContainer` client-side; instead, we treat clicks as intent (e.g. "user picked rare candy") and re-open or refresh the menu with a new state.
- For dynamic price displays we rebuild the slot ItemStacks server-side (item + lore lines containing the price) and call `broadcastChanges()` after each mutation.

Files affected:
- **Market:** `gui/ShopGui.kt` → `gui/ShopMenu.kt` + `gui/ShopController.kt` (controller decouples menu from market state). `gui/TransactionGui.kt` → `gui/TransactionMenu.kt` + `gui/TransactionController.kt`. The economy bridge (currently embedded in `TransactionGui`, lines 221–268) extracts to `economy/EconomyBridge.kt` (§7).
- **Ranked:** `gui/TeamSelectionGui.kt` → `gui/TeamSelectionMenu.kt` + `gui/TeamSelectionController.kt`.

`MenuType` registration uses NeoForge's `DeferredRegister<MenuType<*>>` and the constructor receives an `IExtendedScreenHandlerType`-equivalent for the initial state payload — NeoForge has `IMenuTypeExtension.create(...)` for this.

## 7. Cobblemon Economy bridge

Move the reflection-based bridge out of `TransactionGui` into a dedicated `economy/EconomyBridge.kt` (object/singleton). This is now the only place in the codebase that knows about Cobblemon Economy.

Public API (used by transaction logic and `/market admin balance` commands):

```kotlin
object EconomyBridge {
    fun getBalance(uuid: UUID): Int           // returns 0 if CE absent
    fun deposit(uuid: UUID, amount: Int)      // no-op if CE absent
    fun withdraw(uuid: UUID, amount: Int): Boolean  // returns false if CE absent or insufficient
    fun isAvailable(): Boolean                // true once a successful CE call has occurred
}
```

Implementation notes:
- Drop the Fabric-specific entrypoint-container lookup. CE's `CobblemonEconomy.getEconomyManager()` is a public *static* method, so `Class.forName("com.cobblemon.economy.fabric.CobblemonEconomy").getMethod("getEconomyManager").invoke(null)` works regardless of loader. Cache the resolved `Class` and `Method` references on first success.
- The class FQN starts with `com.cobblemon.economy.fabric.` — Connector preserves package names of loaded Fabric mods, so this FQN remains valid on NeoForge under Connector.
- Wrap every reflective call in a try/catch that logs once and degrades gracefully (return 0 / false).
- Optional but cheap: at mod startup, check `ModList.get().isLoaded("cobblemon_economy")` to log a one-line "currency: enabled" or "currency: disabled" status; gameplay-affecting commands (`/market buy`) return a clear error to the player when CE is missing rather than silently failing.

## 8. Persistence

Unchanged. Files remain at:
- `<configDir>/cobblemon-market/config.json`, `items.json`, `state.json`, `player_spend.json`
- `<configDir>/cobblemon-ranked/config.json`, `elo.json`, `team.json`

`<configDir>` resolves to `FMLPaths.CONFIGDIR.get()` on NeoForge (same path the Fabric loader was using — `<server>/config/`). No data migration needed.

## 9. Tests

Existing unit tests in `src/test/kotlin/`:
- `pricing/PricingEngineTest.kt`
- `elo/EloCalculatorTest.kt` (and any other M-state ranked tests)

They're loader-agnostic JUnit5 — keep verbatim. The build still runs `useJUnitPlatform()`.

We do not add new test scaffolding for the GUI rewrites in this initial port (server-side menu code without a real client is awkward to test in unit form). The pricing/ELO logic is already test-covered; the GUIs sit on top of that and are observed via in-game sanity checks.

## 10. Server-side mods (already in place)

The following server mods are required and **already present** in `cobblemon-server/mods/`:
- `neoforge-21.1.227` (server)
- `kotlinforforge-5.11.0-all.jar`
- `Cobblemon-neoforge-1.7.3+1.21.1.jar`
- `connector-2.0.0-beta.14+1.21.1-full.jar`
- `forgified-fabric-api-0.116.7+2.2.4+1.21.1.jar`
- `cobblemon-economy-0.0.17.jar` (Fabric jar, loaded via Connector — added 2026-05-08)

Our two new NeoForge jars (`cobblemon-market-X.Y.Z.jar`, `cobblemon-ranked-X.Y.Z.jar`) drop into the same `mods/` folder.

`cobblemon-server-changes.md` will get an entry recording the mod additions.

## 11. Risks

- **Connector loading CE through a future Sinytra update.** Connector betas can break Fabric mods between releases. Mitigation: pin the exact `connector-2.0.0-beta.14+1.21.1-full.jar` we verified against; revisit when Sinytra ships a stable.
- **Cobblemon Economy reflection drift.** CE 0.0.17's `EconomyManager` API matches what our bridge expects (verified by `javap` against the jar). If CE updates and renames `getBalance/addBalance/subtractBalance`, the bridge degrades to no-ops. Mitigation: log once on first reflection failure with the full exception so the cause is obvious.
- **Vanilla menu sync subtleties.** Click latency between server and client can produce stale prices in `TransactionMenu` if the user clicks faster than the server can rebroadcast. Mitigation: stamp each menu instance with a generation counter; reject clicks bound to a stale generation by re-opening the menu.
- **Removing NPC shopkeeper changes the player-facing access pattern** (right-click villager → `/market open`). Communicate this to players; admins can still place named villagers as flavor.

## 12. Out of Scope

- Reintroducing the NPC shopkeeper system — deferred.
- Switching to CobbleDollars / Impactor — deferred until either (a) CobbleDollars Bridge gets a NeoForge build, or (b) Cobblemon Economy stops being maintained.
- Multi-loader builds.
- Touching `cobblemon-showcase` (separate mod with its own spec already in `docs/superpowers/specs/2026-04-07-cobblemon-showcase-design.md`).
