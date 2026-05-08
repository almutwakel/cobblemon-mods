# NeoForge 1.21.1 Port — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port `cobblemon-market` and `cobblemon-ranked` from Fabric 1.21.1 to pure NeoForge 1.21.1 with feature parity (custom GUIs rebuilt on vanilla `MenuType`, NPC shopkeeper deferred), keeping all unit tests green and existing JSON state on disk untouched.

**Architecture:** Single-loader NeoForge mods built with `net.neoforged.moddev` (ModDevGradle). Kotlin entry points use `kotlinforforge`'s `@Mod` class form. All chest UIs are server-driven via `AbstractContainerMenu` + `ServerPlayer.openMenu(MenuProvider)` — no GUI library dependency. Cobblemon Economy stays as the currency, accessed through a single `EconomyBridge` object that uses plain `Class.forName` reflection (no FabricLoader entrypoint dance).

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.227, Kotlin 2.2.20, ModDevGradle 2.x, KotlinForForge 5.11.0, Cobblemon 1.7.3+1.21.1, JUnit 5. Server-side: Sinytra Connector + Forgified Fabric API (already installed) load Cobblemon Economy 0.0.17 (Fabric jar) under NeoForge.

**Spec:** `docs/superpowers/specs/2026-05-09-neoforge-port-design.md`

**File touch summary:**

Per mod (mirror layout):

| Action | Path | Purpose |
|---|---|---|
| Replace | `build.gradle.kts` | ModDevGradle plugin + NF deps |
| Replace | `settings.gradle.kts` | Drop `architectury.dev` repo |
| Replace | `gradle.properties` | Drop `loader_version`/`fabric_version`, add `neoforge_version`, `kotlin_for_forge_version` |
| Delete | `src/main/resources/fabric.mod.json` | superseded |
| Create | `src/main/resources/META-INF/neoforge.mods.toml` | NF manifest |
| Modify | entry point (`CobblemonMarket.kt`/`CobblemonRanked.kt`) | `@Mod` class + event listeners |
| Modify | `commands/*Commands.kt` | drop `FabricLoader` imports |
| Modify | `pricing/`/`elo/`/`config/`/`data/` | unchanged source — verify imports stay clean |

Market-only:
| Action | Path | Purpose |
|---|---|---|
| Delete | `gui/ShopGui.kt`, `gui/TransactionGui.kt`, `shop/ShopkeeperManager.kt` | superseded |
| Create | `economy/EconomyBridge.kt` (+ test) | extracted CE bridge |
| Create | `gui/MenuRegistry.kt` | `DeferredRegister<MenuType<*>>` |
| Create | `gui/ShopMenu.kt` (+ provider) | vanilla menu rewrite |
| Create | `gui/TransactionMenu.kt` (+ provider) | vanilla menu rewrite |

Ranked-only:
| Action | Path | Purpose |
|---|---|---|
| Delete | `gui/TeamSelectionGui.kt` | superseded |
| Create | `gui/MenuRegistry.kt` | `DeferredRegister<MenuType<*>>` |
| Create | `gui/TeamSelectionMenu.kt` (+ provider) | vanilla menu rewrite |

---

## Pre-flight

### Task 0: Commit current working-tree baseline

Before changing anything, commit the in-flight Fabric work as the porting baseline. The git status at start shows ~10 modified Fabric files plus three untracked source files. We commit those untouched so the port operates on a known-good Fabric build.

**Files:** all currently-modified and untracked source under `cobblemon-market/src/`, `cobblemon-ranked/src/`. Specifically include `cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/PlayerSpendStore.kt` and `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/data/TeamStore.kt` (both untracked).

- [ ] **Step 1: Stage source-only changes**

```bash
cd /Users/almutwakel/Documents/Projects/minecraft
git add cobblemon-market/src cobblemon-ranked/src
```

- [ ] **Step 2: Verify staging is source-only**

```bash
git status --short
```
Expected: only `M`/`A` lines under `cobblemon-market/src` and `cobblemon-ranked/src`. Anything else (`.DS_Store`, zip files, server dirs) must remain unstaged.

- [ ] **Step 3: Commit baseline**

```bash
git commit -m "$(cat <<'EOF'
chore: baseline pre-NeoForge-port working tree

Captures in-flight Fabric source for both mods so the NeoForge port
operates on a clean checkpoint. No behavior change.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: Tag the baseline for safety**

```bash
git tag fabric-baseline-pre-port
```

This makes the pre-port state easy to diff against and recover from if the port goes sideways.

---

## Phase 1 — `cobblemon-market` port

Each task is run from the repo root. All paths are relative to `/Users/almutwakel/Documents/Projects/minecraft/`.

### Task 1: Replace `cobblemon-market` build files

**Files:**
- Modify: `cobblemon-market/build.gradle.kts`
- Modify: `cobblemon-market/settings.gradle.kts`
- Modify: `cobblemon-market/gradle.properties`

- [ ] **Step 1: Replace `cobblemon-market/build.gradle.kts` with the ModDevGradle version**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.78"
    kotlin("jvm") version "2.2.20"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    maven("https://artefacts.cobblemon.com/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    mavenCentral()
}

neoForge {
    version = project.property("neoforge_version") as String

    runs {
        register("server") {
            server()
            programArguments.add("--nogui")
        }
        register("client") {
            client()
        }
    }

    mods {
        register("cobblemon_market") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.property("kotlin_for_forge_version")}")
    implementation("com.cobblemon:neoforge:${project.property("cobblemon_version")}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(project.properties)
        }
    }

    compileJava {
        options.release = 21
    }

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
```

- [ ] **Step 2: Replace `cobblemon-market/settings.gradle.kts`**

```kotlin
rootProject.name = "cobblemon-market"

pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}
```

- [ ] **Step 3: Replace `cobblemon-market/gradle.properties`**

```
org.gradle.jvmargs=-Xmx4G
minecraft_version=1.21.1
neoforge_version=21.1.227
kotlin_for_forge_version=5.11.0
cobblemon_version=1.7.3+1.21.1
mod_version=1.0.0
maven_group=com.cobblemonmarket
```

- [ ] **Step 4: Verify Gradle still resolves**

```bash
cd cobblemon-market && ./gradlew --no-daemon help -q
```
Expected: success (no error). The compile won't pass yet because the source still imports Fabric APIs — that's fine, this is just verifying the plugin chain loads.

### Task 2: Replace `fabric.mod.json` with `neoforge.mods.toml` for market

**Files:**
- Delete: `cobblemon-market/src/main/resources/fabric.mod.json`
- Create: `cobblemon-market/src/main/resources/META-INF/neoforge.mods.toml`

- [ ] **Step 1: Create the NeoForge manifest**

