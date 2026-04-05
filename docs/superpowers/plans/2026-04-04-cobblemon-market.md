# Cobblemon Market Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Fabric mod that adds a dynamic-pricing shopkeeper NPC to Cobblemon, where prices adjust based on supply and demand via a configurable pricing engine.

**Architecture:** Kotlin Fabric mod depending on Cobblemon 1.7.3 and Cobblemon Economy 0.0.17. Pricing math is unit-tested. NPC uses a vanilla Villager entity with intercepted interactions. GUI built with sgui. State persisted as JSON files.

**Tech Stack:** Kotlin, Fabric 1.21.1, Cobblemon API, Cobblemon Economy API, sgui, Gson, JUnit 5

---

## File Structure

```
cobblemon-market/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  src/main/kotlin/com/cobblemonmarket/
    CobblemonMarket.kt              # ModInitializer entry point
    config/MarketConfig.kt           # Global spread/recovery config
    config/ItemConfig.kt             # Per-item base sell price config
    data/ItemState.kt                # Per-item runtime state (factor, transactions)
    data/MarketStore.kt              # JSON persistence for all market state
    pricing/PricingEngine.kt         # Pure pricing math (no side effects)
    shop/ShopkeeperManager.kt       # NPC spawn/remove, interaction interception
    gui/ShopGui.kt                   # Main shop GUI (item list with prices)
    gui/TransactionGui.kt            # Quantity selection + price preview + confirm
    commands/MarketCommands.kt       # All /market commands
  src/main/resources/
    fabric.mod.json
  src/test/kotlin/com/cobblemonmarket/
    pricing/PricingEngineTest.kt     # Unit tests for pricing math
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `cobblemon-market/settings.gradle.kts`
- Create: `cobblemon-market/gradle.properties`
- Create: `cobblemon-market/build.gradle.kts`
- Create: `cobblemon-market/src/main/resources/fabric.mod.json`
- Create: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
// cobblemon-market/settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "cobblemon-market"
```

- [ ] **Step 2: Create gradle.properties**

```properties
# cobblemon-market/gradle.properties
org.gradle.jvmargs=-Xmx2G
minecraft_version=1.21.1
loader_version=0.16.5
fabric_version=0.103.0+1.21.1
mod_version=1.0.0
maven_group=com.cobblemonmarket
```

- [ ] **Step 3: Create build.gradle.kts**

```kotlin
// cobblemon-market/build.gradle.kts
plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
    kotlin("jvm") version "2.0.21"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    maven("https://api.modrinth.com/maven")
    maven("https://maven.nucleoid.xyz")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")

    modImplementation("maven.modrinth:cobblemon:1.7.3")
    modImplementation("maven.modrinth:cobblemon-economy:0.0.17")

    modImplementation(include("eu.pb4:sgui:2.0.0+26.1")!!)

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
```

- [ ] **Step 4: Create fabric.mod.json**

```json
{
    "schemaVersion": 1,
    "id": "cobblemon-market",
    "version": "${version}",
    "name": "Cobblemon Market",
    "description": "Dynamic-pricing shopkeeper for Cobblemon",
    "environment": "*",
    "entrypoints": {
        "main": [
            {
                "adapter": "kotlin",
                "value": "com.cobblemonmarket.CobblemonMarket"
            }
        ]
    },
    "depends": {
        "fabricloader": ">=0.16.5",
        "minecraft": "~1.21.1",
        "fabric-api": "*",
        "fabric-language-kotlin": ">=1.12.3+kotlin.2.0.21",
        "cobblemon": ">=1.7.1"
    },
    "suggests": {
        "cobblemon-economy": ">=0.0.16"
    }
}
```

- [ ] **Step 5: Create entry point stub**

```kotlin
// src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt
package com.cobblemonmarket

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CobblemonMarket : ModInitializer {
    const val MOD_ID = "cobblemon-market"
    val logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("Cobblemon Market initializing...")
    }
}
```

- [ ] **Step 6: Verify project compiles**

```bash
cd cobblemon-market && ./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add cobblemon-market/
git commit -m "feat(market): scaffold Fabric mod project"
```

---

### Task 2: Config System

**Files:**
- Create: `src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt`
- Create: `src/main/kotlin/com/cobblemonmarket/config/ItemConfig.kt`

- [ ] **Step 1: Create global market config**

```kotlin
// src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt
package com.cobblemonmarket.config

import com.cobblemonmarket.CobblemonMarket
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class MarketConfig(
    val spreadBase: Double = 3.0,
    val spreadExtra: Double = 4.0,
    val recoveryRatePerHour: Double = 0.01,
    val factorFloor: Double = 0.10,
    val factorCeiling: Double = 1.00,
    val sellDecay: Double = 0.98,
    val buyGrowth: Double = 1.02,
    val transactionWindowSize: Int = 50
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): MarketConfig {
            val file = configDir.resolve("cobblemon-market").resolve("config.json")
            if (!file.exists()) {
                val default = MarketConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), MarketConfig::class.java)
            } catch (e: Exception) {
                CobblemonMarket.logger.error("Failed to load market config, using defaults", e)
                MarketConfig()
            }
        }

        fun save(configDir: Path, config: MarketConfig) {
            val dir = configDir.resolve("cobblemon-market")
            dir.createDirectories()
            dir.resolve("config.json").writeText(gson.toJson(config))
        }
    }
}
```

- [ ] **Step 2: Create per-item config**

```kotlin
// src/main/kotlin/com/cobblemonmarket/config/ItemConfig.kt
package com.cobblemonmarket.config

import com.cobblemonmarket.CobblemonMarket
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class ItemEntry(
    val baseSellPrice: Int
)

object ItemConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun load(configDir: Path): Map<String, ItemEntry> {
        val file = configDir.resolve("cobblemon-market").resolve("items.json")
        if (!file.exists()) {
            val defaults = defaultItems()
            save(configDir, defaults)
            return defaults
        }
        return try {
            val type = object : TypeToken<Map<String, ItemEntry>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load items config, using defaults", e)
            defaultItems()
        }
    }

    fun save(configDir: Path, items: Map<String, ItemEntry>) {
        val dir = configDir.resolve("cobblemon-market")
        dir.createDirectories()
        dir.resolve("items.json").writeText(gson.toJson(items))
    }

    private fun defaultItems(): Map<String, ItemEntry> = mapOf(
        "cobblemon:rare_candy" to ItemEntry(baseSellPrice = 2000),
        "cobblemon:ultra_ball" to ItemEntry(baseSellPrice = 300),
        "cobblemon:great_ball" to ItemEntry(baseSellPrice = 100),
        "cobblemon:poke_ball" to ItemEntry(baseSellPrice = 30),
        "cobblemon:revive" to ItemEntry(baseSellPrice = 500)
    )
}
```

- [ ] **Step 3: Wire configs into entry point**

```kotlin
// Update CobblemonMarket.kt
package com.cobblemonmarket

import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.MarketConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonMarket : ModInitializer {
    const val MOD_ID = "cobblemon-market"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: MarketConfig
    lateinit var items: Map<String, ItemEntry>

    override fun onInitialize() {
        logger.info("Cobblemon Market initializing...")
        val configDir = FabricLoader.getInstance().configDir
        config = MarketConfig.load(configDir)
        items = ItemConfig.load(configDir)
        logger.info("Market config loaded: ${items.size} items configured")
    }
}
```

- [ ] **Step 4: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(market): add config system for market and items"
```

---

### Task 3: Market Data Model & Persistence

**Files:**
- Create: `src/main/kotlin/com/cobblemonmarket/data/ItemState.kt`
- Create: `src/main/kotlin/com/cobblemonmarket/data/MarketStore.kt`

- [ ] **Step 1: Create item state data class**

```kotlin
// src/main/kotlin/com/cobblemonmarket/data/ItemState.kt
package com.cobblemonmarket.data

data class Transaction(
    val type: String, // "buy" or "sell"
    val timestamp: Long
)

data class ItemState(
    var priceFactor: Double = 1.0,
    val transactions: MutableList<Transaction> = mutableListOf()
)
```

- [ ] **Step 2: Create MarketStore with JSON persistence**

```kotlin
// src/main/kotlin/com/cobblemonmarket/data/MarketStore.kt
package com.cobblemonmarket.data

import com.cobblemonmarket.CobblemonMarket
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MarketStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-market").resolve("state.json")
    private val states: MutableMap<String, ItemState> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, ItemState>>() {}.type
            val loaded: MutableMap<String, ItemState> = gson.fromJson(file.readText(), type)
            states.clear()
            states.putAll(loaded)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load market state", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-market").createDirectories()
        file.writeText(gson.toJson(states))
    }

    fun getOrCreate(itemId: String): ItemState {
        return states.getOrPut(itemId) { ItemState() }
    }

    fun getAll(): Map<String, ItemState> = states.toMap()

    fun setFactor(itemId: String, factor: Double) {
        getOrCreate(itemId).priceFactor = factor
        save()
    }

    fun addTransaction(itemId: String, type: String) {
        val state = getOrCreate(itemId)
        val windowSize = CobblemonMarket.config.transactionWindowSize
        state.transactions.add(Transaction(type = type, timestamp = System.currentTimeMillis()))
        // Trim to window size
        while (state.transactions.size > windowSize) {
            state.transactions.removeAt(0)
        }
        save()
    }
}
```

- [ ] **Step 3: Wire MarketStore into entry point**

Add to `CobblemonMarket.kt`:
```kotlin
lateinit var marketStore: MarketStore

// In onInitialize(), after items load:
marketStore = MarketStore(configDir)
marketStore.load()
logger.info("Market state loaded")
```

- [ ] **Step 4: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(market): add market data model and JSON persistence"
```

---

### Task 4: Pricing Engine (TDD)