```toml
modLoader = "kotlinforforge"
loaderVersion = "[5,)"
license = "All Rights Reserved"

[[mods]]
modId = "cobblemon_market"
version = "${version}"
displayName = "Cobblemon Market"
description = "Dynamic-pricing shopkeeper for Cobblemon"
authors = "almutwakel"

[[dependencies.cobblemon_market]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_market]]
modId = "kotlinforforge"
type = "required"
versionRange = "[5,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_market]]
modId = "cobblemon"
type = "required"
versionRange = "[1.7.1,)"
ordering = "AFTER"
side = "BOTH"

[[dependencies.cobblemon_market]]
modId = "cobblemon_economy"
type = "optional"
versionRange = "[0.0.16,)"
ordering = "AFTER"
side = "BOTH"
```

- [ ] **Step 2: Delete the old Fabric manifest**

```bash
git rm cobblemon-market/src/main/resources/fabric.mod.json
```

### Task 3: Rewrite `CobblemonMarket.kt` entry point

**Files:**
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt`

The current Fabric `object` implementing `ModInitializer` becomes a `@Mod` class with companion-stored static state. NeoForge invokes the constructor; KFF discovers the `@Mod` annotation through the `kotlinforforge` mod loader configured in `neoforge.mods.toml`.

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.cobblemonmarket

import com.cobblemonmarket.commands.MarketCommands
import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.data.MarketStore
import com.cobblemonmarket.data.PlayerSpendStore
import com.cobblemonmarket.gui.MenuRegistry
import com.cobblemonmarket.pricing.PricingEngine
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonMarket.MOD_ID)
class CobblemonMarket(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Market initializing...")

        val configDir = FMLPaths.CONFIGDIR.get()
        config = MarketConfig.load(configDir)
        items = ItemConfig.load(configDir)
        marketStore = MarketStore(configDir)
        marketStore.load()
        playerSpendStore = PlayerSpendStore(configDir)
        playerSpendStore.load()

        MenuRegistry.MENUS.register(modBus)

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)

        logger.info("Cobblemon Market initialized! ${items.size} items, market state loaded.")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MarketCommands.register(event.dispatcher)
    }

    private var recoveryTickCounter: Int = 0

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        recoveryTickCounter++
        if (recoveryTickCounter % 72000 == 0) {
            applyRecoveryToAll()
        }
    }

    private fun applyRecoveryToAll() {
        var updated = false
        for ((itemId, _) in items) {
            val state = marketStore.getOrCreate(itemId)
            val oldFactor = state.priceFactor
            state.priceFactor = PricingEngine.applyRecovery(
                oldFactor, config.recoveryRatePerHour, config.factorCeiling
            )
            if (state.priceFactor != oldFactor) updated = true
        }
        if (updated) {
            marketStore.save()
            logger.info("Hourly price recovery applied")
        }
    }

    companion object {
        const val MOD_ID = "cobblemon_market"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: MarketConfig
        var items: Map<String, ItemEntry> = emptyMap()
        lateinit var marketStore: MarketStore
        lateinit var playerSpendStore: PlayerSpendStore
    }
}
```

Note: `MOD_ID` switched from `cobblemon-market` to `cobblemon_market` (NeoForge requires `[a-z][a-z0-9_]*`). Persistence directories continue to use the kebab-case name — see Task 5.

### Task 4: Strip Fabric imports from `MarketCommands.kt`

**Files:**
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt`

Three Fabric usages need replacing (lines 12-13 imports, lines 34, 227, 242 usages). The economy-bridge code at lines ~227-242 moves to its own file in Task 6 — for this task, just patch the `FabricLoader.configDir` call and the version-lookup call.

- [ ] **Step 1: Replace the `import` lines**

Remove:
```kotlin
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
```

Add:
```kotlin
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
```

- [ ] **Step 2: Replace the version lookup at line ~34**

Change:
```kotlin
val version = FabricLoader.getInstance().getModContainer(CobblemonMarket.MOD_ID)
    .map { it.metadata.version.friendlyString }.orElse("unknown")
```
To:
```kotlin
val version = ModList.get().getModContainerById(CobblemonMarket.MOD_ID)
    .map { it.modInfo.version.toString() }.orElse("unknown")
```

- [ ] **Step 3: Replace `configDir` lookup at line ~242**

Change `val configDir = FabricLoader.getInstance().configDir` to `val configDir = FMLPaths.CONFIGDIR.get()`.

- [ ] **Step 4: Leave the economy-reflection block at lines ~225-235 intact for now**

It will be deleted in Task 6 when we extract `EconomyBridge`. Do not touch it in this task.

### Task 5: Persistence directory rename guard

**Files:**
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/ItemConfig.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/MarketStore.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/PlayerSpendStore.kt`

Old persistence root: `<configDir>/cobblemon-market/`. We want to keep that exact path (kebab-case) so existing JSON files persist after the mod ID rename to `cobblemon_market`.

- [ ] **Step 1: Audit each file for hardcoded `cobblemon-market` directory references**

```bash
grep -nE 'cobblemon-market|CobblemonMarket\.MOD_ID|MOD_ID' cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/*.kt cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/*.kt
```
For each file: if it uses `CobblemonMarket.MOD_ID` to build a path, hardcode the literal `"cobblemon-market"` instead so the directory name doesn't follow the mod-ID rename.

- [ ] **Step 2: Define a single `PERSISTENCE_DIR_NAME` constant**

In `cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt`'s companion, alongside `MOD_ID`:
```kotlin
const val PERSISTENCE_DIR_NAME = "cobblemon-market"
```
Then update each `config/`, `data/` file's path-building code to use `CobblemonMarket.PERSISTENCE_DIR_NAME` instead of `MOD_ID`. This collapses the kebab/snake split into one obvious place.

- [ ] **Step 3: Re-grep to confirm no `MOD_ID` is still used as a directory name**

```bash
grep -nE 'MOD_ID' cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/*.kt cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/*.kt
```
Expected: no matches in path-building code (matches in logger names are fine).

### Task 6: Extract `EconomyBridge` and refactor reflection

**Files:**
- Create: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/economy/EconomyBridge.kt`
- Create: `cobblemon-market/src/test/kotlin/com/cobblemonmarket/economy/EconomyBridgeTest.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt` (replace inline reflection at lines ~225-235)
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt` (replace inline reflection at lines 221-269) — this file will be deleted in Task 9, but during Task 6 we just point its calls at `EconomyBridge` so the file still compiles after the extraction step.

Replace the FabricLoader-entrypoint lookup with a plain `Class.forName` call against the public static `getEconomyManager()`. Cache the resolved `Method` references on first success. Degrade gracefully when Cobblemon Economy isn't installed.

- [ ] **Step 1: Write the failing test first**

Create `cobblemon-market/src/test/kotlin/com/cobblemonmarket/economy/EconomyBridgeTest.kt`:

```kotlin
package com.cobblemonmarket.economy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class EconomyBridgeTest {

    @Test
    fun `getBalance returns zero when economy class is not on classpath`() {
        // The test classpath does not contain Cobblemon Economy
        val bridge = EconomyBridge
        assertEquals(0, bridge.getBalance(UUID.randomUUID()))
    }

    @Test
    fun `withdraw returns false when economy class is not on classpath`() {
        assertFalse(EconomyBridge.withdraw(UUID.randomUUID(), 100))
    }

    @Test
    fun `deposit is a no-op when economy class is not on classpath`() {
        // does not throw
        EconomyBridge.deposit(UUID.randomUUID(), 100)
    }

    @Test
    fun `isAvailable is false until a successful call has occurred`() {
        // After the above calls, all reflective lookups failed
        assertFalse(EconomyBridge.isAvailable())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

```bash
cd cobblemon-market && ./gradlew --no-daemon test
```
Expected: compile failure ("EconomyBridge not found").

- [ ] **Step 3: Create `EconomyBridge.kt` with the minimal implementation that makes the test pass**

```kotlin
package com.cobblemonmarket.economy

import com.cobblemonmarket.CobblemonMarket
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object EconomyBridge {

    private const val ECONOMY_CLASS = "com.cobblemon.economy.fabric.CobblemonEconomy"
    // EconomyManager method names — verified against cobblemon-economy 0.0.17 via javap
    private const val M_GET_BALANCE = "getBalance"
    private const val M_ADD_BALANCE = "addBalance"
    private const val M_SUB_BALANCE = "subtractBalance"

    @Volatile private var resolvedManager: Any? = null
    @Volatile private var getBalanceMethod: Method? = null
    @Volatile private var addBalanceMethod: Method? = null
    @Volatile private var subBalanceMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)
    private val available = AtomicBoolean(false)

    private fun manager(): Any? {
        resolvedManager?.let { return it }
        return try {
            val cls = Class.forName(ECONOMY_CLASS)
            val mgr = cls.getMethod("getEconomyManager").invoke(null)
            resolvedManager = mgr
            getBalanceMethod = mgr.javaClass.getMethod(M_GET_BALANCE, UUID::class.java)
            addBalanceMethod = mgr.javaClass.getMethod(M_ADD_BALANCE, UUID::class.java, BigDecimal::class.java)
            subBalanceMethod = mgr.javaClass.getMethod(M_SUB_BALANCE, UUID::class.java, BigDecimal::class.java)
            available.set(true)
            mgr
        } catch (e: ClassNotFoundException) {
            warnOnce("Cobblemon Economy not loaded — currency operations disabled")
            null
        } catch (e: Throwable) {
            warnOnce("Cobblemon Economy reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun getBalance(uuid: UUID): Int = try {
        val mgr = manager() ?: return 0
        (getBalanceMethod!!.invoke(mgr, uuid) as BigDecimal).toInt()
    } catch (e: Throwable) {
        CobblemonMarket.logger.error("EconomyBridge.getBalance failed", e); 0
    }

    fun deposit(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        try {
            val mgr = manager() ?: return
            addBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount))
        } catch (e: Throwable) {
            CobblemonMarket.logger.error("EconomyBridge.deposit failed", e)
        }
    }

    fun withdraw(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            // Cobblemon Economy's subtractBalance returns boolean (true if successful)
            subBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount)) as Boolean
        } catch (e: Throwable) {
            CobblemonMarket.logger.error("EconomyBridge.withdraw failed", e); false
        }
    }

    fun isAvailable(): Boolean = available.get()

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) {
            CobblemonMarket.logger.warn(msg)
        }
    }
}
```

- [ ] **Step 4: Run the test, expect pass**

```bash
cd cobblemon-market && ./gradlew --no-daemon test --tests com.cobblemonmarket.economy.EconomyBridgeTest
```
Expected: 4 passed.

- [ ] **Step 5: Replace inline reflection in `MarketCommands.kt`**

Find the block around line 225-235 that currently does `FabricLoader.getInstance() ... getEntrypointContainers ... getEconomyManager().invoke(...)`. Replace the whole block with calls to `EconomyBridge.getBalance(uuid)`. Add `import com.cobblemonmarket.economy.EconomyBridge` at the top of the file.

- [ ] **Step 6: Replace inline reflection in `TransactionGui.kt`**

The four private methods on lines 225-269 (`getEconomyManager`, `getPlayerBalance`, `subtractBalance`, `addBalance`) become one-liner delegates to `EconomyBridge`:

```kotlin
private fun getPlayerBalance(): Int = EconomyBridge.getBalance(player.uuid)
private fun subtractBalance(amount: Int) { EconomyBridge.withdraw(player.uuid, amount) }
private fun addBalance(amount: Int) { EconomyBridge.deposit(player.uuid, amount) }
```
Delete the now-unused `getEconomyManager()`. (Note: this file is deleted entirely in Task 9 — the edit here is just to keep the project compilable through Tasks 7-8.)

- [ ] **Step 7: Run full test suite**

```bash
cd cobblemon-market && ./gradlew --no-daemon test
```
Expected: existing `PricingEngineTest` (28 tests passed) + new `EconomyBridgeTest` (4 tests passed) all green.

### Task 7: Delete the NPC shopkeeper system

**Files:**
- Delete: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/shop/ShopkeeperManager.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt` (remove `ShopkeeperManager.init(...)` call)
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt` (delete `npc create` and `npc remove` subcommands)

- [ ] **Step 1: Find all references to `ShopkeeperManager`**

```bash
grep -rn "ShopkeeperManager\|com.cobblemonmarket.shop" cobblemon-market/src/main/kotlin/
```

- [ ] **Step 2: Remove the `ShopkeeperManager.init(...)` call from `CobblemonMarket.kt`**

In the `init` block, delete the line `ShopkeeperManager.init(configDir)`.

- [ ] **Step 3: Remove `npc create` and `npc remove` subcommand registrations from `MarketCommands.kt`**

Locate the `npc` literal block (search for `literal("npc")`). Delete the entire `then(literal("npc")...)` chain from the dispatcher tree.

- [ ] **Step 4: Delete the file**

```bash
git rm cobblemon-market/src/main/kotlin/com/cobblemonmarket/shop/ShopkeeperManager.kt
```

- [ ] **Step 5: Compile to verify no dangling references**

```bash
cd cobblemon-market && ./gradlew --no-daemon compileKotlin
```
Expected: success.

### Task 8: Vanilla `MenuType` registry skeleton

**Files:**
- Create: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/MenuRegistry.kt`

- [ ] **Step 1: Create the registry file**

```kotlin
package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.core.registries.Registries
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredRegister

object MenuRegistry {

    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CobblemonMarket.MOD_ID)

    val SHOP: net.neoforged.neoforge.registries.DeferredHolder<MenuType<*>, MenuType<ShopMenu>> =
        MENUS.register("shop") { ->
            IMenuTypeExtension.create<ShopMenu> { containerId, inv, _ ->
                ShopMenu(containerId, inv)
            }
        }

    val TRANSACTION: net.neoforged.neoforge.registries.DeferredHolder<MenuType<*>, MenuType<TransactionMenu>> =
        MENUS.register("transaction") { ->
            IMenuTypeExtension.create<TransactionMenu> { containerId, inv, data ->
                val itemId = data.readUtf()
                TransactionMenu(containerId, inv, itemId)
            }
        }
}
```