**Files:**
- Create: `src/test/kotlin/com/cobblemonmarket/pricing/PricingEngineTest.kt`
- Create: `src/main/kotlin/com/cobblemonmarket/pricing/PricingEngine.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// src/test/kotlin/com/cobblemonmarket/pricing/PricingEngineTest.kt
package com.cobblemonmarket.pricing

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingEngineTest {

    // Default config values
    private val spreadBase = 3.0
    private val spreadExtra = 4.0
    private val sellDecay = 0.98
    private val buyGrowth = 1.02
    private val factorFloor = 0.10
    private val factorCeiling = 1.00

    @Test
    fun `sell price with default factor`() {
        val sellPrice = PricingEngine.sellPrice(baseSellPrice = 2000, priceFactor = 1.0)
        assertEquals(2000, sellPrice)
    }

    @Test
    fun `buy price with default factor and balanced spread`() {
        // No transactions -> skew defaults to 0.5 -> spread = 3 + 4*(0)^2 = 3
        val buyPrice = PricingEngine.buyPrice(
            baseSellPrice = 2000, priceFactor = 1.0,
            sells = 0, buys = 0,
            spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(6000, buyPrice) // 2000 * 1.0 * 3.0
    }

    @Test
    fun `spread with fully one-sided sells`() {
        // 50 sells, 0 buys -> skew = 1.0 -> spread = 3 + 4*(2*0.5)^2 = 3 + 4 = 7
        val spread = PricingEngine.calculateSpread(
            sells = 50, buys = 0, spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(7.0, spread, 0.001)
    }

    @Test
    fun `spread with balanced activity`() {
        // 25 sells, 25 buys -> skew = 0.5 -> spread = 3 + 4*(0)^2 = 3
        val spread = PricingEngine.calculateSpread(
            sells = 25, buys = 25, spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(3.0, spread, 0.001)
    }

    @Test
    fun `spread with mostly buys`() {
        // 10 sells, 40 buys -> skew = 0.2 -> spread = 3 + 4*(2*0.3)^2 = 3 + 4*0.36 = 4.44
        val spread = PricingEngine.calculateSpread(
            sells = 10, buys = 40, spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(4.44, spread, 0.001)
    }

    @Test
    fun `factor after sell decays`() {
        val newFactor = PricingEngine.updateFactorOnSell(1.0, sellDecay, factorFloor)
        assertEquals(0.98, newFactor, 0.001)
    }

    @Test
    fun `factor after buy grows`() {
        val newFactor = PricingEngine.updateFactorOnBuy(0.5, buyGrowth, factorCeiling)
        assertEquals(0.51, newFactor, 0.001)
    }

    @Test
    fun `factor floor is respected on sell`() {
        val newFactor = PricingEngine.updateFactorOnSell(0.10, sellDecay, factorFloor)
        assertEquals(0.10, newFactor, 0.001)
    }

    @Test
    fun `factor ceiling is respected on buy`() {
        val newFactor = PricingEngine.updateFactorOnBuy(0.995, buyGrowth, factorCeiling)
        assertEquals(1.00, newFactor, 0.001)
    }

    @Test
    fun `50 consecutive sells crash the price`() {
        var f = 1.0
        repeat(50) { f = PricingEngine.updateFactorOnSell(f, sellDecay, factorFloor) }
        // 0.98^50 = 0.3642
        assertTrue(abs(f - 0.98.pow(50)) < 0.001)
    }

    @Test
    fun `passive recovery moves factor toward ceiling`() {
        val newFactor = PricingEngine.applyRecovery(
            priceFactor = 0.50, recoveryRate = 0.01, factorCeiling = 1.00
        )
        // 0.50 + 0.01 * (1.0 - 0.50) = 0.50 + 0.005 = 0.505
        assertEquals(0.505, newFactor, 0.0001)
    }

    @Test
    fun `recovery is faster when factor is low`() {
        val recoverLow = PricingEngine.applyRecovery(0.10, 0.01, 1.00)
        val recoverMid = PricingEngine.applyRecovery(0.50, 0.01, 1.00)
        // Low: 0.10 + 0.01*0.90 = 0.109 (gain 0.009)
        // Mid: 0.50 + 0.01*0.50 = 0.505 (gain 0.005)
        assertTrue(recoverLow - 0.10 > recoverMid - 0.50)
    }

    @Test
    fun `batch sell simulates iterative price decay`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 2000, startFactor = 1.0, quantity = 5,
            sellDecay = sellDecay, factorFloor = factorFloor
        )
        assertEquals(5, result.perUnitPrices.size)
        // First sell at f=1.0: 2000
        assertEquals(2000, result.perUnitPrices[0])
        // Second sell at f=0.98: 1960
        assertEquals(1960, result.perUnitPrices[1])
        // Total should be sum of all
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
        // Final factor should be 0.98^5
        assertTrue(abs(result.finalFactor - 0.98.pow(5)) < 0.001)
    }

    @Test
    fun `batch buy simulates iterative price growth`() {
        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = 2000, startFactor = 1.0, quantity = 3,
            buyGrowth = buyGrowth, factorCeiling = factorCeiling,
            sells = 0, buys = 0,
            spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(3, result.perUnitPrices.size)
        // First buy at f=1.0, spread=3: 6000
        assertEquals(6000, result.perUnitPrices[0])
        // Second buy at f=1.02, spread recalculated with 1 buy: ~6120
        // (spread with 0 sells, 1 buy, total < 2 -> default skew 0.5 -> spread=3)
        // f=1.02 capped at 1.0 -> buy price = 2000 * 1.0 * 3 = 6000
        // Wait: factorCeiling is 1.0, so 1.0 * 1.02 = 1.02 -> capped to 1.0
        // So all 3 buys are at 6000 when starting at f=1.0
        assertEquals(6000, result.perUnitPrices[1])
        assertEquals(6000, result.perUnitPrices[2])
        assertEquals(18000, result.totalPrice)
    }

    @Test
    fun `batch buy with depressed factor shows increasing prices`() {
        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = 2000, startFactor = 0.50, quantity = 3,
            buyGrowth = buyGrowth, factorCeiling = factorCeiling,
            sells = 25, buys = 25,
            spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        // f=0.50, spread=3: buy = 2000*0.50*3 = 3000
        assertEquals(3000, result.perUnitPrices[0])
        // f=0.51, spread=3 (26 buys, 25 sells, skew~0.49): buy = 2000*0.51*3 = 3060
        assertEquals(3060, result.perUnitPrices[1])
        // Prices should increase
        assertTrue(result.perUnitPrices[1] > result.perUnitPrices[0])
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test
```
Expected: FAIL — `PricingEngine` class not found.

- [ ] **Step 3: Implement PricingEngine**

```kotlin
// src/main/kotlin/com/cobblemonmarket/pricing/PricingEngine.kt
package com.cobblemonmarket.pricing

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class BatchResult(
    val perUnitPrices: List<Int>,
    val totalPrice: Int,
    val finalFactor: Double
)

object PricingEngine {

    fun sellPrice(baseSellPrice: Int, priceFactor: Double): Int {
        return (baseSellPrice * priceFactor).roundToInt()
    }

    fun buyPrice(
        baseSellPrice: Int, priceFactor: Double,
        sells: Int, buys: Int,
        spreadBase: Double, spreadExtra: Double
    ): Int {
        val spread = calculateSpread(sells, buys, spreadBase, spreadExtra)
        return (baseSellPrice * priceFactor * spread).roundToInt()
    }

    fun calculateSpread(
        sells: Int, buys: Int,
        spreadBase: Double, spreadExtra: Double
    ): Double {
        val total = sells + buys
        val skew = if (total < 2) 0.5 else sells.toDouble() / total
        return spreadBase + spreadExtra * (2.0 * abs(skew - 0.5)).pow(2)
    }

    fun updateFactorOnSell(priceFactor: Double, sellDecay: Double, factorFloor: Double): Double {
        return maxOf(priceFactor * sellDecay, factorFloor)
    }

    fun updateFactorOnBuy(priceFactor: Double, buyGrowth: Double, factorCeiling: Double): Double {
        return minOf(priceFactor * buyGrowth, factorCeiling)
    }

    fun applyRecovery(priceFactor: Double, recoveryRate: Double, factorCeiling: Double): Double {
        return priceFactor + recoveryRate * (factorCeiling - priceFactor)
    }

    /**
     * Simulate selling [quantity] items, each at their own price as f decays.
     * Returns per-unit payouts and the final factor.
     */
    fun simulateBatchSell(
        baseSellPrice: Int, startFactor: Double, quantity: Int,
        sellDecay: Double, factorFloor: Double
    ): BatchResult {
        var f = startFactor
        val prices = mutableListOf<Int>()
        repeat(quantity) {
            prices.add(sellPrice(baseSellPrice, f))
            f = updateFactorOnSell(f, sellDecay, factorFloor)
        }
        return BatchResult(prices, prices.sum(), f)
    }

    /**
     * Simulate buying [quantity] items, each at their own price as f grows.
     * Spread is recalculated per unit based on updated transaction counts.
     */
    fun simulateBatchBuy(
        baseSellPrice: Int, startFactor: Double, quantity: Int,
        buyGrowth: Double, factorCeiling: Double,
        sells: Int, buys: Int,
        spreadBase: Double, spreadExtra: Double
    ): BatchResult {
        var f = startFactor
        var currentBuys = buys
        val prices = mutableListOf<Int>()
        repeat(quantity) {
            val spread = calculateSpread(sells, currentBuys, spreadBase, spreadExtra)
            prices.add((baseSellPrice * f * spread).roundToInt())
            f = updateFactorOnBuy(f, buyGrowth, factorCeiling)
            currentBuys++
        }
        return BatchResult(prices, prices.sum(), f)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test
```
Expected: All tests PASS. Adjust any rounding-sensitive assertions if off by 1.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(market): add pricing engine with unit tests"
```

---

### Task 5: Hourly Price Recovery

**Files:**
- Modify: `src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt`

- [ ] **Step 1: Add recovery tick to entry point**

```kotlin
import com.cobblemonmarket.pricing.PricingEngine
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

// In onInitialize(), add:
var recoveryTickCounter = 0
ServerTickEvents.END_SERVER_TICK.register { _ ->
    recoveryTickCounter++
    // 20 tps * 3600 seconds = 72000 ticks per hour
    if (recoveryTickCounter % 72000 == 0) {
        applyRecoveryToAll()
    }
}
```

- [ ] **Step 2: Add recovery method**

```kotlin
// Add to CobblemonMarket object:
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
```

- [ ] **Step 3: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(market): add hourly price recovery tick"
```

---

### Task 6: NPC Shopkeeper

**Files:**
- Create: `src/main/kotlin/com/cobblemonmarket/shop/ShopkeeperManager.kt`

Uses vanilla Villager entities with NoAI + Invulnerable. Interactions intercepted via Fabric's `UseEntityCallback`.

- [ ] **Step 1: Implement shopkeeper manager**

```kotlin
// src/main/kotlin/com/cobblemonmarket/shop/ShopkeeperManager.kt
package com.cobblemonmarket.shop

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.gui.ShopGui
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerProfession
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class ShopkeeperData(
    val uuid: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val world: String
)

object ShopkeeperManager {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val shopkeeperUuids: MutableSet<UUID> = mutableSetOf()
    private val shopkeepers: MutableList<ShopkeeperData> = mutableListOf()
    private lateinit var configDir: Path

    fun init(configDir: Path) {
        this.configDir = configDir
        loadShopkeepers()
        registerInteractionHandler()
    }

    fun spawnShopkeeper(player: ServerPlayer, name: String): Boolean {
        val level = player.serverLevel()
        val villager = Villager(EntityType.VILLAGER, level)
        villager.setPos(player.x, player.y, player.z)
        villager.customName = Component.literal(name)
        villager.isCustomNameVisible = true
        villager.isNoAi = true
        villager.isInvulnerable = true
        villager.isSilent = true
        villager.villagerData = villager.villagerData
            .setProfession(VillagerProfession.LIBRARIAN)

        if (!level.addFreshEntity(villager)) {
            return false
        }

        val data = ShopkeeperData(
            uuid = villager.uuid.toString(),
            name = name,
            x = player.x, y = player.y, z = player.z,
            world = level.dimension().location().toString()
        )
        shopkeepers.add(data)
        shopkeeperUuids.add(villager.uuid)
        saveShopkeepers()
        return true
    }

    fun removeNearest(player: ServerPlayer, radius: Double = 5.0): Boolean {
        val level = player.serverLevel()
        val nearby = level.getEntitiesOfClass(
            Villager::class.java,
            player.boundingBox.inflate(radius)
        ) { shopkeeperUuids.contains(it.uuid) }

        val nearest = nearby.minByOrNull { it.distanceTo(player) } ?: return false
        shopkeeperUuids.remove(nearest.uuid)
        shopkeepers.removeAll { it.uuid == nearest.uuid.toString() }
        nearest.discard()
        saveShopkeepers()
        return true
    }

    private fun registerInteractionHandler() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClientSide) return@register InteractionResult.PASS
            if (entity !is Villager) return@register InteractionResult.PASS
            if (!shopkeeperUuids.contains(entity.uuid)) return@register InteractionResult.PASS

            val serverPlayer = player as? ServerPlayer ?: return@register InteractionResult.PASS
            // Open shop GUI
            ShopGui(serverPlayer).open()
            InteractionResult.SUCCESS
        }
    }

    private fun loadShopkeepers() {
        val file = configDir.resolve("cobblemon-market").resolve("shopkeepers.json")
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableList<ShopkeeperData>>() {}.type
            val loaded: MutableList<ShopkeeperData> = gson.fromJson(file.readText(), type)
            shopkeepers.clear()
            shopkeepers.addAll(loaded)
            shopkeeperUuids.clear()
            shopkeepers.forEach { shopkeeperUuids.add(UUID.fromString(it.uuid)) }
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load shopkeepers", e)
        }
    }

    private fun saveShopkeepers() {
        val dir = configDir.resolve("cobblemon-market")
        dir.createDirectories()
        dir.resolve("shopkeepers.json").writeText(gson.toJson(shopkeepers))
    }
}
```