The `IMenuTypeExtension.create` factory accepts a `RegistryFriendlyByteBuf` for the initial state payload — `ShopMenu` doesn't need extra state, `TransactionMenu` reads the chosen item ID. `ShopMenu` and `TransactionMenu` will be created in Tasks 9 and 10.

- [ ] **Step 2: Verify `CobblemonMarket.kt` already calls `MenuRegistry.MENUS.register(modBus)`**

The entry-point template from Task 3 includes this line in the `init` block. If you missed it, add it now.

### Task 9: `ShopMenu` — top-level item picker

**Files:**
- Delete: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/ShopGui.kt`
- Create: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/ShopMenu.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt` — replace `ShopGui.open(player)` calls with `ShopMenuProvider.open(player)`. The `/market open` command is added here (the dispatcher previously opened the GUI from the right-click NPC handler; we now expose it as a command since NPCs are gone).

The shop menu shows configured items in a 6-row chest layout, each slot displaying an item with its current buy/sell prices written into the stack's lore. Clicking a slot opens the corresponding `TransactionMenu` for that item.

- [ ] **Step 1: Create `ShopMenu.kt`**

```kotlin
package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.pricing.PricingEngine
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries

class ShopMenu(
    containerId: Int,
    private val playerInventory: Inventory,
) : AbstractContainerMenu(MenuRegistry.SHOP.get(), containerId) {

    private val display = SimpleContainer(SLOT_COUNT)

    init {
        // 6 rows × 9 cols of read-only display slots, no player-inventory slots (read-only menu)
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            addSlot(DisplaySlot(display, row * COLS + col, 8 + col * 18, 18 + row * 18))
        }
        repaint()
    }

    private fun repaint() {
        val items = CobblemonMarket.items.entries.toList()
        for (i in 0 until SLOT_COUNT) {
            display.setItem(i, if (i < items.size) buildSlot(items[i].key) else ItemStack.EMPTY)
        }
        broadcastChanges()
    }

    private fun buildSlot(itemId: String): ItemStack {
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val stack = ItemStack(item)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val entry = CobblemonMarket.items[itemId] ?: return stack
        val sellPrice = PricingEngine.sellPrice(entry.baseSellPrice, state.priceFactor)
        val spread = PricingEngine.dynamicSpread(state, CobblemonMarket.config)
        val buyPrice = PricingEngine.buyPrice(entry.baseSellPrice, state.priceFactor, spread)
        val lore = listOf(
            Component.literal("§7Buy: §a$$buyPrice"),
            Component.literal("§7Sell: §c$$sellPrice"),
            Component.literal(""),
            Component.literal("§eClick to trade"),
        )
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        if (slotId !in 0 until SLOT_COUNT) return
        val itemId = CobblemonMarket.items.keys.toList().getOrNull(slotId) ?: return
        val sp = player as? ServerPlayer ?: return
        sp.closeContainer()
        TransactionMenuProvider.open(sp, itemId)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    /** Read-only slot — never accepts placed/picked-up stacks. */
    private class DisplaySlot(container: SimpleContainer, slot: Int, x: Int, y: Int) :
        Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
        override fun mayPickup(player: Player): Boolean = false
    }

    companion object {
        const val ROWS = 6
        const val COLS = 9
        const val SLOT_COUNT = ROWS * COLS
    }
}

object ShopMenuProvider : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Cobblemon Market")
    override fun createMenu(containerId: Int, inv: Inventory, player: Player): AbstractContainerMenu =
        ShopMenu(containerId, inv)

    fun open(player: ServerPlayer) {
        player.openMenu(this)
    }
}
```

- [ ] **Step 2: Wire up `/market open` in `MarketCommands.kt`**

Add a top-level `open` literal to the dispatcher tree, requiring no permissions:
```kotlin
.then(Commands.literal("open")
    .executes { ctx ->
        val sp = ctx.source.playerOrException
        ShopMenuProvider.open(sp)
        1
    })
```

- [ ] **Step 3: Delete the old `ShopGui.kt`**

```bash
git rm cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/ShopGui.kt
```

- [ ] **Step 4: Compile to verify**

```bash
cd cobblemon-market && ./gradlew --no-daemon compileKotlin
```
Expected: success. (Note: `TransactionMenuProvider` is referenced but not yet created — compile may fail. If so, stub `TransactionMenuProvider.open(player, itemId)` as a `TODO()` until Task 10 — or proceed straight to Task 10 in the same commit and do steps 1-4 of both tasks before compiling.)

### Task 10: `TransactionMenu` — quantity picker for one item

**Files:**
- Delete: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt`
- Create: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionMenu.kt`

The transaction menu shows: top row = the selected item × current quantity preview, bottom row = control buttons (`-1`, `-5`, `-10`, `+1`, `+5`, `+10`, `BUY`, `SELL`, `BACK`). The pricing breakdown text appears in the lore of the preview item, regenerated server-side after every click.

The full code is ~250 LoC (mirrors the Fabric `TransactionGui` server-side logic, but built on `AbstractContainerMenu` instead of sgui).

- [ ] **Step 1: Create `TransactionMenu.kt`**