- [ ] **Step 2: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(market): add NPC shopkeeper spawn/remove/interaction"
```

**Note:** Villager UUIDs may not persist across server restarts if the entity is unloaded. At implementation time, verify that the Villager entity persists. If not, re-spawn from saved ShopkeeperData on server start using `ServerLifecycleEvents.SERVER_STARTED`.

---

### Task 7: Shop GUI

**Files:**
- Create: `src/main/kotlin/com/cobblemonmarket/gui/ShopGui.kt`
- Create: `src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt`

- [ ] **Step 1: Implement main shop GUI**

Shows all configured items with current buy/sell prices. Click an item to open the transaction GUI.

```kotlin
// src/main/kotlin/com/cobblemonmarket/gui/ShopGui.kt
package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.pricing.PricingEngine
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items

class ShopGui(private val player: ServerPlayer) {

    fun open() {
        val items = CobblemonMarket.items
        val config = CobblemonMarket.config
        val store = CobblemonMarket.marketStore

        // Calculate rows needed (9 items per row, min 1 row, max 6)
        val rows = ((items.size + 8) / 9).coerceIn(1, 6)
        val menuType = when (rows) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }

        val gui = SimpleGui(menuType, player, false)
        gui.title = Component.literal("Market Shop")

        var slot = 0
        for ((itemId, itemEntry) in items) {
            if (slot >= rows * 9) break

            val state = store.getOrCreate(itemId)
            val sellCount = state.transactions.count { it.type == "sell" }
            val buyCount = state.transactions.count { it.type == "buy" }

            val currentSellPrice = PricingEngine.sellPrice(itemEntry.baseSellPrice, state.priceFactor)
            val currentBuyPrice = PricingEngine.buyPrice(
                itemEntry.baseSellPrice, state.priceFactor,
                sellCount, buyCount, config.spreadBase, config.spreadExtra
            )

            // Try to resolve the actual item, fallback to paper
            val mcItem = try {
                val loc = ResourceLocation.parse(itemId)
                BuiltInRegistries.ITEM.get(loc)
            } catch (e: Exception) {
                Items.PAPER
            }

            val factorPercent = (state.priceFactor * 100).toInt()

            gui.setSlot(slot, GuiElementBuilder(mcItem)
                .setName(Component.literal(formatItemName(itemId)))
                .setLore(listOf(
                    Component.literal("Sell to shop: $currentSellPrice PokeDollars"),
                    Component.literal("Buy from shop: $currentBuyPrice PokeDollars"),
                    Component.literal("Market factor: $factorPercent%"),
                    Component.literal(""),
                    Component.literal("Click to buy or sell")
                ))
                .setCallback { _, _, _ ->
                    TransactionGui(player, itemId, itemEntry, state).open()
                }
                .build())
            slot++
        }

        gui.open()
    }

    private fun formatItemName(itemId: String): String {
        // "cobblemon:rare_candy" -> "Rare Candy"
        return itemId.substringAfter(":")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
```

- [ ] **Step 2: Implement transaction GUI with batch preview**

```kotlin
// src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt
package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.data.ItemState
import com.cobblemonmarket.pricing.PricingEngine
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class TransactionGui(
    private val player: ServerPlayer,
    private val itemId: String,
    private val itemEntry: ItemEntry,
    private val state: ItemState
) {
    private var quantity = 1
    private var isBuying = true // true = buy from shop, false = sell to shop

    fun open() {
        rebuild()
    }

    private fun rebuild() {
        val gui = SimpleGui(MenuType.GENERIC_9x4, player, false)
        val config = CobblemonMarket.config
        val itemName = formatItemName(itemId)

        gui.title = Component.literal(if (isBuying) "Buy $itemName" else "Sell $itemName")

        // Row 1: Mode toggle + item display
        gui.setSlot(0, GuiElementBuilder(if (isBuying) Items.EMERALD else Items.GOLD_INGOT)
            .setName(Component.literal(if (isBuying) "Mode: BUYING" else "Mode: SELLING"))
            .setLore(listOf(Component.literal("Click to switch")))
            .setCallback { _, _, _ ->
                isBuying = !isBuying
                quantity = 1
                rebuild()
            }.build())

        val mcItem = try {
            BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        } catch (e: Exception) { Items.PAPER }

        gui.setSlot(4, GuiElementBuilder(mcItem)
            .setName(Component.literal(itemName))
            .setCount(quantity.coerceIn(1, 64))
            .build())

        // Fill spacers in row 1
        for (i in listOf(1, 2, 3, 5, 6, 7, 8)) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }

        // Row 2: Quantity buttons
        val quantities = listOf(1, 5, 10, 25, 50)
        for ((i, q) in quantities.withIndex()) {
            val selected = q == quantity
            gui.setSlot(9 + i + 2, GuiElementBuilder(
                if (selected) Items.LIME_STAINED_GLASS_PANE else Items.WHITE_STAINED_GLASS_PANE
            )
                .setName(Component.literal("Qty: $q"))
                .setCount(q.coerceIn(1, 64))
                .setCallback { _, _, _ ->
                    quantity = q
                    rebuild()
                }.build())
        }

        // Fill rest of row 2
        for (i in listOf(9, 10)) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }
        gui.setSlot(17, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
            .setName(Component.literal(" ")).build())

        // Row 3: Price preview
        val sellCount = state.transactions.count { it.type == "sell" }
        val buyCount = state.transactions.count { it.type == "buy" }
        val lore: List<Component>

        if (isBuying) {
            val result = PricingEngine.simulateBatchBuy(
                baseSellPrice = itemEntry.baseSellPrice,
                startFactor = state.priceFactor,
                quantity = quantity,
                buyGrowth = config.buyGrowth,
                factorCeiling = config.factorCeiling,
                sells = sellCount, buys = buyCount,
                spreadBase = config.spreadBase, spreadExtra = config.spreadExtra
            )
            lore = buildList {
                add(Component.literal("Buy $quantity x $itemName:"))
                result.perUnitPrices.forEachIndexed { idx, price ->
                    add(Component.literal("  #${idx + 1}: $price PokeDollars"))
                }
                add(Component.literal(""))
                add(Component.literal("Total: ${result.totalPrice} PokeDollars"))
            }
        } else {
            val result = PricingEngine.simulateBatchSell(
                baseSellPrice = itemEntry.baseSellPrice,
                startFactor = state.priceFactor,
                quantity = quantity,
                sellDecay = config.sellDecay,
                factorFloor = config.factorFloor
            )
            lore = buildList {
                add(Component.literal("Sell $quantity x $itemName:"))
                result.perUnitPrices.forEachIndexed { idx, price ->
                    add(Component.literal("  #${idx + 1}: $price PokeDollars"))
                }
                add(Component.literal(""))
                add(Component.literal("Total: ${result.totalPrice} PokeDollars"))
            }
        }

        gui.setSlot(22, GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("Price Breakdown"))
            .setLore(lore)
            .build())

        // Fill rest of row 3
        for (i in listOf(18, 19, 20, 21, 23, 24, 25, 26)) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }

        // Row 4: Back, spacers, Confirm
        gui.setSlot(27, GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("Back to Shop"))
            .setCallback { _, _, _ ->
                ShopGui(player).open()
            }.build())

        for (i in 28..33) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }

        gui.setSlot(34, GuiElementBuilder(Items.LIME_CONCRETE)
            .setName(Component.literal("Confirm"))
            .setCallback { _, _, _ ->
                executeTransaction()
            }.build())

        gui.setSlot(35, GuiElementBuilder(Items.RED_CONCRETE)
            .setName(Component.literal("Cancel"))
            .setCallback { _, _, _ ->
                ShopGui(player).open()
            }.build())

        gui.open()
    }

    private fun executeTransaction() {
        val config = CobblemonMarket.config
        val store = CobblemonMarket.marketStore
        val mcItem = try {
            BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        } catch (e: Exception) {
            player.sendSystemMessage(Component.literal("[Market] Unknown item: $itemId"))
            return
        }

        if (isBuying) {
            executeBuy(mcItem, config, store)
        } else {
            executeSell(mcItem, config, store)
        }
    }

    private fun executeBuy(
        mcItem: net.minecraft.world.item.Item,
        config: com.cobblemonmarket.config.MarketConfig,
        store: com.cobblemonmarket.data.MarketStore
    ) {
        val sellCount = state.transactions.count { it.type == "sell" }
        val buyCount = state.transactions.count { it.type == "buy" }

        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = itemEntry.baseSellPrice,
            startFactor = state.priceFactor,
            quantity = quantity,
            buyGrowth = config.buyGrowth,
            factorCeiling = config.factorCeiling,
            sells = sellCount, buys = buyCount,
            spreadBase = config.spreadBase, spreadExtra = config.spreadExtra
        )

        // Check if player has enough money
        // Uses Cobblemon Economy API — verify exact method at implementation time
        val balance = getPlayerBalance(player)
        if (balance < result.totalPrice) {
            player.sendSystemMessage(Component.literal(
                "[Market] Not enough PokeDollars! Need ${result.totalPrice}, have $balance."
            ))
            return
        }

        // Check inventory space
        if (!hasInventorySpace(player, quantity)) {
            player.sendSystemMessage(Component.literal("[Market] Not enough inventory space!"))
            return
        }

        // Execute: deduct money, give items, update state
        subtractBalance(player, result.totalPrice)
        player.inventory.add(ItemStack(mcItem, quantity))
        state.priceFactor = result.finalFactor
        repeat(quantity) { store.addTransaction(itemId, "buy") }
        store.save()

        player.sendSystemMessage(Component.literal(
            "[Market] Bought $quantity x ${formatItemName(itemId)} for ${result.totalPrice} PokeDollars."
        ))

        // Refresh GUI
        ShopGui(player).open()
    }

    private fun executeSell(
        mcItem: net.minecraft.world.item.Item,
        config: com.cobblemonmarket.config.MarketConfig,
        store: com.cobblemonmarket.data.MarketStore
    ) {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = itemEntry.baseSellPrice,
            startFactor = state.priceFactor,
            quantity = quantity,
            sellDecay = config.sellDecay,
            factorFloor = config.factorFloor
        )

        // Check if player has enough items
        val itemCount = countItems(player, mcItem)
        if (itemCount < quantity) {
            player.sendSystemMessage(Component.literal(
                "[Market] You only have $itemCount x ${formatItemName(itemId)}."
            ))
            return
        }

        // Execute: remove items, add money, update state
        removeItems(player, mcItem, quantity)
        addBalance(player, result.totalPrice)
        state.priceFactor = result.finalFactor
        repeat(quantity) { store.addTransaction(itemId, "sell") }
        store.save()

        player.sendSystemMessage(Component.literal(
            "[Market] Sold $quantity x ${formatItemName(itemId)} for ${result.totalPrice} PokeDollars."
        ))

        // Refresh GUI
        ShopGui(player).open()
    }

    // --- Economy bridge methods ---
    // These wrap Cobblemon Economy API. Verify exact API at implementation time.

    private fun getPlayerBalance(player: ServerPlayer): Int {
        // TODO: Replace with actual Cobblemon Economy API call
        // Expected: CobblemonEconomy.getEconomyManager().getBalance(player.uuid).toInt()
        return try {
            val economyClass = Class.forName("com.cobblemon.economy.CobblemonEconomy")
            val manager = economyClass.getMethod("getEconomyManager").invoke(null)
            val balance = manager.javaClass.getMethod("getBalance", java.util.UUID::class.java)
                .invoke(manager, player.uuid) as java.math.BigDecimal
            balance.toInt()
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to get balance", e)
            0
        }
    }

    private fun subtractBalance(player: ServerPlayer, amount: Int) {
        try {
            val economyClass = Class.forName("com.cobblemon.economy.CobblemonEconomy")
            val manager = economyClass.getMethod("getEconomyManager").invoke(null)
            manager.javaClass.getMethod("subtractBalance", java.util.UUID::class.java, java.math.BigDecimal::class.java)
                .invoke(manager, player.uuid, java.math.BigDecimal(amount))
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to subtract balance", e)
        }
    }

    private fun addBalance(player: ServerPlayer, amount: Int) {
        try {
            val economyClass = Class.forName("com.cobblemon.economy.CobblemonEconomy")
            val manager = economyClass.getMethod("getEconomyManager").invoke(null)
            manager.javaClass.getMethod("addBalance", java.util.UUID::class.java, java.math.BigDecimal::class.java)
                .invoke(manager, player.uuid, java.math.BigDecimal(amount))
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to add balance", e)
        }
    }

    // --- Inventory helpers ---

    private fun countItems(player: ServerPlayer, item: net.minecraft.world.item.Item): Int {
        return player.inventory.items.sumOf { stack ->
            if (stack.item == item) stack.count else 0
        }
    }

    private fun removeItems(player: ServerPlayer, item: net.minecraft.world.item.Item, count: Int) {
        var remaining = count
        for (stack in player.inventory.items) {
            if (remaining <= 0) break
            if (stack.item == item) {
                val take = minOf(remaining, stack.count)
                stack.shrink(take)
                remaining -= take
            }
        }
    }

    private fun hasInventorySpace(player: ServerPlayer, count: Int): Boolean {
        var space = 0
        for (stack in player.inventory.items) {
            if (stack.isEmpty) space += 64
            // Don't count partially-filled stacks for simplicity
        }
        return space >= count
    }

    private fun formatItemName(itemId: String): String {
        return itemId.substringAfter(":")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
```

- [ ] **Step 3: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(market): add shop GUI with batch transaction preview"
```

**Implementation note:** The economy bridge methods use reflection as a fallback. At implementation time, if Cobblemon Economy is available as a compile dependency, replace the reflection calls with direct API calls. The expected API shape is:
```kotlin
import com.cobblemon.economy.CobblemonEconomy
val manager = CobblemonEconomy.getEconomyManager()
manager.getBalance(uuid)          // BigDecimal
manager.addBalance(uuid, amount)  // void
manager.subtractBalance(uuid, amount) // Boolean
```

---

### Task 8: Commands

**Files:**
- Create: `src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt`

- [ ] **Step 1: Implement all /market commands**

```kotlin
// src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt
package com.cobblemonmarket.commands

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.pricing.PricingEngine
import com.cobblemonmarket.shop.ShopkeeperManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object MarketCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("market")
                .then(Commands.literal("prices")
                    .executes { ctx ->
                        showPrices(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("history")
                    .then(Commands.argument("item", StringArgumentType.string())
                        .suggests { _, builder ->
                            CobblemonMarket.items.keys.forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val itemId = StringArgumentType.getString(ctx, "item")
                            showHistory(ctx.source, itemId)
                            1
                        }
                    )
                )
                .then(Commands.literal("npc")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val player = ctx.source.playerOrException
                                val name = StringArgumentType.getString(ctx, "name")
                                createNpc(player, name)
                                1
                            }
                        )
                    )
                    .then(Commands.literal("remove")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            removeNpc(player)
                            1
                        }
                    )
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("setfactor")
                        .then(Commands.argument("item", StringArgumentType.string())
                            .suggests { _, builder ->
                                CobblemonMarket.items.keys.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes { ctx ->
                                    val itemId = StringArgumentType.getString(ctx, "item")
                                    val value = DoubleArgumentType.getDouble(ctx, "value")
                                    setFactor(ctx.source, itemId, value)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx ->
                            reload(ctx.source)
                            1
                        }
                    )
                )
        )
    }

    private fun showPrices(source: CommandSourceStack) {
        val items = CobblemonMarket.items
        val config = CobblemonMarket.config
        val store = CobblemonMarket.marketStore

        source.sendSystemMessage(Component.literal("[Market] === Current Prices ==="))

        for ((itemId, entry) in items) {
            val state = store.getOrCreate(itemId)
            val sellCount = state.transactions.count { it.type == "sell" }
            val buyCount = state.transactions.count { it.type == "buy" }

            val sellPrice = PricingEngine.sellPrice(entry.baseSellPrice, state.priceFactor)
            val buyPrice = PricingEngine.buyPrice(
                entry.baseSellPrice, state.priceFactor,
                sellCount, buyCount, config.spreadBase, config.spreadExtra
            )
            val factorPercent = (state.priceFactor * 100).toInt()

            val name = itemId.substringAfter(":").replace("_", " ")
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

            source.sendSystemMessage(Component.literal(
                "  $name: Sell $sellPrice | Buy $buyPrice ($factorPercent%)"
            ))
        }
    }

    private fun showHistory(source: CommandSourceStack, itemId: String) {
        if (itemId !in CobblemonMarket.items) {
            source.sendSystemMessage(Component.literal("[Market] Unknown item: $itemId"))
            return
        }
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val sells = state.transactions.count { it.type == "sell" }
        val buys = state.transactions.count { it.type == "buy" }
        val total = state.transactions.size
        val factorPercent = (state.priceFactor * 100).toInt()

        source.sendSystemMessage(Component.literal("[Market] History for $itemId:"))
        source.sendSystemMessage(Component.literal("  Factor: $factorPercent%"))
        source.sendSystemMessage(Component.literal("  Last $total transactions: $sells sells, $buys buys"))
        source.sendSystemMessage(Component.literal(
            "  Skew: ${if (total < 2) "N/A" else "${"%.1f".format(sells.toDouble() / total * 100)}% sells"}"
        ))
    }

    private fun createNpc(player: ServerPlayer, name: String) {
        if (ShopkeeperManager.spawnShopkeeper(player, name)) {
            player.sendSystemMessage(Component.literal("[Market] Shopkeeper '$name' created."))
        } else {
            player.sendSystemMessage(Component.literal("[Market] Failed to create shopkeeper."))
        }
    }

    private fun removeNpc(player: ServerPlayer) {
        if (ShopkeeperManager.removeNearest(player)) {
            player.sendSystemMessage(Component.literal("[Market] Nearest shopkeeper removed."))
        } else {
            player.sendSystemMessage(Component.literal("[Market] No shopkeeper found within 5 blocks."))
        }
    }

    private fun setFactor(source: CommandSourceStack, itemId: String, value: Double) {
        if (itemId !in CobblemonMarket.items) {
            source.sendSystemMessage(Component.literal("[Market] Unknown item: $itemId"))
            return
        }
        CobblemonMarket.marketStore.setFactor(itemId, value)
        source.sendSystemMessage(Component.literal(
            "[Market] Set $itemId factor to ${(value * 100).toInt()}%"
        ))
    }

    private fun reload(source: CommandSourceStack) {
        val configDir = net.fabricmc.loader.api.FabricLoader.getInstance().configDir
        CobblemonMarket.config = com.cobblemonmarket.config.MarketConfig.load(configDir)
        CobblemonMarket.items = com.cobblemonmarket.config.ItemConfig.load(configDir)
        source.sendSystemMessage(Component.literal("[Market] Config reloaded. ${CobblemonMarket.items.size} items loaded."))
    }
}
```

- [ ] **Step 2: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(market): add all /market commands"
```

---

### Task 9: Integration Wiring & Manual Testing

**Files:**
- Modify: `src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt` (final assembly)

- [ ] **Step 1: Finalize entry point**

```kotlin
package com.cobblemonmarket

import com.cobblemonmarket.commands.MarketCommands
import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.data.MarketStore
import com.cobblemonmarket.pricing.PricingEngine
import com.cobblemonmarket.shop.ShopkeeperManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonMarket : ModInitializer {
    const val MOD_ID = "cobblemon-market"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: MarketConfig
    lateinit var items: Map<String, ItemEntry>
    lateinit var marketStore: MarketStore

    override fun onInitialize() {
        logger.info("Cobblemon Market initializing...")

        val configDir = FabricLoader.getInstance().configDir
        config = MarketConfig.load(configDir)
        items = ItemConfig.load(configDir)
        marketStore = MarketStore(configDir)
        marketStore.load()

        ShopkeeperManager.init(configDir)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            MarketCommands.register(dispatcher)
        }

        // Hourly price recovery
        var recoveryTickCounter = 0
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            recoveryTickCounter++
            if (recoveryTickCounter % 72000 == 0) { // Every hour at 20 tps
                applyRecoveryToAll()
            }
        }

        logger.info("Cobblemon Market initialized! ${items.size} items, market state loaded.")
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
}
```

- [ ] **Step 2: Build the mod JAR**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. JAR at `build/libs/cobblemon-market-1.0.0.jar`.

- [ ] **Step 3: Manual test plan**

Run a local Fabric server with Cobblemon + Cobblemon Economy + this mod. Test:

1. **Config generation:** Start server, verify `config/cobblemon-market/config.json`, `items.json` are created.
2. **Price command:** Run `/market prices`, verify all 5 items show with correct starting prices (sell = base, buy = base * 3).
3. **NPC spawn:** Run `/market npc create "Poke Mart"`. Verify villager appears with custom name, no AI, invulnerable.
4. **Shop GUI:** Right-click the NPC. Verify GUI opens with all items and correct prices.
5. **Buy flow:** Click an item, select quantity 5, verify price breakdown shows increasing costs per unit. Confirm purchase. Verify items in inventory, money deducted, GUI refreshes with new prices.
6. **Sell flow:** With items in inventory, click item, switch to Sell mode, select quantity. Verify decreasing payout per unit. Confirm. Verify items removed, money added.
7. **Price crash:** Sell 50 rare candies. Verify factor drops to ~0.364, sell price drops accordingly, buy price has high spread.
8. **History:** Run `/market history cobblemon:rare_candy`, verify transaction count and skew.
9. **Admin setfactor:** Run `/market admin setfactor cobblemon:rare_candy 1.0`, verify factor resets.
10. **NPC removal:** Run `/market npc remove` near the NPC, verify it disappears.
11. **Recovery:** Wait or adjust tick speed, verify factor slowly recovers toward 1.0.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(market): finalize wiring and integration"
```