```kotlin
package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.economy.EconomyBridge
import com.cobblemonmarket.pricing.PricingEngine
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DataComponents
import net.minecraft.world.item.component.ItemLore

class TransactionMenu(
    containerId: Int,
    private val playerInventory: Inventory,
    private val itemId: String,
) : AbstractContainerMenu(MenuRegistry.TRANSACTION.get(), containerId) {

    private val display = SimpleContainer(27)
    private var quantity: Int = 1

    init {
        for (row in 0 until 3) for (col in 0 until 9) {
            addSlot(DisplaySlot(display, row * 9 + col, 8 + col * 18, 18 + row * 18))
        }
        repaint()
    }

    private fun repaint() {
        for (i in 0 until display.containerSize) display.setItem(i, ItemStack.EMPTY)

        // Slot 4 (top middle): the item with current price preview
        display.setItem(SLOT_PREVIEW, buildPreview())

        // Slot 9-14: quantity buttons (-10, -5, -1, +1, +5, +10)
        display.setItem(SLOT_MINUS_10, button(Items.RED_STAINED_GLASS_PANE, "§c-10"))
        display.setItem(SLOT_MINUS_5,  button(Items.RED_STAINED_GLASS_PANE, "§c-5"))
        display.setItem(SLOT_MINUS_1,  button(Items.RED_STAINED_GLASS_PANE, "§c-1"))
        display.setItem(SLOT_PLUS_1,   button(Items.LIME_STAINED_GLASS_PANE, "§a+1"))
        display.setItem(SLOT_PLUS_5,   button(Items.LIME_STAINED_GLASS_PANE, "§a+5"))
        display.setItem(SLOT_PLUS_10,  button(Items.LIME_STAINED_GLASS_PANE, "§a+10"))

        // Slot 18-26: BUY / SELL / BACK
        display.setItem(SLOT_BUY,  button(Items.EMERALD, "§aBUY $quantity"))
        display.setItem(SLOT_SELL, button(Items.GOLD_INGOT, "§eSELL $quantity"))
        display.setItem(SLOT_BACK, button(Items.BARRIER, "§7Back"))

        broadcastChanges()
    }

    private fun buildPreview(): ItemStack {
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val stack = ItemStack(item, quantity.coerceAtMost(64))
        val entry = CobblemonMarket.items[itemId] ?: return stack
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val spread = PricingEngine.dynamicSpread(state, CobblemonMarket.config)
        val (totalBuy, perUnitBuy)  = PricingEngine.simulateBatchBuy(entry.baseSellPrice, state.priceFactor, spread, quantity, CobblemonMarket.config)
        val (totalSell, perUnitSell) = PricingEngine.simulateBatchSell(entry.baseSellPrice, state.priceFactor, quantity, CobblemonMarket.config)
        val lore = mutableListOf<Component>(
            Component.literal("§7Quantity: §f$quantity"),
            Component.literal("§aBuy total: §f$$totalBuy"),
            Component.literal("§eSell total: §f$$totalSell"),
            Component.literal(""),
        )
        // Show first 5 per-unit prices to mirror the original GUI
        if (perUnitBuy.isNotEmpty()) {
            lore += Component.literal("§7Buy per-unit: §f" + perUnitBuy.take(5).joinToString(", ") { "$$it" } + if (perUnitBuy.size > 5) " ..." else "")
        }
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    private fun button(item: net.minecraft.world.item.Item, name: String): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        return stack
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val sp = player as? ServerPlayer ?: return
        when (slotId) {
            SLOT_MINUS_10 -> { quantity = (quantity - 10).coerceAtLeast(1); repaint() }
            SLOT_MINUS_5  -> { quantity = (quantity - 5).coerceAtLeast(1);  repaint() }
            SLOT_MINUS_1  -> { quantity = (quantity - 1).coerceAtLeast(1);  repaint() }
            SLOT_PLUS_1   -> { quantity = (quantity + 1).coerceAtMost(MAX_QTY); repaint() }
            SLOT_PLUS_5   -> { quantity = (quantity + 5).coerceAtMost(MAX_QTY); repaint() }
            SLOT_PLUS_10  -> { quantity = (quantity + 10).coerceAtMost(MAX_QTY); repaint() }
            SLOT_BUY  -> performBuy(sp)
            SLOT_SELL -> performSell(sp)
            SLOT_BACK -> { sp.closeContainer(); ShopMenuProvider.open(sp) }
        }
    }

    private fun performBuy(player: ServerPlayer) {
        val entry = CobblemonMarket.items[itemId] ?: return
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val spread = PricingEngine.dynamicSpread(state, CobblemonMarket.config)
        val (totalCost, _) = PricingEngine.simulateBatchBuy(entry.baseSellPrice, state.priceFactor, spread, quantity, CobblemonMarket.config)
        val balance = EconomyBridge.getBalance(player.uuid)
        if (balance < totalCost) {
            player.sendSystemMessage(Component.literal("§cInsufficient balance: have $$balance, need $$totalCost"))
            return
        }
        if (!hasInventorySpace(player, quantity)) {
            player.sendSystemMessage(Component.literal("§cNot enough inventory space"))
            return
        }
        if (!EconomyBridge.withdraw(player.uuid, totalCost)) {
            player.sendSystemMessage(Component.literal("§cTransaction failed"))
            return
        }
        // Apply per-unit factor changes and grant items
        repeat(quantity) {
            state.priceFactor = PricingEngine.applyBuyTick(state.priceFactor, CobblemonMarket.config.buyGrowth, CobblemonMarket.config.factorCeiling)
            CobblemonMarket.marketStore.recordTransaction(itemId, "buy")
        }
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        player.inventory.add(ItemStack(item, quantity))
        CobblemonMarket.marketStore.save()
        player.sendSystemMessage(Component.literal("§aBought $quantity for $$totalCost"))
        repaint()
    }

    private fun performSell(player: ServerPlayer) {
        val entry = CobblemonMarket.items[itemId] ?: return
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val (totalProceeds, _) = PricingEngine.simulateBatchSell(entry.baseSellPrice, state.priceFactor, quantity, CobblemonMarket.config)
        val itemRef = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val have = countItems(player, itemRef)
        if (have < quantity) {
            player.sendSystemMessage(Component.literal("§cYou only have $have ${itemId.substringAfterLast(':')}"))
            return
        }
        removeItems(player, itemRef, quantity)
        repeat(quantity) {
            state.priceFactor = PricingEngine.applySellTick(state.priceFactor, CobblemonMarket.config.sellDecay, CobblemonMarket.config.factorFloor)
            CobblemonMarket.marketStore.recordTransaction(itemId, "sell")
        }
        EconomyBridge.deposit(player.uuid, totalProceeds)
        CobblemonMarket.marketStore.save()
        player.sendSystemMessage(Component.literal("§aSold $quantity for $$totalProceeds"))
        repaint()
    }

    // --- inventory helpers (carried over from old TransactionGui) ---
    private fun countItems(player: Player, item: net.minecraft.world.item.Item): Int =
        player.inventory.items.sumOf { if (it.item == item) it.count else 0 }

    private fun removeItems(player: Player, item: net.minecraft.world.item.Item, count: Int) {
        var remaining = count
        for (stack in player.inventory.items) {
            if (remaining <= 0) break
            if (stack.item == item) { val take = minOf(remaining, stack.count); stack.shrink(take); remaining -= take }
        }
    }

    private fun hasInventorySpace(player: Player, count: Int): Boolean {
        var space = 0
        for (s in player.inventory.items) if (s.isEmpty) space += 64
        return space >= count
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private class DisplaySlot(c: SimpleContainer, slot: Int, x: Int, y: Int) : Slot(c, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = false
        override fun mayPickup(player: Player) = false
    }

    companion object {
        const val MAX_QTY = 256
        // 3-row chest layout slot indices
        const val SLOT_PREVIEW = 4
        const val SLOT_MINUS_10 = 9
        const val SLOT_MINUS_5  = 10
        const val SLOT_MINUS_1  = 11
        const val SLOT_PLUS_1   = 12
        const val SLOT_PLUS_5   = 13
        const val SLOT_PLUS_10  = 14
        const val SLOT_BUY  = 18
        const val SLOT_SELL = 22
        const val SLOT_BACK = 26
    }
}

class TransactionMenuFactory(private val itemId: String) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Trade")
    override fun createMenu(containerId: Int, inv: Inventory, player: Player): AbstractContainerMenu =
        TransactionMenu(containerId, inv, itemId)
}

object TransactionMenuProvider {
    fun open(player: ServerPlayer, itemId: String) {
        // Pass itemId through the MenuType payload writer
        player.openMenu(TransactionMenuFactory(itemId)) { buf -> buf.writeUtf(itemId) }
    }
}
```

- [ ] **Step 2: Verify `PricingEngine` exposes the helpers used above**

Open `cobblemon-market/src/main/kotlin/com/cobblemonmarket/pricing/PricingEngine.kt` and confirm these methods exist (they were in the original Fabric `TransactionGui`'s logic, likely already migrated). If any are missing, add them based on the existing per-unit math in the file:

```kotlin
fun simulateBatchBuy(basePrice: Int, factor: Double, spread: Double, qty: Int, cfg: MarketConfig): Pair<Int, List<Int>>
fun simulateBatchSell(basePrice: Int, factor: Double, qty: Int, cfg: MarketConfig): Pair<Int, List<Int>>
fun applyBuyTick(factor: Double, growth: Double, ceiling: Double): Double
fun applySellTick(factor: Double, decay: Double, floor: Double): Double
fun sellPrice(basePrice: Int, factor: Double): Int
fun buyPrice(basePrice: Int, factor: Double, spread: Double): Int
fun dynamicSpread(state: ItemState, cfg: MarketConfig): Double
```
If you add any new helpers, add a unit test for each in `PricingEngineTest.kt` covering at least one happy-path case and one boundary case.

- [ ] **Step 3: Delete the old `TransactionGui.kt`**

```bash
git rm cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt
```

- [ ] **Step 4: Confirm `MarketStore` exposes `recordTransaction(itemId, type)`**

```bash
grep -n "fun recordTransaction" cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/MarketStore.kt
```
If absent, add a method that appends `{type, timestamp: now}` to `state.transactions` (bounded by `cfg.transactionWindowSize`).

- [ ] **Step 5: Compile**

```bash
cd cobblemon-market && ./gradlew --no-daemon compileKotlin
```
Expected: success.

### Task 11: Build and unit-test the market mod

- [ ] **Step 1: Run the full build**

```bash
cd cobblemon-market && ./gradlew --no-daemon build
```
Expected: `BUILD SUCCESSFUL`. Output jar at `cobblemon-market/build/libs/cobblemon-market-1.0.0.jar`.

- [ ] **Step 2: Run only tests, double-check**

```bash
./gradlew --no-daemon test
```
Expected: all tests passed (28 in `PricingEngineTest`, 4 in `EconomyBridgeTest`, plus any added in Task 10).

- [ ] **Step 3: Commit Phase 1**

```bash
cd /Users/almutwakel/Documents/Projects/minecraft
git add cobblemon-market/
git commit -m "$(cat <<'EOF'
feat(market): port to NeoForge 1.21.1

Replace Fabric loader integration with native NeoForge entry point,
KotlinForForge mod loader, and ModDevGradle build. Rewrite ShopGui and
TransactionGui as vanilla AbstractContainerMenu (sgui-free). Extract
Cobblemon Economy reflection to a dedicated EconomyBridge with cached
Method handles and graceful no-op fallback when CE is absent. Drop NPC
shopkeeper system; access via /market open. Persistence directories and
JSON formats unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2 — `cobblemon-ranked` port

Same overall shape as Phase 1 minus the economy bridge.

### Task 12: Replace `cobblemon-ranked` build files

**Files:**
- Modify: `cobblemon-ranked/build.gradle.kts`
- Modify: `cobblemon-ranked/settings.gradle.kts`
- Modify: `cobblemon-ranked/gradle.properties`

- [ ] **Step 1: Replace `cobblemon-ranked/build.gradle.kts`**

Identical content to `cobblemon-market/build.gradle.kts` from Task 1, **except** the `mods` block uses `register("cobblemon_ranked")`.

- [ ] **Step 2: Replace `cobblemon-ranked/settings.gradle.kts`**

```kotlin
rootProject.name = "cobblemon-ranked"

pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}
```

- [ ] **Step 3: Replace `cobblemon-ranked/gradle.properties`**

```
org.gradle.jvmargs=-Xmx4G
minecraft_version=1.21.1
neoforge_version=21.1.227
kotlin_for_forge_version=5.11.0
cobblemon_version=1.7.3+1.21.1
mod_version=1.0.0
maven_group=com.cobblemonranked
```

- [ ] **Step 4: Verify Gradle resolves**

```bash
cd cobblemon-ranked && ./gradlew --no-daemon help -q
```
Expected: success.

### Task 13: Replace `fabric.mod.json` with `neoforge.mods.toml` for ranked

**Files:**
- Delete: `cobblemon-ranked/src/main/resources/fabric.mod.json`
- Create: `cobblemon-ranked/src/main/resources/META-INF/neoforge.mods.toml`

- [ ] **Step 1: Create the manifest**

```toml
modLoader = "kotlinforforge"
loaderVersion = "[5,)"
license = "All Rights Reserved"

[[mods]]
modId = "cobblemon_ranked"
version = "${version}"
displayName = "Cobblemon Ranked"
description = "ELO-rated ranked PvP battles for Cobblemon"
authors = "almutwakel"

[[dependencies.cobblemon_ranked]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_ranked]]
modId = "kotlinforforge"
type = "required"
versionRange = "[5,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_ranked]]
modId = "cobblemon"
type = "required"
versionRange = "[1.7.1,)"
ordering = "AFTER"
side = "BOTH"
```

- [ ] **Step 2: Delete the Fabric manifest**

```bash
git rm cobblemon-ranked/src/main/resources/fabric.mod.json
```

### Task 14: Rewrite `CobblemonRanked.kt` entry point

**Files:**
- Modify: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt`

- [ ] **Step 1: Replace contents**

Use the same `@Mod`-class pattern as Task 3. Concrete file:

```kotlin
package com.cobblemonranked

import com.cobblemonranked.commands.RankedCommands
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.data.EloStore
import com.cobblemonranked.data.TeamStore
import com.cobblemonranked.decay.DecayManager
import com.cobblemonranked.gui.MenuRegistry
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonRanked.MOD_ID)
class CobblemonRanked(modBus: IEventBus, container: ModContainer) {
    init {
        logger.info("Cobblemon Ranked initializing...")
        val configDir = FMLPaths.CONFIGDIR.get()
        config = RankedConfig.load(configDir)
        eloStore = EloStore(configDir)
        eloStore.load()
        teamStore = TeamStore(configDir)
        teamStore.load()
        DecayManager.init()

        MenuRegistry.MENUS.register(modBus)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)
        logger.info("Cobblemon Ranked initialized.")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        RankedCommands.register(event.dispatcher)
    }

    private var tickCounter = 0
    private fun onServerTickPost(event: ServerTickEvent.Post) {
        tickCounter++
        if (tickCounter % 1200 == 0) DecayManager.tick()
    }

    companion object {
        const val MOD_ID = "cobblemon_ranked"
        const val PERSISTENCE_DIR_NAME = "cobblemon-ranked"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: RankedConfig
        lateinit var eloStore: EloStore
        lateinit var teamStore: TeamStore
    }
}
```

- [ ] **Step 2: Apply the same `PERSISTENCE_DIR_NAME` discipline as Task 5**

```bash
grep -nE 'cobblemon-ranked|MOD_ID' cobblemon-ranked/src/main/kotlin/com/cobblemonranked/config/*.kt cobblemon-ranked/src/main/kotlin/com/cobblemonranked/data/*.kt
```
Replace any `MOD_ID`-derived directory names with `CobblemonRanked.PERSISTENCE_DIR_NAME`.

### Task 15: `MenuRegistry` and `TeamSelectionMenu`

**Files:**
- Delete: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/gui/TeamSelectionGui.kt`
- Create: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/gui/MenuRegistry.kt`
- Create: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/gui/TeamSelectionMenu.kt`

- [ ] **Step 1: Create `MenuRegistry.kt`**

```kotlin
package com.cobblemonranked.gui

import com.cobblemonranked.CobblemonRanked
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object MenuRegistry {
    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CobblemonRanked.MOD_ID)

    val TEAM_SELECTION: DeferredHolder<MenuType<*>, MenuType<TeamSelectionMenu>> =
        MENUS.register("team_selection") { ->
            IMenuTypeExtension.create<TeamSelectionMenu> { containerId, inv, _ ->
                TeamSelectionMenu(containerId, inv)
            }
        }
}
```

- [ ] **Step 2: Create `TeamSelectionMenu.kt`**

The original `TeamSelectionGui.kt` (174 LoC) is a 6-row chest where players pick up to 6 Pokemon from their party/PC. Apply the same vanilla-menu pattern as `ShopMenu`:
- 6 display slots representing the player's party (or PC top row)
- Click a slot → toggle inclusion in the selected team
- A "CONFIRM" slot at slot 53 → records the selection in `TeamStore` and closes the menu
- The selection state is in-memory on the menu; persisted via `RankedRanked.teamStore.set(playerId, list)` on confirm

The full code follows the same shape as `ShopMenu.kt`. Key differences:
- Slots show `PokemonItem` representations (use `Pokemon.getDisplayName()` and a placeholder icon — Cobblemon ships an item form for `Pokemon`, find it via `com.cobblemon.mod.common.item.PokemonItem` if available; otherwise use `Items.PAPER` with a custom name)
- `clicked(...)` toggles a `BooleanArray(6)` selection mask and calls `repaint()`
- Selection limit enforced server-side; over-cap clicks beep (`player.sendSystemMessage` warning)

Concrete skeleton (approx 220 LoC):

```kotlin
package com.cobblemonranked.gui

import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DataComponents

class TeamSelectionMenu(
    containerId: Int,
    private val playerInventory: Inventory,
) : AbstractContainerMenu(MenuRegistry.TEAM_SELECTION.get(), containerId) {

    private val display = SimpleContainer(54)
    private val selected = BooleanArray(6)
    private var party: List<Pokemon> = emptyList()

    init {
        for (row in 0 until 6) for (col in 0 until 9) {
            addSlot(DisplaySlot(display, row * 9 + col, 8 + col * 18, 18 + row * 18))
        }
        repaint()
    }

    fun bindParty(party: PartyStore) {
        this.party = party.toList().filterNotNull()
        repaint()
    }

    private fun repaint() {
        for (i in 0 until display.containerSize) display.setItem(i, ItemStack.EMPTY)
        // Top row = party, slot N = pokemon N
        party.forEachIndexed { idx, pkm ->
            display.setItem(idx, pokemonStack(pkm, selected[idx]))
        }
        display.setItem(SLOT_CONFIRM, button(Items.EMERALD_BLOCK, "§aCONFIRM"))
        display.setItem(SLOT_CANCEL, button(Items.BARRIER, "§cCANCEL"))
        broadcastChanges()
    }

    private fun pokemonStack(pkm: Pokemon, picked: Boolean): ItemStack {
        val stack = ItemStack(if (picked) Items.LIME_DYE else Items.GRAY_DYE)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal((if (picked) "§a✓ " else "§7☐ ") + pkm.species.name + " Lv." + pkm.level))
        return stack
    }

    private fun button(item: net.minecraft.world.item.Item, name: String): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        return stack
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val sp = player as? ServerPlayer ?: return
        when {
            slotId in 0 until 6 -> {
                if (slotId < party.size) {
                    val countSelected = selected.count { it }
                    if (selected[slotId]) selected[slotId] = false
                    else if (countSelected < 6) selected[slotId] = true
                    repaint()
                }
            }
            slotId == SLOT_CONFIRM -> {
                val team = party.filterIndexed { i, _ -> selected[i] }.map { it.uuid }
                if (team.isEmpty()) {
                    sp.sendSystemMessage(Component.literal("§cSelect at least one Pokemon"))
                    return
                }
                CobblemonRanked.teamStore.set(sp.uuid, team)
                sp.sendSystemMessage(Component.literal("§aTeam confirmed: ${team.size} Pokemon"))
                sp.closeContainer()
            }
            slotId == SLOT_CANCEL -> sp.closeContainer()
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private class DisplaySlot(c: SimpleContainer, slot: Int, x: Int, y: Int) : Slot(c, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = false
        override fun mayPickup(player: Player) = false
    }

    companion object {
        const val SLOT_CONFIRM = 49
        const val SLOT_CANCEL = 53
    }
}

object TeamSelectionMenuProvider : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Select your team")
    override fun createMenu(containerId: Int, inv: Inventory, player: Player): AbstractContainerMenu {
        val menu = TeamSelectionMenu(containerId, inv)
        // Bind party when constructing for a server player
        (player as? ServerPlayer)?.let {
            val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(it.uuid)
            menu.bindParty(party)
        }
        return menu
    }

    fun open(player: ServerPlayer) { player.openMenu(this) }
}
```

If the original `TeamSelectionGui.kt` had specific behaviors not covered above (e.g. PC tab toggling, Pokemon detail tooltips), port them by mirroring the per-slot click handlers. Reference the deleted `TeamSelectionGui.kt` git history (`git show fabric-baseline-pre-port:cobblemon-ranked/src/main/kotlin/com/cobblemonranked/gui/TeamSelectionGui.kt`) for the exact semantics.

- [ ] **Step 3: Update `RankedBattle.kt` and `ChallengeManager.kt` callers**

Search for `TeamSelectionGui` references and replace with `TeamSelectionMenuProvider.open(player)`:
```bash
grep -rn "TeamSelectionGui" cobblemon-ranked/src/main/kotlin/
```

- [ ] **Step 4: Delete the old GUI file**

```bash
git rm cobblemon-ranked/src/main/kotlin/com/cobblemonranked/gui/TeamSelectionGui.kt
```

### Task 16: Build and unit-test the ranked mod

- [ ] **Step 1: Compile**

```bash
cd cobblemon-ranked && ./gradlew --no-daemon compileKotlin
```
Expected: success.

- [ ] **Step 2: Run the build**

```bash
./gradlew --no-daemon build
```
Expected: `BUILD SUCCESSFUL`. Output jar at `cobblemon-ranked/build/libs/cobblemon-ranked-1.0.0.jar`. `EloCalculatorTest` passes (its loader-agnostic).

- [ ] **Step 3: Commit Phase 2**

```bash
cd /Users/almutwakel/Documents/Projects/minecraft
git add cobblemon-ranked/
git commit -m "$(cat <<'EOF'
feat(ranked): port to NeoForge 1.21.1

Replace Fabric loader integration with native NeoForge entry point
and ModDevGradle build. Rewrite TeamSelectionGui as a vanilla
AbstractContainerMenu (sgui-free). Persistence directories and JSON
formats unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3 — Server integration

### Task 17: Drop new jars into `cobblemon-server/mods/`

**Files:**
- Copy: `cobblemon-market/build/libs/cobblemon-market-1.0.0.jar` → `cobblemon-server/mods/cobblemon-market-1.0.0.jar`
- Copy: `cobblemon-ranked/build/libs/cobblemon-ranked-1.0.0.jar` → `cobblemon-server/mods/cobblemon-ranked-1.0.0.jar`
- Optional remove: `cobblemon-server/mods/cobblemon_ranked-neoforge-1.4.2.jar` (the upstream/community ranked mod from the mrpack — our mod replaces it; if both are present mod-id collision will block startup)

- [ ] **Step 1: Inspect for ID collisions before copying**

```bash
ls cobblemon-server/mods/ | grep -iE "cobblemon[_-]ranked|cobblemon[_-]market"
```
Expected before: `cobblemon_ranked-neoforge-1.4.2.jar` is present (from the mrpack), no market jar.

- [ ] **Step 2: Move the upstream ranked jar aside**

```bash
mkdir -p cobblemon-server/mods-disabled
mv cobblemon-server/mods/cobblemon_ranked-neoforge-1.4.2.jar cobblemon-server/mods-disabled/
```

- [ ] **Step 3: Copy our jars**

```bash
cp cobblemon-market/build/libs/cobblemon-market-1.0.0.jar cobblemon-server/mods/
cp cobblemon-ranked/build/libs/cobblemon-ranked-1.0.0.jar  cobblemon-server/mods/
```

- [ ] **Step 4: Have the user restart the server in their terminal**

Print:
```
The new mods are in cobblemon-server/mods/. In your server console:
  stop
  ./run.sh
Watch the log for "cobblemon_market" and "cobblemon_ranked" in the
"Loading N mods" list. Report any errors.
```

If running headless, restart it ourselves instead:
```bash
PID=$(lsof -nP -iTCP:25565 -sTCP:LISTEN -t 2>/dev/null)
[ -n "$PID" ] && kill -TERM "$PID"
( cd cobblemon-server && ./run.sh > startup.log 2>&1 & )
```

- [ ] **Step 5: Verify both mods loaded**

```bash
grep -aE 'cobblemon_market|cobblemon_ranked' cobblemon-server/startup.log | sed 's/\x1b\[[0-9;]*m//g' | head -10
grep -aE 'Done \(' cobblemon-server/startup.log | sed 's/\x1b\[[0-9;]*m//g'
```
Expected: both mod IDs appear in the "Loading N mods" list, and "Done (Xs)" prints.

### Task 18: Smoke test commands

- [ ] **Step 1: From the server console, run sequentially**

```
op sixthsense
help market
market open
market prices
help ranked
ranked stats sixthsense
```
Expected: each command either prints output or no error; `/market open` opens the chest UI for an in-game player.

- [ ] **Step 2: If any command errors, capture the stack trace**

```bash
tail -200 cobblemon-server/logs/latest.log
```
Diagnose; common issues:
- `EconomyBridge` returns 0 for everyone → CE not loaded; check `cobblemon-economy-0.0.17.jar` is in `mods/`
- `MenuType not registered` → `MenuRegistry.MENUS.register(modBus)` was missed in the entry point
- `ClassNotFoundException: com.cobblemon.economy.fabric.CobblemonEconomy` only when `/market buy` runs → CE loaded but the FQN drifted; `javap` the current CE jar to confirm

### Task 19: Update `cobblemon-server-changes.md`

**Files:**
- Modify: `cobblemon-server-changes.md`

- [ ] **Step 1: Append a section**

```markdown
### 2026-05-09 — Replaced upstream cobblemon_ranked with our build; added cobblemon_market
- **Disabled:** `cobblemon_ranked-neoforge-1.4.2.jar` (moved to `mods-disabled/`). Replaced by our in-house `cobblemon-ranked-1.0.0.jar` to avoid mod-id collision.
- **Added:** `cobblemon-market-1.0.0.jar` (new in-house dynamic-pricing market mod).
- **Added:** `cobblemon-ranked-1.0.0.jar` (in-house ranked mod, NeoForge port).
- **Reason:** Server-side rollout of the locally-developed mods (ported from Fabric to NeoForge — see `docs/superpowers/specs/2026-05-09-neoforge-port-design.md`).
- **Caveat:** `/market` features depend on Cobblemon Economy 0.0.17 being loaded (already in `mods/` as of 2026-05-08).
```

- [ ] **Step 2: Commit**

```bash
git add cobblemon-server-changes.md
git commit -m "$(cat <<'EOF'
docs: record cobblemon-market and cobblemon-ranked deployment

Disable upstream cobblemon_ranked in favour of in-house build; add
in-house cobblemon-market.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Done

After Task 19:
- Both mods are pure NeoForge 1.21.1 jars built with ModDevGradle.
- All chest UIs use vanilla `MenuType`.
- Cobblemon Economy reflection lives in one file (`EconomyBridge.kt`) covered by 4 unit tests.
- The 28 + 4 + N pricing tests and 30+ ELO tests are all green.
- Server `mods/` directory contains both jars and they appear in the loaded-mods list.
- `cobblemon-server-changes.md` is current.

Future follow-ons (out of scope for this plan):
- Reintroduce the NPC shopkeeper (custom entity registration on NeoForge).
- Migrate from Cobblemon Economy to CobbleDollars + Impactor when the Bridge ships a NeoForge build.
- Reintroduce a Fabric build via Architectury common-module if cross-loader becomes a goal.
