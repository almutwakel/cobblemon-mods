# Cobblemon Gacha — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a third server-only NeoForge mod, `cobblemon-gacha`, that lets players spend tier-coded keys at world-placed crates to roll a CS:GO-style animation that lands on a weighted reward from a CSV-defined loot table, with daily key grants for login + ranked battles, admin grants for milestones, and tier-aware server-wide announcements.

**Architecture:** Same toolchain and patterns as `cobblemon-market` and `cobblemon-ranked` — Kotlin entry with `@Mod` class form, ModDevGradle build, Gson per-player JSON persistence, `DeferredRegister<MenuType<*>>` for custom container UIs, vanilla items + `DataComponents.CUSTOM_DATA` to tag gacha keys (no item registration). Reward roll commits before the visual animation so the animation is purely cosmetic. Right-click handler ignores any block coord not configured as a crate.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.227, Kotlin 2.2.20, ModDevGradle 2.0.78, KotlinForForge 5.11.0, Cobblemon NeoForge 1.7.3+1.21.1 (for the `BATTLE_VICTORY` event), JUnit 5, Gson 2.10. Server-only mod (`displayTest = IGNORE_ALL_VERSION`).

**Spec:** `docs/superpowers/specs/2026-05-11-cobblemon-gacha-design.md`

---

## File touch summary

All new files unless marked otherwise. Paths relative to repo root.

| Action | Path | Purpose |
|---|---|---|
| Create | `cobblemon-gacha/build.gradle.kts` | ModDevGradle build (copy market) |
| Create | `cobblemon-gacha/settings.gradle.kts` | Standalone gradle project |
| Create | `cobblemon-gacha/gradle.properties` | Versions |
| Copy | `cobblemon-gacha/gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle wrapper from `cobblemon-market/` |
| Create | `cobblemon-gacha/src/main/resources/META-INF/neoforge.mods.toml` | Mod manifest |
| Create | `cobblemon-gacha/src/main/resources/tables/common.csv` | Bundled default Common table |
| Create | `cobblemon-gacha/src/main/resources/tables/rare.csv` | Bundled default Rare table |
| Create | `cobblemon-gacha/src/main/resources/tables/ultra.csv` | Bundled default Ultra table |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt` | `@Mod` entry, event wiring |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/KeyTier.kt` | enum |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/LootTable.kt` | `LootTable`, `LootEntry`, `LootTier`, `ItemSpec` |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/PlayerGachaData.kt` | mutable per-player record |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/PlayerGachaStore.kt` | per-player JSON persistence |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/GachaConfig.kt` | crate coords, animation ticks |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt` | CSV→JSON migrator + JSON loader |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/item/KeyItems.kt` | build keyed ItemStacks |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/item/PlaceholderItems.kt` | build placeholder ItemStacks |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/reward/RewardRoller.kt` | weighted random pick |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/reward/RewardGranter.kt` | materialize + deliver |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/announce/PullAnnouncer.kt` | broadcast + sound + firework |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/interaction/CrateInteractionHandler.kt` | right-click router |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/interaction/KeyGrantHooks.kt` | login + battle event subscribers |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/GachaMenuRegistry.kt` | `DeferredRegister<MenuType<*>>` |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/RollMenu.kt` | animated container menu |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/OddsMenu.kt` | read-only odds preview menu |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/util/TickScheduler.kt` | delayed runnable scheduler |
| Create | `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/commands/GachaCommands.kt` | all Brigadier commands |
| Create | `cobblemon-gacha/src/test/kotlin/com/cobblemongacha/config/LootTableLoaderTest.kt` | CSV-parse tests |
| Create | `cobblemon-gacha/src/test/kotlin/com/cobblemongacha/reward/RewardRollerTest.kt` | weighted-roll tests |
| Create | `cobblemon-gacha/src/test/kotlin/com/cobblemongacha/data/PlayerGachaStoreTest.kt` | JSON round-trip |

---

## Task 1: Scaffold the module (build files, manifest, empty entry, gradle wrapper)

**Files:**
- Create: `cobblemon-gacha/build.gradle.kts`
- Create: `cobblemon-gacha/settings.gradle.kts`
- Create: `cobblemon-gacha/gradle.properties`
- Copy:   `cobblemon-gacha/gradlew`, `gradlew.bat`, `gradle/wrapper/*` (from `cobblemon-market/`)
- Create: `cobblemon-gacha/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt`

- [ ] **Step 1: Create the gradle wrapper by copying from cobblemon-market**

```bash
mkdir -p cobblemon-gacha/gradle/wrapper cobblemon-gacha/src/main/kotlin/com/cobblemongacha cobblemon-gacha/src/main/resources/META-INF cobblemon-gacha/src/main/resources/tables cobblemon-gacha/src/test/kotlin/com/cobblemongacha
cp cobblemon-market/gradlew cobblemon-market/gradlew.bat cobblemon-gacha/
cp cobblemon-market/gradle/wrapper/gradle-wrapper.jar cobblemon-market/gradle/wrapper/gradle-wrapper.properties cobblemon-gacha/gradle/wrapper/
chmod +x cobblemon-gacha/gradlew
```

- [ ] **Step 2: Write `cobblemon-gacha/settings.gradle.kts`**

```kotlin
rootProject.name = "cobblemon-gacha"

pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}
```

- [ ] **Step 3: Write `cobblemon-gacha/gradle.properties`**

```
org.gradle.jvmargs=-Xmx4G
minecraft_version=1.21.1
neoforge_version=21.1.227
kotlin_for_forge_version=5.11.0
cobblemon_version=1.7.3+1.21.1
mod_version=1.0.0
maven_group=com.cobblemongacha
```

- [ ] **Step 4: Write `cobblemon-gacha/build.gradle.kts`**

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
    mavenCentral()
    maven("https://artefacts.cobblemon.com/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
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
        register("cobblemon_gacha") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.property("kotlin_for_forge_version")}")
    implementation("com.cobblemon:neoforge:${project.property("cobblemon_version")}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
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

- [ ] **Step 5: Write `cobblemon-gacha/src/main/resources/META-INF/neoforge.mods.toml`**

```toml
modLoader = "kotlinforforge"
loaderVersion = "[5,)"
license = "All Rights Reserved"

[[mods]]
modId = "cobblemon_gacha"
version = "${version}"
displayName = "Cobblemon Gacha"
description = "Gacha-style lootboxes for the Cobblemon server"
authors = "almutwakel"
# Server-side only: clients don't need this jar.
displayTest = "IGNORE_ALL_VERSION"

[[dependencies.cobblemon_gacha]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_gacha]]
modId = "kotlinforforge"
type = "required"
versionRange = "[5,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.cobblemon_gacha]]
modId = "cobblemon"
type = "required"
versionRange = "[1.7.1,)"
ordering = "AFTER"
side = "BOTH"
```

- [ ] **Step 6: Write the minimal entry `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt`**

```kotlin
package com.cobblemongacha

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonGacha.MOD_ID)
class CobblemonGacha(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Gacha initializing (scaffold)…")
    }

    companion object {
        const val MOD_ID = "cobblemon_gacha"
        const val PERSISTENCE_DIR_NAME = "cobblemon-gacha"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
```

- [ ] **Step 7: Verify the project compiles**

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add cobblemon-gacha/
git commit -m "Add cobblemon-gacha scaffold (build files, manifest, empty entry)"
```

---

## Task 2: Bundle the loot-table CSVs as default resources

**Files:**
- Create: `cobblemon-gacha/src/main/resources/tables/common.csv`
- Create: `cobblemon-gacha/src/main/resources/tables/rare.csv`
- Create: `cobblemon-gacha/src/main/resources/tables/ultra.csv`

These ship inside the jar and are copied to `config/cobblemon-gacha/tables/` on first boot.

- [ ] **Step 1: Copy the user's CSVs verbatim**

```bash
cp "/Users/almutwakel/Downloads/loot_tables.csv/Common Key-Table 1.csv" cobblemon-gacha/src/main/resources/tables/common.csv
cp "/Users/almutwakel/Downloads/loot_tables.csv/Rare Key-Table 1.csv"   cobblemon-gacha/src/main/resources/tables/rare.csv
cp "/Users/almutwakel/Downloads/loot_tables.csv/Ultra Key-Table 1.csv"  cobblemon-gacha/src/main/resources/tables/ultra.csv
```

- [ ] **Step 2: Sanity-check the row counts**

```bash
wc -l cobblemon-gacha/src/main/resources/tables/*.csv
```
Expected: 23 lines (Common), 26 lines (Rare), 21 lines (Ultra) — including the header and TOTAL rows. Exact counts may differ but each file should be > 15 lines.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/resources/tables/
git commit -m "Bundle Common/Rare/Ultra CSV loot tables as default resources"
```

---

## Task 3: Data types — KeyTier, LootTable, LootEntry, ItemSpec

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/KeyTier.kt`
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/LootTable.kt`

Pure data classes — no behavior, no tests yet.

- [ ] **Step 1: Write `KeyTier.kt`**

```kotlin
package com.cobblemongacha.data

/**
 * Three lootbox tiers. Each tier has a Common/Rare/Ultra key (vanilla item + custom_data tag)
 * and one configured crate coord. The order matters: higher ordinal = rarer.
 */
enum class KeyTier(val key: String, val displayName: String) {
    COMMON("common", "Common"),
    RARE("rare", "Rare"),
    ULTRA("ultra", "Ultra");

    companion object {
        fun fromKey(k: String): KeyTier? = entries.firstOrNull { it.key == k.lowercase() }
    }
}
```

- [ ] **Step 2: Write `LootTable.kt`**

```kotlin
package com.cobblemongacha.data

/** Tier banner within a loot table: drives lore/announcements (e.g. "(HIGH)" tag). */
enum class LootTier { Floor, Mid, High, Jackpot }

/**
 * One materialisable item inside a `LootEntry`. Three forms (sealed):
 *   - `Vanilla` — a regular vanilla or modded item id with count and optional name/lore overrides.
 *   - `GachaKeyRef` — emit a Common/Rare/Ultra Key ItemStack (so jackpot entries can grant keys).
 *   - `Placeholder` — emit a placeholder ItemStack (Pokemon egg, voucher, TBD ultra reward).
 *
 * `RewardGranter` walks one of these into an actual `ItemStack`.
 */
sealed class ItemSpec {
    data class Vanilla(
        val id: String,
        val count: Int,
        val nameOverride: String? = null,
        val loreLines: List<String> = emptyList(),
    ) : ItemSpec()

    data class GachaKeyRef(val tier: KeyTier, val count: Int) : ItemSpec()

    /** kind: "pokemon_egg" | "voucher" | "tbd_ultra" — picks the vanilla base item. */
    data class Placeholder(val kind: String, val label: String, val count: Int) : ItemSpec()
}

/**
 * One row in a loot table. `weightPct` is the raw percentage from the CSV (before normalisation).
 * 0% entries are kept in the table but skipped by RewardRoller (used to record unfinished entries).
 * `label` is the human-readable string shown in announcements; copied verbatim from the CSV "Item" cell.
 * `items` is the list of stacks delivered if this entry is rolled (one entry may bundle several stacks).
 */
data class LootEntry(
    val lootTier: LootTier,
    val label: String,
    val weightPct: Double,
    val items: List<ItemSpec>,
    val notes: String = "",
)

/**
 * A whole loot table. `entries` preserves CSV order. `totalWeightPct` is the raw sum of `weightPct`
 * before normalisation — kept so admins editing the JSON can see if their odds drift from 100%.
 */
data class LootTable(
    val tier: KeyTier,
    val totalWeightPct: Double,
    val entries: List<LootEntry>,
)
```

- [ ] **Step 3: Compile to confirm types are well-formed**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/
git commit -m "Add KeyTier, LootTable, LootEntry, ItemSpec data types"
```

---

## Task 4: LootTableLoader — CSV→LootTable parser

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt`
- Create: `cobblemon-gacha/src/test/kotlin/com/cobblemongacha/config/LootTableLoaderTest.kt`

The loader (a) parses CSV strings into `LootTable`, (b) on first boot copies bundled CSVs from the jar to `config/cobblemon-gacha/tables/*.json` after converting them, (c) loads existing JSON tables on subsequent boots. This task only implements **CSV parsing** — JSON read/write comes in a later task once we know `RewardRoller` consumes `LootTable` the way we expect.

The CSV "Item" cell is parsed into `ItemSpec`s via a hand-written tokenizer:
- Leading integer → count.
- Substring match against a known-items map (`"Poké Ball"` → `cobblemon:poke_ball`, `"Master Ball"` → `cobblemon:master_ball`, `"Rare Candy"` → `cobblemon:rare_candy`, `"Exp Candy S"` → `cobblemon:exp_candy_s`, etc.).
- Substring `"Egg"` → `Placeholder("pokemon_egg", originalLabel, 1)`.
- Substring `"Voucher"` or `"Fragment"` → `Placeholder("voucher", originalLabel, 1)`.
- Substrings `"Common Key" | "Rare Key" | "Ultra Key"` → `GachaKeyRef(matchedTier, count)`.
- Empty "Item" cell → `Placeholder("tbd_ultra", "TBD Ultra Reward", 1)`.
- Unknown text → `Placeholder("tbd_ultra", label, 1)` plus a one-line warn log.

- [ ] **Step 1: Write the test file first**

```kotlin
package com.cobblemongacha.config

import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LootTableLoaderTest {

    private val commonCsv = """
        Tier,Item,Chance %,Notes,
        Floor,20 Poké Balls,18.0%,"Bread and butter, always useful",
        ,10 Great Balls,15.0%,,
        ,3 Revives,12.0%,,
        Jackpot,1 Rare Key,0.5%,Upgrade,
        ,TOTAL,100.0%,,
    """.trimIndent()

    private val ultraCsv = """
        Tier,Item,Chance %,Notes,
        Floor,1 Ability Patch,12.0%,Rare high-tier is ultra floor,
        ,,8.0%,Farming combo,
        ,,7.0%,,
        Jackpot,1 Shiny Egg,1.0%,Best shiny egg in the game,
        ,TOTAL,98.0%,,
    """.trimIndent()

    @Test
    fun `parses Common header row and skips TOTAL`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        // 4 reward rows (TOTAL filtered out)
        assertEquals(4, table.entries.size)
    }

    @Test
    fun `propagates Tier column when blank`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        // Rows 1 and 2 inherit Floor from row 0; row 3 is Jackpot
        assertEquals(LootTier.Floor, table.entries[0].lootTier)
        assertEquals(LootTier.Floor, table.entries[1].lootTier)
        assertEquals(LootTier.Floor, table.entries[2].lootTier)
        assertEquals(LootTier.Jackpot, table.entries[3].lootTier)
    }

    @Test
    fun `parses count and item id for Pokeballs`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val entry = table.entries[0]
        assertEquals("20 Poké Balls", entry.label)
        assertEquals(1, entry.items.size)
        val item = entry.items[0] as ItemSpec.Vanilla
        assertEquals("cobblemon:poke_ball", item.id)
        assertEquals(20, item.count)
    }

    @Test
    fun `parses GachaKeyRef for jackpot Rare Key`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val key = table.entries[3].items[0] as ItemSpec.GachaKeyRef
        assertEquals(KeyTier.RARE, key.tier)
        assertEquals(1, key.count)
    }

    @Test
    fun `parses weight percentages`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        assertEquals(18.0, table.entries[0].weightPct, 1e-9)
        assertEquals(0.5, table.entries[3].weightPct, 1e-9)
    }

    @Test
    fun `blank Item cell becomes TBD ultra placeholder`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        // Rows 1 and 2 are blank — should become TBD placeholders.
        val tbd1 = table.entries[1].items[0] as ItemSpec.Placeholder
        val tbd2 = table.entries[2].items[0] as ItemSpec.Placeholder
        assertEquals("tbd_ultra", tbd1.kind)
        assertEquals("tbd_ultra", tbd2.kind)
        assertTrue(tbd1.label.contains("TBD"))
    }

    @Test
    fun `Shiny Egg label parses as pokemon_egg placeholder`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        val egg = table.entries[3].items[0] as ItemSpec.Placeholder
        assertEquals("pokemon_egg", egg.kind)
        assertTrue(egg.label.contains("Shiny Egg"))
    }

    @Test
    fun `totalWeightPct reflects sum of entry weightPcts`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        // 12 + 8 + 7 + 1 = 28
        assertEquals(28.0, table.entries.sumOf { it.weightPct }, 1e-9)
        // totalWeightPct field stores what CSV TOTAL row claimed (98.0)
        assertEquals(98.0, table.totalWeightPct, 1e-9)
    }
}
```

- [ ] **Step 2: Run the test — it should fail to compile**

```bash
cd cobblemon-gacha && ./gradlew compileTestKotlin --no-daemon 2>&1 | tail -10
```
Expected: errors about `LootTableLoader` unresolved reference.

- [ ] **Step 3: Write `LootTableLoader.kt`**

```kotlin
package com.cobblemongacha.config

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.LootTier

/**
 * Parses Society Sunlit-style loot CSVs into structured `LootTable` objects.
 *
 * Columns (CSV header is consumed and discarded): `Tier, Item, Chance %, Notes, <trailing empty>`.
 * Blank `Tier` cells inherit from the row above (the CSVs use this for compactness).
 * `TOTAL` rows are recognised and stored as `totalWeightPct` but never become a `LootEntry`.
 * The `Item` cell is parsed into a list of `ItemSpec`s via [parseItemLabel].
 */
object LootTableLoader {

    private val log = org.slf4j.LoggerFactory.getLogger("cobblemon-gacha/loot-loader")

    /**
     * Map of human substring → vanilla/modded item id. Substrings are matched longest-first to
     * avoid `Great Ball` being absorbed by `Ball`. Add new entries as the CSVs grow.
     */
    private val KNOWN_ITEMS: List<Pair<String, String>> = listOf(
        "Master Ball" to "cobblemon:master_ball",
        "Ultra Ball" to "cobblemon:ultra_ball",
        "Great Ball" to "cobblemon:great_ball",
        "Quick Ball" to "cobblemon:quick_ball",
        "Quick balls" to "cobblemon:quick_ball",
        "Poké Ball" to "cobblemon:poke_ball",
        "Poke Ball" to "cobblemon:poke_ball",
        "Max Revive" to "cobblemon:max_revive",
        "Max Potion" to "cobblemon:max_potion",
        "Revive" to "cobblemon:revive",
        "Exp Candy XL" to "cobblemon:exp_candy_xl",
        "EXP Candy XL" to "cobblemon:exp_candy_xl",
        "Exp Candy L" to "cobblemon:exp_candy_l",
        "EXP Candy L" to "cobblemon:exp_candy_l",
        "EXP candy XL" to "cobblemon:exp_candy_xl",
        "Exp Candy M" to "cobblemon:exp_candy_m",
        "Exp Candy S" to "cobblemon:exp_candy_s",
        "Rare Candy" to "cobblemon:rare_candy",
        "Rare candy" to "cobblemon:rare_candy",
        "Lucky Egg" to "cobblemon:lucky_egg",
        "Lucky egg" to "cobblemon:lucky_egg",
        "Exp Share" to "cobblemon:exp_share",
        "EXP share" to "cobblemon:exp_share",
        "Bottle Cap" to "cobblemon:bottle_cap",
        "Gold Bottle Cap" to "cobblemon:gold_bottle_cap",
        "Ability Patch" to "cobblemon:ability_patch",
        "Focus" to "cobblemon:focus_sash",
        "Leftovers" to "cobblemon:leftovers",
        "PP Up" to "cobblemon:pp_up",
        "Nature Mint" to "cobblemon:nature_mint",
        "Nature mints" to "cobblemon:nature_mint",
        "Nature mint" to "cobblemon:nature_mint",
        "Mint seed" to "minecraft:wheat_seeds",
        "Silk Touch Book" to "minecraft:enchanted_book",
        "Nether wart" to "minecraft:nether_wart",
        "Evolution Stone" to "cobblemon:fire_stone",
        "EV Vitamin" to "cobblemon:hp_up",
        "IV Candy" to "cobblemon:rare_candy",
        "Bee egg" to "minecraft:egg",
    )

    /**
     * Parse a CSV string into a `LootTable` for the given key tier.
     *
     * @param tier The key tier this table belongs to.
     * @param csv The full CSV file content (including header row).
     */
    fun parseCsv(tier: KeyTier, csv: String): LootTable {
        val lines = csv.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        // Skip header
        if (lines.size < 2) return LootTable(tier, 0.0, emptyList())
        val dataLines = lines.drop(1)

        val entries = mutableListOf<LootEntry>()
        var currentTier: LootTier = LootTier.Floor
        var totalWeight = 0.0
        var tbdCounter = 1

        for (line in dataLines) {
            val cells = splitCsvLine(line)
            val tierCell = cells.getOrNull(0)?.trim().orEmpty()
            val itemCell = cells.getOrNull(1)?.trim().orEmpty()
            val pctCell = cells.getOrNull(2)?.trim().orEmpty()
            val notesCell = cells.getOrNull(3)?.trim().orEmpty()

            // TOTAL row
            if (itemCell.equals("TOTAL", ignoreCase = true)) {
                totalWeight = parsePct(pctCell)
                continue
            }

            if (tierCell.isNotEmpty()) {
                currentTier = parseTier(tierCell) ?: currentTier
            }
            val weight = parsePct(pctCell)

            val items: List<ItemSpec> = parseItemLabel(itemCell, weight, tbdCounter)
            if (itemCell.isBlank()) tbdCounter++

            entries.add(LootEntry(
                lootTier = currentTier,
                label = if (itemCell.isBlank()) "TBD Ultra Reward #${tbdCounter - 1}" else itemCell,
                weightPct = weight,
                items = items,
                notes = notesCell,
            ))
        }

        return LootTable(tier, totalWeight, entries)
    }

    private fun parseTier(s: String): LootTier? = when (s.lowercase()) {
        "floor" -> LootTier.Floor
        "mid" -> LootTier.Mid
        "high" -> LootTier.High
        "jackpot" -> LootTier.Jackpot
        else -> null
    }

    private fun parsePct(s: String): Double = s.removeSuffix("%").trim().toDoubleOrNull() ?: 0.0

    /**
     * Hand-written CSV line splitter. Handles double-quoted cells (the Society Sunlit CSVs
     * quote any cell that contains a comma, e.g. "Bread and butter, always useful").
     */
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(buf.toString()); buf.clear() }
                else -> buf.append(c)
            }
        }
        out.add(buf.toString())
        return out
    }

    /**
     * Tokenise the "Item" cell into `ItemSpec`s.
     *
     * The first integer in the label is treated as a count (defaults to 1).
     * The label is then matched against:
     *   - "<tier> Key" → GachaKeyRef
     *   - "<...> Egg"  → Placeholder("pokemon_egg", label, 1)
     *   - "<...> Fragment" | "<...> Voucher" → Placeholder("voucher", label, 1)
     *   - known item substrings → Vanilla(id, count)
     *   - blank → Placeholder("tbd_ultra", "TBD Ultra Reward #<n>", 1)
     *   - unknown → Placeholder("tbd_ultra", label, 1) with a warn log
     */
    private fun parseItemLabel(label: String, weight: Double, tbdIndex: Int): List<ItemSpec> {
        if (label.isBlank()) {
            return listOf(ItemSpec.Placeholder("tbd_ultra", "TBD Ultra Reward #$tbdIndex", 1))
        }
        val count = "^\\s*(\\d+)".toRegex().find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val lower = label.lowercase()

        // Gacha key references
        if (lower.contains("ultra key")) return listOf(ItemSpec.GachaKeyRef(KeyTier.ULTRA, count))
        if (lower.contains("rare key"))  return listOf(ItemSpec.GachaKeyRef(KeyTier.RARE, count))
        if (lower.contains("common key")) return listOf(ItemSpec.GachaKeyRef(KeyTier.COMMON, count))

        // Pokemon-egg-shaped placeholders (Shiny Egg, Mid-tier Egg, etc.)
        if (lower.contains("egg") && !lower.contains("lucky egg") && !lower.contains("bee egg")) {
            return listOf(ItemSpec.Placeholder("pokemon_egg", label, 1))
        }

        // Monument vouchers / fragments
        if (lower.contains("voucher") || lower.contains("fragment") || lower.contains("monument")) {
            return listOf(ItemSpec.Placeholder("voucher", label, 1))
        }

        // Substring match against the known-items table (longest first)
        val sortedKnown = KNOWN_ITEMS.sortedByDescending { it.first.length }
        for ((needle, id) in sortedKnown) {
            if (lower.contains(needle.lowercase())) {
                return listOf(ItemSpec.Vanilla(id, count))
            }
        }

        // Bundle items like "Competitive Ready-Kit (3 Vitamins each + 2 Mints)" — fall back to placeholder.
        log.warn("LootTableLoader: unknown item label '{}' (weight={}); using TBD placeholder", label, weight)
        return listOf(ItemSpec.Placeholder("tbd_ultra", label, 1))
    }
}
```

- [ ] **Step 4: Run the tests — they should pass**

```bash
cd cobblemon-gacha && ./gradlew test --no-daemon 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt cobblemon-gacha/src/test/kotlin/com/cobblemongacha/config/LootTableLoaderTest.kt
git commit -m "Add LootTableLoader.parseCsv — turns Society Sunlit CSVs into LootTable"
```

---

## Task 5: LootTableLoader — JSON read/write + first-boot migration

**Files:**
- Modify: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt`

Add `loadAll(configDir): Map<KeyTier, LootTable>`. Behaviour:
1. For each tier: if `configDir/cobblemon-gacha/tables/<tier>.json` exists, read it via Gson.
2. Otherwise: read the bundled `tables/<tier>.csv` from the jar's resources, call `parseCsv`, write the result to JSON, return.
3. The on-disk JSON schema matches what Gson serialises from `LootTable` directly (no DTOs).

- [ ] **Step 1: Extend `LootTableLoader.kt` with `loadAll` and helpers**

Add the following at the end of the `object LootTableLoader` body (before the final `}`):

```kotlin
    private val gson: com.google.gson.Gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

    /**
     * Loads (or migrates) all three loot tables for the running mod.
     *
     * For each tier, prefers the on-disk JSON at `<configDir>/cobblemon-gacha/tables/<tier>.json`.
     * If that file is missing, parses the bundled CSV from the jar's resources and writes the
     * resulting `LootTable` to disk as JSON. Returns the in-memory map for runtime use.
     */
    fun loadAll(configDir: java.nio.file.Path): Map<KeyTier, LootTable> {
        val tablesDir = configDir.resolve("cobblemon-gacha").resolve("tables")
        java.nio.file.Files.createDirectories(tablesDir)
        val out = mutableMapOf<KeyTier, LootTable>()
        for (tier in KeyTier.entries) {
            val jsonFile = tablesDir.resolve("${tier.key}.json")
            val table = if (java.nio.file.Files.exists(jsonFile)) {
                loadJson(tier, jsonFile)
            } else {
                val csv = readBundledCsv(tier)
                val parsed = parseCsv(tier, csv)
                writeJson(parsed, jsonFile)
                CobblemonGacha.logger.info("Migrated bundled {} CSV to {}", tier.key, jsonFile)
                parsed
            }
            out[tier] = table
        }
        return out
    }

    private fun loadJson(tier: KeyTier, path: java.nio.file.Path): LootTable {
        return try {
            gson.fromJson(java.nio.file.Files.readString(path), LootTable::class.java)
        } catch (e: Exception) {
            CobblemonGacha.logger.error("Failed to read {} table json, falling back to bundled CSV", tier.key, e)
            val csv = readBundledCsv(tier)
            parseCsv(tier, csv)
        }
    }

    private fun writeJson(table: LootTable, path: java.nio.file.Path) {
        java.nio.file.Files.writeString(path, gson.toJson(table))
    }

    private fun readBundledCsv(tier: KeyTier): String {
        val resource = "/tables/${tier.key}.csv"
        val stream = LootTableLoader::class.java.getResourceAsStream(resource)
            ?: error("Bundled loot table resource not found: $resource")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
```

- [ ] **Step 2: Add a sanity-check round-trip test**

Add to `LootTableLoaderTest.kt`:

```kotlin
    @Test
    fun `parseCsv result round-trips through gson`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(table)
        val rebuilt = gson.fromJson(json, com.cobblemongacha.data.LootTable::class.java)
        assertEquals(table.entries.size, rebuilt.entries.size)
        assertEquals(table.totalWeightPct, rebuilt.totalWeightPct, 1e-9)
        assertEquals(table.entries[0].label, rebuilt.entries[0].label)
    }
```

Note: Gson cannot natively deserialise sealed-class polymorphism. The round-trip test passes only because `LootEntry.items` is empty in the rebuilt object (Gson serialises but can't reconstruct the sealed `ItemSpec` instances). The runtime path uses CSV→parse→JSON→reparse from CSV on next boot if needed. Document this in the function docs:

Add this Kdoc comment above `writeJson`:
```kotlin
    /**
     * NOTE: The Gson serialisation of `LootTable.entries[*].items` produces JSON that Gson cannot
     * round-trip back into the sealed `ItemSpec` hierarchy without a custom adapter. We accept this
     * because the JSON is intended for admin editing, not round-tripping — `loadJson` includes a
     * fall-back to the bundled CSV if deserialisation fails. A future improvement is to register a
     * `JsonDeserializer<ItemSpec>` that inspects a discriminator field; v1 keeps it simple.
     */
```

- [ ] **Step 3: Run tests**

```bash
cd cobblemon-gacha && ./gradlew test --no-daemon 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, 9 tests pass.

- [ ] **Step 4: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt cobblemon-gacha/src/test/kotlin/com/cobblemongacha/config/LootTableLoaderTest.kt
git commit -m "LootTableLoader.loadAll — JSON load + first-boot CSV migration"
```

---

## Task 6: ItemSpec deserialisation adapter

**Files:**
- Modify: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt`

Without an adapter, edited JSON can't be loaded back. Add a discriminator-based Gson adapter so the `ItemSpec` sealed type round-trips.

- [ ] **Step 1: Write the adapter**

Inside `object LootTableLoader`, replace the `private val gson` line and `loadJson`/`writeJson` with this richer setup:

```kotlin
    private val gson: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ItemSpec::class.java, ItemSpecAdapter)
            .create()
    }

    /**
     * Gson adapter that serialises the `ItemSpec` sealed hierarchy with a `type` discriminator.
     *
     *   ItemSpec.Vanilla     → { "type": "vanilla",     "id": "...", "count": N, "nameOverride": ..., "loreLines": [...] }
     *   ItemSpec.GachaKeyRef → { "type": "gacha_key",   "tier": "common"|"rare"|"ultra", "count": N }
     *   ItemSpec.Placeholder → { "type": "placeholder", "kind": "...", "label": "...", "count": N }
     */
    private object ItemSpecAdapter :
        com.google.gson.JsonSerializer<ItemSpec>, com.google.gson.JsonDeserializer<ItemSpec> {
        override fun serialize(src: ItemSpec, t: java.lang.reflect.Type, ctx: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
            val obj = com.google.gson.JsonObject()
            when (src) {
                is ItemSpec.Vanilla -> {
                    obj.addProperty("type", "vanilla")
                    obj.addProperty("id", src.id)
                    obj.addProperty("count", src.count)
                    src.nameOverride?.let { obj.addProperty("nameOverride", it) }
                    if (src.loreLines.isNotEmpty()) {
                        val arr = com.google.gson.JsonArray()
                        src.loreLines.forEach(arr::add)
                        obj.add("loreLines", arr)
                    }
                }
                is ItemSpec.GachaKeyRef -> {
                    obj.addProperty("type", "gacha_key")
                    obj.addProperty("tier", src.tier.key)
                    obj.addProperty("count", src.count)
                }
                is ItemSpec.Placeholder -> {
                    obj.addProperty("type", "placeholder")
                    obj.addProperty("kind", src.kind)
                    obj.addProperty("label", src.label)
                    obj.addProperty("count", src.count)
                }
            }
            return obj
        }

        override fun deserialize(json: com.google.gson.JsonElement, t: java.lang.reflect.Type, ctx: com.google.gson.JsonDeserializationContext): ItemSpec {
            val obj = json.asJsonObject
            return when (obj["type"].asString) {
                "vanilla" -> ItemSpec.Vanilla(
                    id = obj["id"].asString,
                    count = obj["count"].asInt,
                    nameOverride = obj["nameOverride"]?.takeIf { !it.isJsonNull }?.asString,
                    loreLines = obj["loreLines"]?.takeIf { !it.isJsonNull }?.asJsonArray?.map { it.asString } ?: emptyList(),
                )
                "gacha_key" -> ItemSpec.GachaKeyRef(
                    tier = KeyTier.fromKey(obj["tier"].asString) ?: error("unknown tier: ${obj["tier"]}"),
                    count = obj["count"].asInt,
                )
                "placeholder" -> ItemSpec.Placeholder(
                    kind = obj["kind"].asString,
                    label = obj["label"].asString,
                    count = obj["count"].asInt,
                )
                else -> error("unknown ItemSpec type: ${obj["type"]}")
            }
        }
    }
```

- [ ] **Step 2: Update the round-trip test to assert items survive**

Replace the round-trip test in `LootTableLoaderTest.kt` with:

```kotlin
    @Test
    fun `parseCsv result round-trips through full gson with ItemSpec adapter`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val json = LootTableLoader.toJson(table)
        val rebuilt = LootTableLoader.fromJson(json)
        assertEquals(table.entries.size, rebuilt.entries.size)
        assertEquals(table.totalWeightPct, rebuilt.totalWeightPct, 1e-9)
        // First entry should be a vanilla Poke Ball
        val ball = rebuilt.entries[0].items[0] as ItemSpec.Vanilla
        assertEquals("cobblemon:poke_ball", ball.id)
        assertEquals(20, ball.count)
        // Jackpot row should be a GachaKeyRef
        val key = rebuilt.entries[3].items[0] as ItemSpec.GachaKeyRef
        assertEquals(KeyTier.RARE, key.tier)
    }
```

- [ ] **Step 3: Expose `toJson` and `fromJson` on `LootTableLoader` for testing**

Add to `LootTableLoader`:

```kotlin
    /** Public test seam — serialises a LootTable to JSON using the configured adapter. */
    fun toJson(table: LootTable): String = gson.toJson(table)

    /** Public test seam — deserialises a LootTable from JSON using the configured adapter. */
    fun fromJson(json: String): LootTable = gson.fromJson(json, LootTable::class.java)
```

And update `loadJson`/`writeJson` to use these directly (they already do via `gson`).

- [ ] **Step 4: Run tests**

```bash
cd cobblemon-gacha && ./gradlew test --no-daemon 2>&1 | tail -10
```
Expected: all 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/LootTableLoader.kt cobblemon-gacha/src/test/kotlin/com/cobblemongacha/config/LootTableLoaderTest.kt
git commit -m "Add ItemSpecAdapter so loot tables can be JSON-edited and round-trip safely"
```

---

## Task 7: PlayerGachaData + PlayerGachaStore (JSON round-trip tested)

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/PlayerGachaData.kt`
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/PlayerGachaStore.kt`
- Create: `cobblemon-gacha/src/test/kotlin/com/cobblemongacha/data/PlayerGachaStoreTest.kt`

- [ ] **Step 1: Write `PlayerGachaData.kt`**

```kotlin
package com.cobblemongacha.data

/**
 * Per-player gacha record. `lastLoginGrantDate` and `lastRankedGrantDate` are `LocalDate.toString()`
 * values (yyyy-MM-dd) so each is a single field and they compare with `!=`. `null` means the player
 * has never received that grant.
 */
data class PlayerGachaData(
    var name: String,
    var lastLoginGrantDate: String? = null,
    var lastRankedGrantDate: String? = null,
)
```

- [ ] **Step 2: Write `PlayerGachaStore.kt`**

```kotlin
package com.cobblemongacha.data

import com.cobblemongacha.CobblemonGacha
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Per-player gacha state, persisted as `config/cobblemon-gacha/players.json`. Pattern mirrors
 * `cobblemon-ranked`'s `EloStore`: load on startup, mutate in-memory, call `save()` after each grant.
 */
class PlayerGachaStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-gacha").resolve("players.json")
    private val players: MutableMap<String, PlayerGachaData> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, PlayerGachaData>>() {}.type
            val loaded: MutableMap<String, PlayerGachaData> = gson.fromJson(file.readText(), type)
            players.clear()
            players.putAll(loaded)
        } catch (e: Exception) {
            CobblemonGacha.logger.error("Failed to load player gacha data", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-gacha").createDirectories()
        file.writeText(gson.toJson(players))
    }

    fun getOrCreate(uuid: UUID, name: String): PlayerGachaData =
        players.getOrPut(uuid.toString()) { PlayerGachaData(name = name) }.also {
            // Keep the cached name current so admin commands can resolve UUID → name from json alone
            if (it.name != name) it.name = name
        }

    fun get(uuid: UUID): PlayerGachaData? = players[uuid.toString()]

    fun getAll(): Map<String, PlayerGachaData> = players.toMap()
}
```

- [ ] **Step 3: Write the round-trip test**

```kotlin
package com.cobblemongacha.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class PlayerGachaStoreTest {

    @Test
    fun `round-trip preserves all fields`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        val data = store.getOrCreate(u, "SixthSense")
        data.lastLoginGrantDate = "2026-05-11"
        data.lastRankedGrantDate = "2026-05-10"
        store.save()

        val rehydrated = PlayerGachaStore(tmp)
        rehydrated.load()
        val loaded = rehydrated.get(u)!!
        assertEquals("SixthSense", loaded.name)
        assertEquals("2026-05-11", loaded.lastLoginGrantDate)
        assertEquals("2026-05-10", loaded.lastRankedGrantDate)
    }

    @Test
    fun `missing file is silent on load`(@TempDir tmp: Path) {
        val store = PlayerGachaStore(tmp)
        store.load() // no file
        assertNull(store.get(UUID.randomUUID()))
    }

    @Test
    fun `getOrCreate refreshes cached name`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        store.getOrCreate(u, "OldName")
        val refreshed = store.getOrCreate(u, "NewName")
        assertEquals("NewName", refreshed.name)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd cobblemon-gacha && ./gradlew test --no-daemon 2>&1 | tail -10
```
Expected: 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/PlayerGachaData.kt cobblemon-gacha/src/main/kotlin/com/cobblemongacha/data/PlayerGachaStore.kt cobblemon-gacha/src/test/kotlin/com/cobblemongacha/data/PlayerGachaStoreTest.kt
git commit -m "Add PlayerGachaData + PlayerGachaStore for per-player daily-grant tracking"
```

---

## Task 8: GachaConfig (crate coords, animation ticks)

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/GachaConfig.kt`

- [ ] **Step 1: Write `GachaConfig.kt`**

```kotlin
package com.cobblemongacha.config

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.KeyTier
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * World coordinate for a configured crate. `dim` is the registry id string for the dimension
 * (e.g. "minecraft:overworld"). All three crates start as `null` and are populated by the
 * `/gacha admin setcrate` command.
 */
data class CrateCoord(val x: Int, val y: Int, val z: Int, val dim: String)

/**
 * Server-wide gacha config: where the three crates are and how the rolling animation ticks.
 *
 * `animationTicks` is the gap (in server ticks, 20 = 1s) between successive marquee updates.
 * Length controls how many candidates the player sees; the values define a deceleration curve.
 * `jackpotHoldTicks` is how long the final reward sits in the centre slot before the menu closes.
 */
data class GachaConfig(
    val crates: MutableMap<String, CrateCoord?> = mutableMapOf(
        KeyTier.COMMON.key to null,
        KeyTier.RARE.key to null,
        KeyTier.ULTRA.key to null,
    ),
    val animationTicks: List<Int> = listOf(2, 2, 3, 3, 4, 5, 7, 10, 15),
    val jackpotHoldTicks: Int = 20,
) {
    fun crateOf(tier: KeyTier): CrateCoord? = crates[tier.key]

    fun setCrate(tier: KeyTier, coord: CrateCoord?) {
        crates[tier.key] = coord
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): GachaConfig {
            val file = configDir.resolve("cobblemon-gacha").resolve("config.json")
            if (!file.exists()) {
                val default = GachaConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), GachaConfig::class.java) ?: GachaConfig()
            } catch (e: Exception) {
                CobblemonGacha.logger.error("Failed to load gacha config, using defaults", e)
                GachaConfig()
            }
        }

        fun save(configDir: Path, config: GachaConfig) {
            val dir = configDir.resolve("cobblemon-gacha")
            dir.createDirectories()
            dir.resolve("config.json").writeText(gson.toJson(config))
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/config/GachaConfig.kt
git commit -m "Add GachaConfig (crate coords + animation tuning) with json persistence"
```

---

## Task 9: KeyItems builder (vanilla items + DataComponents tag)

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/item/KeyItems.kt`

A Common Key is a `trial_key` ItemStack with:
- `DataComponents.CUSTOM_NAME = "§eCommon Key"` (Component)
- `DataComponents.LORE = [...]`
- `DataComponents.CUSTOM_DATA = CustomData.of(CompoundTag.create("gacha_key" → "common"))`

The `gacha_key` tag is what `CrateInteractionHandler` looks for.

- [ ] **Step 1: Write `KeyItems.kt`**

```kotlin
package com.cobblemongacha.item

import com.cobblemongacha.data.KeyTier
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Builds keyed ItemStacks for the three gacha tiers. Keys are vanilla items (trial_key,
 * ominous_trial_key, nether_star) tagged with `custom_data { gacha_key = <tier.key> }` so
 * the crate interaction handler can recognise them without registering custom items.
 */
object KeyItems {

    private const val TAG_NAME = "gacha_key"

    /** Build a single Key ItemStack of [count]. */
    fun build(tier: KeyTier, count: Int = 1): ItemStack {
        val (item, displayName) = when (tier) {
            KeyTier.COMMON -> Items.TRIAL_KEY to Component.literal("§e§lCommon Key")
            KeyTier.RARE -> Items.OMINOUS_TRIAL_KEY to Component.literal("§5§lRare Key")
            KeyTier.ULTRA -> Items.NETHER_STAR to Component.literal("§6§lUltra Key")
        }
        val stack = ItemStack(item, count)
        stack.set(DataComponents.CUSTOM_NAME, displayName)
        val lore = listOf(
            Component.literal("§7Right-click the §f${tier.displayName} Crate §7at spawn"),
            Component.literal("§7to roll for a reward."),
        )
        stack.set(DataComponents.LORE, ItemLore(lore))
        val tag = CompoundTag()
        tag.putString(TAG_NAME, tier.key)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }

    /** Inverse of [build]: returns the tier encoded in the stack's custom_data, or null. */
    fun tierOf(stack: ItemStack): KeyTier? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        if (!tag.contains(TAG_NAME)) return null
        return KeyTier.fromKey(tag.getString(TAG_NAME))
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/item/KeyItems.kt
git commit -m "Add KeyItems.build / tierOf — vanilla items tagged via DataComponents.CUSTOM_DATA"
```

---

## Task 10: PlaceholderItems builder

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/item/PlaceholderItems.kt`

- [ ] **Step 1: Write `PlaceholderItems.kt`**

```kotlin
package com.cobblemongacha.item

import com.cobblemongacha.data.ItemSpec
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Builds placeholder ItemStacks for unimplemented gacha rewards (Pokemon eggs, vouchers,
 * blank Ultra rows). The base vanilla item is chosen by [ItemSpec.Placeholder.kind]:
 *   - "pokemon_egg" → minecraft:egg
 *   - "voucher"     → minecraft:filled_map
 *   - "tbd_ultra"   → minecraft:knowledge_book (anything else)
 *
 * The stack is tagged `custom_data { gacha_placeholder=true, placeholder_id=<kind>:<label> }`
 * so a future `migratePlaceholders` command can swap them for real items.
 */
object PlaceholderItems {

    fun build(spec: ItemSpec.Placeholder): ItemStack {
        val (item, color) = when (spec.kind) {
            "pokemon_egg" -> Items.EGG to "§a"
            "voucher" -> Items.FILLED_MAP to "§6"
            else -> Items.KNOWLEDGE_BOOK to "§7"
        }
        val stack = ItemStack(item, spec.count)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("$color${spec.label} §8(Placeholder)"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§8Stand-in until the real item ships."),
            Component.literal("§8Admins can swap via /gacha admin migratePlaceholders (future)."),
        )))
        val tag = CompoundTag().apply {
            putBoolean("gacha_placeholder", true)
            putString("placeholder_id", "${spec.kind}:${spec.label}")
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/item/PlaceholderItems.kt
git commit -m "Add PlaceholderItems — eggs, vouchers, TBD ultra rewards as themed vanilla items"
```

---

## Task 11: RewardRoller — weighted random selection

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/reward/RewardRoller.kt`
- Create: `cobblemon-gacha/src/test/kotlin/com/cobblemongacha/reward/RewardRollerTest.kt`

- [ ] **Step 1: Write the test first**

```kotlin
package com.cobblemongacha.reward

import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.LootTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RewardRollerTest {

    private fun mkEntry(label: String, weight: Double, tier: LootTier = LootTier.Floor) =
        LootEntry(tier, label, weight, listOf(ItemSpec.Vanilla("minecraft:stone", 1)))

    @Test
    fun `rolls only nonzero-weight entries`() {
        val table = LootTable(KeyTier.COMMON, 100.0, listOf(
            mkEntry("A", 0.0),
            mkEntry("B", 100.0),
        ))
        repeat(50) {
            val rolled = RewardRoller.roll(table, Random.Default)
            assertEquals("B", rolled.label, "0-weight entry must never be picked")
        }
    }

    @Test
    fun `deterministic with seeded random`() {
        val table = LootTable(KeyTier.COMMON, 100.0, listOf(
            mkEntry("A", 30.0),
            mkEntry("B", 70.0),
        ))
        val r1 = Random(42)
        val r2 = Random(42)
        repeat(20) {
            assertEquals(RewardRoller.roll(table, r1).label, RewardRoller.roll(table, r2).label)
        }
    }

    @Test
    fun `empirical distribution within tolerance over 100k rolls`() {
        val table = LootTable(KeyTier.COMMON, 100.0, listOf(
            mkEntry("A", 30.0),
            mkEntry("B", 70.0),
        ))
        val rand = Random(0)
        val counts = mutableMapOf<String, Int>()
        val n = 100_000
        repeat(n) {
            val label = RewardRoller.roll(table, rand).label
            counts.merge(label, 1) { a, _ -> a + 1 }
        }
        val pctA = (counts["A"] ?: 0).toDouble() / n
        val pctB = (counts["B"] ?: 0).toDouble() / n
        assertTrue(kotlin.math.abs(pctA - 0.30) < 0.01, "A pct $pctA")
        assertTrue(kotlin.math.abs(pctB - 0.70) < 0.01, "B pct $pctB")
    }

    @Test
    fun `throws if no positive-weight entries`() {
        val table = LootTable(KeyTier.COMMON, 0.0, listOf(mkEntry("A", 0.0)))
        try {
            RewardRoller.roll(table, Random.Default)
            assert(false) { "expected exception" }
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
```

- [ ] **Step 2: Run the test — should fail with unresolved RewardRoller**

```bash
cd cobblemon-gacha && ./gradlew compileTestKotlin --no-daemon 2>&1 | tail -5
```
Expected: error about `RewardRoller`.

- [ ] **Step 3: Write `RewardRoller.kt`**

```kotlin
package com.cobblemongacha.reward

import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import kotlin.random.Random

/**
 * Stateless weighted picker. Filters out 0-weight entries (which the loot table keeps for record
 * purposes), then picks one entry proportionally to `weightPct`. Random is injected so tests can
 * seed it for determinism.
 */
object RewardRoller {

    fun roll(table: LootTable, random: Random = Random.Default): LootEntry {
        val candidates = table.entries.filter { it.weightPct > 0.0 }
        check(candidates.isNotEmpty()) {
            "Loot table for ${table.tier.key} has no positive-weight entries — refusing to roll"
        }
        val total = candidates.sumOf { it.weightPct }
        val r = random.nextDouble() * total
        var acc = 0.0
        for (entry in candidates) {
            acc += entry.weightPct
            if (r < acc) return entry
        }
        // Floating-point safety net — return the last candidate.
        return candidates.last()
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd cobblemon-gacha && ./gradlew test --no-daemon 2>&1 | tail -10
```
Expected: all 16 tests pass.

- [ ] **Step 5: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/reward/RewardRoller.kt cobblemon-gacha/src/test/kotlin/com/cobblemongacha/reward/RewardRollerTest.kt
git commit -m "Add RewardRoller — weighted random pick from a LootTable"
```

---

## Task 12: RewardGranter — ItemSpec → ItemStack delivery

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/reward/RewardGranter.kt`

- [ ] **Step 1: Write `RewardGranter.kt`**

```kotlin
package com.cobblemongacha.reward

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.item.PlaceholderItems
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * Materialises a `LootEntry`'s `ItemSpec`s into `ItemStack`s and inserts them into the player's
 * inventory. If the inventory cannot hold a stack, the stack is dropped as an `ItemEntity` at the
 * player's feet. Returns the materialised stacks so callers (announcer) can describe what was given.
 */
object RewardGranter {

    fun grant(player: ServerPlayer, entry: LootEntry): List<ItemStack> {
        val stacks = entry.items.mapNotNull { materialize(it) }
        for (stack in stacks) {
            if (stack.isEmpty) continue
            if (!player.inventory.add(stack)) {
                // Drop at player's feet — vanilla loot fallback behaviour.
                val drop = ItemEntity(player.serverLevel(), player.x, player.y, player.z, stack)
                drop.setDefaultPickUpDelay()
                player.serverLevel().addFreshEntity(drop)
            }
        }
        return stacks
    }

    /**
     * Build the first representative ItemStack for an entry — used by OddsMenu to render the
     * "what does this entry give?" tile, and by RollMenu as the centre-slot reveal.
     */
    fun representative(entry: LootEntry): ItemStack {
        val first = entry.items.firstOrNull() ?: return ItemStack.EMPTY
        return materialize(first) ?: ItemStack.EMPTY
    }

    private fun materialize(spec: ItemSpec): ItemStack? = when (spec) {
        is ItemSpec.Vanilla -> {
            val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.id))
            if (item == net.minecraft.world.item.Items.AIR) {
                CobblemonGacha.logger.warn("Unknown item id in loot table: {}", spec.id)
                null
            } else ItemStack(item, spec.count)
        }
        is ItemSpec.GachaKeyRef -> KeyItems.build(spec.tier, spec.count)
        is ItemSpec.Placeholder -> PlaceholderItems.build(spec)
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/reward/RewardGranter.kt
git commit -m "Add RewardGranter — materialises ItemSpec→ItemStack and inserts/drops on the player"
```

---

## Task 13: PullAnnouncer (chat broadcast + sound + firework)

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/announce/PullAnnouncer.kt`

- [ ] **Step 1: Write `PullAnnouncer.kt`**

```kotlin
package com.cobblemongacha.announce

import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTier
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.core.component.DataComponents
import it.unimi.dsi.fastutil.ints.IntList

/**
 * Broadcasts a pull to the whole server, plays the appropriate sound at the puller, and spawns
 * a tier-coloured firework at the crate on jackpot pulls.
 *
 * Announcement format:
 *   Floor/Mid:  §7[Gacha] §a<Player>§7 opened a §f<Tier> Box §7and got §f<label>
 *   High:       Floor/Mid line + §6 (HIGH)
 *   Jackpot:    §e[Gacha] §6★ JACKPOT! §a<Player>§6 got §f<label> §6from a <Tier> Box ★
 */
object PullAnnouncer {

    fun broadcast(
        server: MinecraftServer,
        player: ServerPlayer,
        tier: KeyTier,
        entry: LootEntry,
        crateBlockPos: net.minecraft.core.BlockPos? = null,
    ) {
        val playerName = player.name.string
        val message = when (entry.lootTier) {
            LootTier.Floor, LootTier.Mid -> Component.literal(
                "§7[Gacha] §a$playerName§7 opened a §f${tier.displayName} Box §7and got §f${entry.label}"
            )
            LootTier.High -> Component.literal(
                "§7[Gacha] §a$playerName§7 opened a §f${tier.displayName} Box §7and got §f${entry.label}§6 (HIGH)"
            )
            LootTier.Jackpot -> Component.literal(
                "§e[Gacha] §6★ JACKPOT! §a$playerName§6 got §f${entry.label} §6from a ${tier.displayName} Box ★"
            )
        }
        server.playerList.broadcastSystemMessage(message, false)

        // Sound at the puller
        val sound = if (entry.lootTier == LootTier.Jackpot) SoundEvents.PLAYER_LEVELUP else SoundEvents.NOTE_BLOCK_PLING.value()
        player.serverLevel().playSound(
            null, player.x, player.y, player.z, sound, SoundSource.PLAYERS, 1.0f, 1.0f,
        )

        if (entry.lootTier == LootTier.Jackpot && crateBlockPos != null) {
            spawnFirework(server, player, tier, crateBlockPos)
        }
    }

    /**
     * Spawns a vanilla `FireworkRocketEntity` at the crate with a tier-coloured large-ball explosion
     * and a 1-tick fuse so it pops instantly. Colours: white (Common), red (Rare), purple (Ultra).
     */
    private fun spawnFirework(
        server: MinecraftServer,
        player: ServerPlayer,
        tier: KeyTier,
        pos: net.minecraft.core.BlockPos,
    ) {
        val color = when (tier) {
            KeyTier.COMMON -> 0xFFFFFF
            KeyTier.RARE -> 0xCC2222
            KeyTier.ULTRA -> 0x8B00FF
        }
        val rocket = ItemStack(Items.FIREWORK_ROCKET)
        val explosion = FireworkExplosion(
            FireworkExplosion.Shape.LARGE_BALL,
            IntList.of(color),
            IntList.of(),
            /*hasTrail*/ true,
            /*hasTwinkle*/ true,
        )
        val fireworks = Fireworks(/*flightDuration*/ 1, listOf(explosion))
        rocket.set(DataComponents.FIREWORKS, fireworks)
        val level = player.serverLevel()
        val entity = FireworkRocketEntity(level, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, rocket)
        level.addFreshEntity(entity)
        // Sparkle near the player as well
        level.sendParticles(ParticleTypes.FIREWORK, player.x, player.y + 1.0, player.z, 20, 0.4, 0.4, 0.4, 0.0)
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/announce/PullAnnouncer.kt
git commit -m "Add PullAnnouncer — tier-aware chat broadcast, sound, jackpot firework"
```

---

## Task 14: TickScheduler util

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/util/TickScheduler.kt`

- [ ] **Step 1: Write `TickScheduler.kt`**

```kotlin
package com.cobblemongacha.util

import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple delayed-runnable scheduler. Hooked from `CobblemonGacha.onServerTickPost` once on
 * startup; tasks are evaluated each server tick (50 ms at 20 TPS).
 *
 * Tasks may cancel via the returned `Cancellable` — useful when the player closes the rolling
 * menu early and we want to drop any pending animation updates.
 */
object TickScheduler {

    fun interface Cancellable { fun cancel() }
    private data class Task(val dueTick: Long, val run: () -> Unit, var cancelled: Boolean = false)

    private val tasks = ConcurrentLinkedQueue<Task>()
    private val tickCounter = AtomicLong(0)

    /** Schedule [run] to fire after [ticks] server ticks. */
    fun later(ticks: Int, run: () -> Unit): Cancellable {
        val task = Task(tickCounter.get() + ticks.toLong(), run)
        tasks.add(task)
        return Cancellable { task.cancelled = true }
    }

    /** Schedule a series of tasks at cumulative intervals. */
    fun chain(intervals: List<Int>, stepRun: (index: Int) -> Unit, finalRun: () -> Unit): Cancellable {
        var running = true
        var cumulative = 0
        val cancels = mutableListOf<Cancellable>()
        for ((i, interval) in intervals.withIndex()) {
            cumulative += interval
            cancels.add(later(cumulative) { if (running) stepRun(i) })
        }
        cancels.add(later(cumulative) { if (running) finalRun() })
        return Cancellable {
            running = false
            cancels.forEach { it.cancel() }
        }
    }

    /** Called by the mod entry once per server tick. */
    fun onServerTickPost(@Suppress("UNUSED_PARAMETER") event: ServerTickEvent.Post) {
        val now = tickCounter.incrementAndGet()
        val iter = tasks.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            if (t.dueTick <= now) {
                iter.remove()
                if (!t.cancelled) {
                    try { t.run() } catch (e: Throwable) {
                        org.slf4j.LoggerFactory.getLogger("cobblemon-gacha/tick").error("TickScheduler task threw", e)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/util/TickScheduler.kt
git commit -m "Add TickScheduler — delayed-runnable queue driven by ServerTickEvent.Post"
```

---

## Task 15: GachaMenuRegistry + RollMenu + OddsMenu

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/GachaMenuRegistry.kt`
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/RollMenu.kt`
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/OddsMenu.kt`

`RollMenu` and `OddsMenu` are read-only chest menus — slots reject pickup/place. They share a base class via composition (each registers its own MenuType for client-side stub creation).

- [ ] **Step 1: Write `GachaMenuRegistry.kt`**

```kotlin
package com.cobblemongacha.gui

import com.cobblemongacha.CobblemonGacha
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object GachaMenuRegistry {

    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CobblemonGacha.MOD_ID)

    val ROLL: DeferredHolder<MenuType<*>, MenuType<RollMenu>> =
        MENUS.register("roll") { ->
            IMenuTypeExtension.create<RollMenu> { id, inv, _ -> RollMenu.clientStub(id, inv) }
        }

    val ODDS: DeferredHolder<MenuType<*>, MenuType<OddsMenu>> =
        MENUS.register("odds") { ->
            IMenuTypeExtension.create<OddsMenu> { id, inv, _ -> OddsMenu.clientStub(id, inv) }
        }
}
```

- [ ] **Step 2: Write `RollMenu.kt`**

```kotlin
package com.cobblemongacha.gui

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.announce.PullAnnouncer
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.reward.RewardGranter
import com.cobblemongacha.reward.RewardRoller
import com.cobblemongacha.util.TickScheduler
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 9-slot read-only menu used for the rolling animation. The reward is decided up front and
 * passed in — the animation is purely cosmetic. Slots 0 and 8 are tier-coloured glass borders.
 * Slot 4 (centre) cycles through random candidates per the configured tick intervals before
 * settling on the decided reward.
 *
 * State lives in `activeRolls` keyed by player UUID so close/disconnect can finalise gracefully.
 */
class RollMenu(
    syncId: Int,
    private val playerInventory: Inventory,
    private val display: SimpleContainer,
) : AbstractContainerMenu(GachaMenuRegistry.ROLL.get(), syncId) {

    init {
        for (i in 0 until 9) {
            addSlot(object : Slot(display, i, 8 + i * 18, 18) {
                override fun mayPickup(player: Player) = false
                override fun mayPlace(stack: ItemStack) = false
            })
        }
        // Player inventory + hotbar (visible, inert)
        for (i in 0 until 3) for (j in 0 until 9) {
            addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 50 + i * 18))
        }
        for (i in 0 until 9) addSlot(Slot(playerInventory, i, 8 + i * 18, 108))
    }

    override fun quickMoveStack(player: Player, slot: Int): ItemStack = ItemStack.EMPTY
    override fun stillValid(player: Player) = true

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger("cobblemon-gacha/roll")

        /** Bookkeeping per active roll. */
        private data class RollState(
            val tier: KeyTier,
            val decided: LootEntry,
            val display: SimpleContainer,
            val cratePos: BlockPos?,
            var animation: TickScheduler.Cancellable? = null,
            var finalized: Boolean = false,
        )

        private val activeRolls = ConcurrentHashMap<UUID, RollState>()

        /**
         * Client-side stub used when the vanilla client opens the menu. The client doesn't
         * touch the container directly — server sets the slot contents and they get synced.
         */
        fun clientStub(syncId: Int, inv: Inventory): RollMenu =
            RollMenu(syncId, inv, SimpleContainer(9))

        /**
         * Open the menu and kick off the animation. The reward is rolled here and stored so
         * close/disconnect can deliver it deterministically.
         */
        fun openFor(player: ServerPlayer, tier: KeyTier, table: LootTable, cratePos: BlockPos?) {
            val decided = RewardRoller.roll(table)
            val container = SimpleContainer(9)
            val borderColor = tierBorder(tier)
            container.setItem(0, borderColor); container.setItem(8, borderColor)
            val state = RollState(tier, decided, container, cratePos)
            activeRolls[player.uuid] = state

            val provider = object : MenuProvider {
                override fun getDisplayName(): Component =
                    Component.literal("§e${tier.displayName} Box — §6Rolling…")
                override fun createMenu(syncId: Int, inv: Inventory, p: Player): AbstractContainerMenu =
                    RollMenu(syncId, inv, container)
            }
            player.openMenu(provider)

            val intervals = CobblemonGacha.config.animationTicks
            // Build candidate sequence — random samples from the table plus the decided reward last.
            val candidatePool = table.entries.filter { it.weightPct > 0.0 }
            val random = Random.Default
            val sequence = List(intervals.size - 1) { candidatePool.random(random) } + decided

            state.animation = TickScheduler.chain(
                intervals = intervals,
                stepRun = { i ->
                    val entry = sequence.getOrNull(i) ?: return@chain
                    val stack = RewardGranter.representative(entry)
                    container.setItem(4, stack)
                },
                finalRun = {
                    container.setItem(4, RewardGranter.representative(decided))
                    TickScheduler.later(CobblemonGacha.config.jackpotHoldTicks) {
                        finalise(player.uuid, player)
                    }
                },
            )
        }

        /**
         * Finalise the roll for the given player. Idempotent — safe to call from animation end,
         * container-close handler, or PlayerLoggedOutEvent. Performs grant + announce exactly once.
         */
        fun finalise(uuid: UUID, player: ServerPlayer?) {
            val state = activeRolls.remove(uuid) ?: return
            if (state.finalized) return
            state.finalized = true
            state.animation?.cancel()
            if (player == null) {
                // Player went offline before finalise — drop the reward into their inventory snapshot
                // via the playerList save. We can't access the offline ServerPlayer here, so log and
                // accept the rare loss. (See spec: vanilla player save persists inventory mutations
                // captured before the LOGGED_OUT event completes.)
                log.warn("Player {} disconnected during roll; reward dropped", uuid)
                return
            }
            // Close the menu if it's still open
            if (player.containerMenu is RollMenu) player.closeContainer()
            RewardGranter.grant(player, state.decided)
            PullAnnouncer.broadcast(player.server, player, state.tier, state.decided, state.cratePos)
        }

        /** Called by the close-event listener registered in CobblemonGacha. */
        fun onPlayerClosedContainer(player: ServerPlayer) {
            finalise(player.uuid, player)
        }

        /** Called by the logged-out-event listener; player may already be partly torn down. */
        fun onPlayerLoggedOut(player: ServerPlayer) {
            finalise(player.uuid, player)
        }

        private fun tierBorder(tier: KeyTier): ItemStack {
            val item = when (tier) {
                KeyTier.COMMON -> Items.WHITE_STAINED_GLASS_PANE
                KeyTier.RARE -> Items.RED_STAINED_GLASS_PANE
                KeyTier.ULTRA -> Items.BLACK_STAINED_GLASS_PANE
            }
            val stack = ItemStack(item)
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§7${tier.displayName} Box"))
            return stack
        }
    }
}
```

- [ ] **Step 3: Write `OddsMenu.kt`**

```kotlin
package com.cobblemongacha.gui

import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.reward.RewardGranter
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/**
 * Read-only preview of a loot table's possible rewards. One slot per entry (up to 27); each
 * slot is the entry's representative ItemStack with appended lore showing tier and percentage.
 */
class OddsMenu(
    syncId: Int,
    private val playerInventory: Inventory,
    display: SimpleContainer,
) : AbstractContainerMenu(GachaMenuRegistry.ODDS.get(), syncId) {

    init {
        val rows = display.containerSize / 9
        for (row in 0 until rows) for (col in 0 until 9) {
            addSlot(object : Slot(display, col + row * 9, 8 + col * 18, 18 + row * 18) {
                override fun mayPickup(player: Player) = false
                override fun mayPlace(stack: ItemStack) = false
            })
        }
        // Player inventory + hotbar
        val topY = 18 + rows * 18 + 14
        for (i in 0 until 3) for (j in 0 until 9) {
            addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, topY + i * 18))
        }
        for (i in 0 until 9) addSlot(Slot(playerInventory, i, 8 + i * 18, topY + 58))
    }

    override fun quickMoveStack(player: Player, slot: Int): ItemStack = ItemStack.EMPTY
    override fun stillValid(player: Player) = true

    companion object {
        fun clientStub(syncId: Int, inv: Inventory): OddsMenu = OddsMenu(syncId, inv, SimpleContainer(9))

        fun openFor(player: ServerPlayer, tier: KeyTier, table: LootTable) {
            val nonZero = table.entries.filter { it.weightPct > 0.0 }
            val rows = ((nonZero.size + 8) / 9).coerceAtMost(3).coerceAtLeast(1)
            val cap = rows * 9
            val display = SimpleContainer(cap)
            nonZero.take(cap).forEachIndexed { i, entry ->
                val stack = RewardGranter.representative(entry).copy()
                if (stack.isEmpty) return@forEachIndexed
                val newName = "${tierColor(entry.lootTier.name)}${entry.label}"
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(newName))
                val lore = mutableListOf(
                    Component.literal("§7Tier: §f${entry.lootTier.name}"),
                    Component.literal("§7Chance: §a${"%.1f".format(entry.weightPct)}%"),
                )
                if (entry.notes.isNotBlank()) lore += Component.literal("§8${entry.notes}")
                stack.set(DataComponents.LORE, ItemLore(lore))
                display.setItem(i, stack)
            }
            val provider = object : MenuProvider {
                override fun getDisplayName(): Component =
                    Component.literal("§e[${tier.displayName} Box] §7Possible Rewards")
                override fun createMenu(syncId: Int, inv: Inventory, p: Player): AbstractContainerMenu =
                    OddsMenu(syncId, inv, display)
            }
            player.openMenu(provider)
        }

        private fun tierColor(name: String): String = when (name) {
            "Floor" -> "§7"
            "Mid" -> "§b"
            "High" -> "§6"
            "Jackpot" -> "§d"
            else -> "§f"
        }
    }
}
```

- [ ] **Step 4: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`. (`RollMenu.openFor` references `CobblemonGacha.config` and `CobblemonGacha.tables` which will be added in Task 17.)

If compilation fails because `CobblemonGacha.config` / `CobblemonGacha.tables` don't exist yet, temporarily wrap the access in `try/catch` or stub them in CobblemonGacha now (will be properly wired in Task 17). The intent is the GUI files should compile.

If you need to stub: add to `CobblemonGacha` companion:
```kotlin
        lateinit var config: com.cobblemongacha.config.GachaConfig
        lateinit var tables: Map<com.cobblemongacha.data.KeyTier, com.cobblemongacha.data.LootTable>
```
(Re-running compile should now succeed.)

- [ ] **Step 5: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/gui/ cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt
git commit -m "Add GachaMenuRegistry, RollMenu (animated), OddsMenu (read-only preview)"
```

---

## Task 16: CrateInteractionHandler + KeyGrantHooks

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/interaction/CrateInteractionHandler.kt`
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/interaction/KeyGrantHooks.kt`

- [ ] **Step 1: Write `CrateInteractionHandler.kt`**

```kotlin
package com.cobblemongacha.interaction

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.gui.OddsMenu
import com.cobblemongacha.gui.RollMenu
import com.cobblemongacha.item.KeyItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Listens to right-click-on-block. If the targeted block is a configured crate coord, cancels
 * the underlying interaction (so chests don't open) and routes to RollMenu or OddsMenu based
 * on whether the player holds a matching key.
 */
object CrateInteractionHandler {

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        val pos = event.pos
        val dimId = player.serverLevel().dimension().location().toString()
        val tier = matchedCrateTier(pos, dimId) ?: return

        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS

        val held = player.mainHandItem
        val heldTier = KeyItems.tierOf(held)
        val table = CobblemonGacha.tables[tier] ?: return

        if (heldTier == tier) {
            held.shrink(1)
            RollMenu.openFor(player, tier, table, pos)
        } else {
            OddsMenu.openFor(player, tier, table)
        }
    }

    /** Returns the `KeyTier` whose configured coord matches [pos] in [dimId], or null. */
    private fun matchedCrateTier(pos: BlockPos, dimId: String): KeyTier? {
        for (tier in KeyTier.entries) {
            val crate = CobblemonGacha.config.crateOf(tier) ?: continue
            if (crate.x == pos.x && crate.y == pos.y && crate.z == pos.z && crate.dim == dimId) {
                return tier
            }
        }
        return null
    }
}
```

- [ ] **Step 2: Write `KeyGrantHooks.kt`**

```kotlin
package com.cobblemongacha.interaction

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.reward.RewardGranter
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.time.LocalDate

/**
 * Wires the two daily key grants:
 *   - Login: PlayerEvent.PlayerLoggedInEvent (NeoForge bus)
 *   - First PvP ranked win of the day: CobblemonEvents.BATTLE_VICTORY (Cobblemon bus)
 *
 * Each grant is gated on `lastLoginGrantDate` / `lastRankedGrantDate` matching today.
 */
object KeyGrantHooks {

    fun registerCobblemonHooks() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            val winners = event.winners.toList()
            val actors = event.battle.actors.toList()
            val playerActors = actors.filter { it is PlayerBattleActor && it.type == ActorType.PLAYER }
            if (playerActors.size < 2) return@subscribe   // not PvP
            for (actor in winners) {
                if (actor !is PlayerBattleActor) continue
                for (uuid in actor.playerUUIDs) {
                    val player = event.battle.players.firstOrNull { it.uuid == uuid } as? ServerPlayer ?: continue
                    tryGrantRanked(player)
                }
            }
        }
    }

    @SubscribeEvent
    fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        tryGrantLogin(player)
    }

    private fun tryGrantLogin(player: ServerPlayer) {
        val today = LocalDate.now().toString()
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.lastLoginGrantDate == today) return
        data.lastLoginGrantDate = today
        CobblemonGacha.playerStore.save()
        val stack = KeyItems.build(KeyTier.COMMON)
        if (!player.inventory.add(stack)) {
            // Drop at feet if full
            player.drop(stack, false)
        }
        player.sendSystemMessage(Component.literal("§e[Gacha] Daily login bonus: §6+1 Common Key"))
        CobblemonGacha.logger.info("Granted login key to {}", player.name.string)
    }

    private fun tryGrantRanked(player: ServerPlayer) {
        val today = LocalDate.now().toString()
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.lastRankedGrantDate == today) return
        data.lastRankedGrantDate = today
        CobblemonGacha.playerStore.save()
        val stack = KeyItems.build(KeyTier.COMMON)
        if (!player.inventory.add(stack)) player.drop(stack, false)
        player.sendSystemMessage(Component.literal("§e[Gacha] First ranked win today: §6+1 Common Key"))
        CobblemonGacha.logger.info("Granted ranked key to {}", player.name.string)
    }
}
```

- [ ] **Step 3: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`. The `playerStore` reference on `CobblemonGacha` will be added in Task 17 — if compile fails, add `lateinit var playerStore: com.cobblemongacha.data.PlayerGachaStore` to `CobblemonGacha.companion`.

- [ ] **Step 4: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/interaction/ cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt
git commit -m "Add CrateInteractionHandler (right-click router) + KeyGrantHooks (login + ranked daily)"
```

---

## Task 17: GachaCommands (admin + user)

**Files:**
- Create: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/commands/GachaCommands.kt`

- [ ] **Step 1: Write `GachaCommands.kt`**

```kotlin
package com.cobblemongacha.commands

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.announce.PullAnnouncer
import com.cobblemongacha.config.GachaConfig
import com.cobblemongacha.config.CrateCoord
import com.cobblemongacha.config.LootTableLoader
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.gui.OddsMenu
import com.cobblemongacha.gui.RollMenu
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.reward.RewardGranter
import com.cobblemongacha.reward.RewardRoller
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths

object GachaCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("gacha")
                .executes { ctx -> showHelp(ctx.source, ctx.source.hasPermission(4)); 1 }
                .then(Commands.literal("help")
                    .executes { ctx -> showHelp(ctx.source, ctx.source.hasPermission(4)); 1 })
                .then(Commands.literal("version")
                    .executes { ctx ->
                        val v = ModList.get().getModContainerById(CobblemonGacha.MOD_ID)
                            .map { it.modInfo.version.toString() }.orElse("unknown")
                        ctx.source.sendSystemMessage(Component.literal("[Gacha] Cobblemon Gacha v$v"))
                        1
                    })
                .then(Commands.literal("odds")
                    .then(Commands.argument("tier", StringArgumentType.string())
                        .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                        .executes { ctx ->
                            val sp = ctx.source.playerOrException
                            val tier = KeyTier.fromKey(StringArgumentType.getString(ctx, "tier"))
                            if (tier == null) {
                                ctx.source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier"))
                                return@executes 0
                            }
                            val table = CobblemonGacha.tables[tier] ?: return@executes 0
                            OddsMenu.openFor(sp, tier, table); 1
                        })
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("tier", StringArgumentType.string())
                                .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                                .executes { ctx -> adminGrant(ctx.source, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tier"), 1) }
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes { ctx -> adminGrant(ctx.source, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tier"), IntegerArgumentType.getInteger(ctx, "count")) })
                            )
                        )
                    )
                    .then(Commands.literal("setcrate")
                        .then(Commands.argument("tier", StringArgumentType.string())
                            .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                            .executes { ctx -> adminSetCrate(ctx.source, StringArgumentType.getString(ctx, "tier")) })
                    )
                    .then(Commands.literal("clearcrate")
                        .then(Commands.argument("tier", StringArgumentType.string())
                            .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                            .executes { ctx -> adminClearCrate(ctx.source, StringArgumentType.getString(ctx, "tier")) })
                    )
                    .then(Commands.literal("force")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("tier", StringArgumentType.string())
                                .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                                .executes { ctx -> adminForce(ctx.source, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tier")) })
                        )
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx -> adminReload(ctx.source) })
                )
        )
    }

    private fun showHelp(source: CommandSourceStack, includeAdmin: Boolean) {
        val lines = mutableListOf(
            "§e[Gacha] §fCommands:",
            "§7  /gacha odds <common|rare|ultra> §f— preview the rewards in a box",
            "§7  /gacha version §f— mod version",
        )
        if (includeAdmin) {
            lines += listOf(
                "§e[Gacha] §fAdmin (op level 4):",
                "§7  /gacha admin grant <player> <tier> [count] §f— give keys",
                "§7  /gacha admin setcrate <tier> §f— bind your targeted block as that tier's crate",
                "§7  /gacha admin clearcrate <tier> §f— unbind a crate",
                "§7  /gacha admin force <player> <tier> §f— roll without consuming a key",
                "§7  /gacha admin reload §f— reload config + tables from disk",
            )
        }
        lines.forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    private fun adminGrant(source: CommandSourceStack, target: net.minecraft.server.level.ServerPlayer, tierStr: String, count: Int): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        val stack = KeyItems.build(tier, count)
        if (!target.inventory.add(stack)) target.drop(stack, false)
        target.sendSystemMessage(Component.literal("§a[Gacha] You received §6$count ${tier.displayName} Key${if (count == 1) "" else "s"} §afrom an admin"))
        source.sendSystemMessage(Component.literal("§a[Gacha] Gave ${target.name.string} $count ${tier.displayName} Key${if (count == 1) "" else "s"}"))
        return 1
    }

    private fun adminSetCrate(source: CommandSourceStack, tierStr: String): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Must be run by a player (stand at the crate)")); return 0
        }
        val hit = player.pick(6.0, 0.0f, false)
        if (hit !is BlockHitResult || hit.type == HitResult.Type.MISS) {
            source.sendSystemMessage(Component.literal("§c[Gacha] Look at the crate block first (within 6 blocks)")); return 0
        }
        val pos = hit.blockPos
        val dim = player.serverLevel().dimension().location().toString()
        CobblemonGacha.config.setCrate(tier, CrateCoord(pos.x, pos.y, pos.z, dim))
        GachaConfig.save(FMLPaths.CONFIGDIR.get(), CobblemonGacha.config)
        source.sendSystemMessage(Component.literal("§a[Gacha] ${tier.displayName} crate bound to (${pos.x}, ${pos.y}, ${pos.z}) in $dim"))
        return 1
    }

    private fun adminClearCrate(source: CommandSourceStack, tierStr: String): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        CobblemonGacha.config.setCrate(tier, null)
        GachaConfig.save(FMLPaths.CONFIGDIR.get(), CobblemonGacha.config)
        source.sendSystemMessage(Component.literal("§a[Gacha] Cleared ${tier.displayName} crate binding"))
        return 1
    }

    private fun adminForce(source: CommandSourceStack, target: net.minecraft.server.level.ServerPlayer, tierStr: String): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        val table = CobblemonGacha.tables[tier] ?: return 0
        RollMenu.openFor(target, tier, table, CobblemonGacha.config.crateOf(tier)?.let {
            net.minecraft.core.BlockPos(it.x, it.y, it.z)
        })
        source.sendSystemMessage(Component.literal("§a[Gacha] Forced ${tier.displayName} roll for ${target.name.string}"))
        return 1
    }

    private fun adminReload(source: CommandSourceStack): Int {
        val dir = FMLPaths.CONFIGDIR.get()
        CobblemonGacha.config = GachaConfig.load(dir)
        CobblemonGacha.tables = LootTableLoader.loadAll(dir)
        source.sendSystemMessage(Component.literal("§a[Gacha] Reloaded config + ${CobblemonGacha.tables.size} tables"))
        return 1
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd cobblemon-gacha && ./gradlew compileKotlin --no-daemon 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/commands/GachaCommands.kt
git commit -m "Add GachaCommands — grant, setcrate, clearcrate, force, reload, odds, version"
```

---

## Task 18: Wire everything in CobblemonGacha entry

**Files:**
- Modify: `cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt`

- [ ] **Step 1: Rewrite `CobblemonGacha.kt` with full wiring**

```kotlin
package com.cobblemongacha

import com.cobblemongacha.commands.GachaCommands
import com.cobblemongacha.config.GachaConfig
import com.cobblemongacha.config.LootTableLoader
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.PlayerGachaStore
import com.cobblemongacha.gui.GachaMenuRegistry
import com.cobblemongacha.gui.RollMenu
import com.cobblemongacha.interaction.CrateInteractionHandler
import com.cobblemongacha.interaction.KeyGrantHooks
import com.cobblemongacha.util.TickScheduler
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonGacha.MOD_ID)
class CobblemonGacha(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Gacha initializing...")

        val configDir = FMLPaths.CONFIGDIR.get()
        config = GachaConfig.load(configDir)
        tables = LootTableLoader.loadAll(configDir)
        playerStore = PlayerGachaStore(configDir)
        playerStore.load()

        // Register MenuTypes on the mod bus
        GachaMenuRegistry.MENUS.register(modBus)

        // Cobblemon battle hook
        KeyGrantHooks.registerCobblemonHooks()

        // NeoForge game event subscribers
        NeoForge.EVENT_BUS.register(CrateInteractionHandler)
        NeoForge.EVENT_BUS.register(KeyGrantHooks)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)
        NeoForge.EVENT_BUS.addListener(::onContainerClose)
        NeoForge.EVENT_BUS.addListener(::onLoggedOut)

        logger.info("Cobblemon Gacha initialized — ${tables.size} tables, ${playerStore.getAll().size} players loaded")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        GachaCommands.register(event.dispatcher)
    }

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        TickScheduler.onServerTickPost(event)
    }

    private fun onContainerClose(event: PlayerContainerEvent.Close) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.containerMenu is RollMenu) RollMenu.onPlayerClosedContainer(player)
    }

    private fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        RollMenu.onPlayerLoggedOut(player)
        playerStore.save()
    }

    companion object {
        const val MOD_ID = "cobblemon_gacha"
        const val PERSISTENCE_DIR_NAME = "cobblemon-gacha"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: GachaConfig
        lateinit var tables: Map<KeyTier, LootTable>
        lateinit var playerStore: PlayerGachaStore
    }
}
```

- [ ] **Step 2: Build the full mod**

```bash
cd cobblemon-gacha && ./gradlew build --no-daemon 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`. All tests pass.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-gacha/src/main/kotlin/com/cobblemongacha/CobblemonGacha.kt
git commit -m "Wire CobblemonGacha entry — config + tables + store, command + event hooks"
```

---

## Task 19: Deploy + RCON-verify the full flow

**Files:** none (deployment / smoke test)

- [ ] **Step 1: Copy the jar onto the running server (server must be stopped or restart after copy)**

```bash
cp cobblemon-gacha/build/libs/cobblemon-gacha-1.0.0.jar cobblemon-server/mods/
```

- [ ] **Step 2: Ask the user to start (or restart) the server via `./run.sh` in their terminal**

Wait for RCON to come up:

```bash
until lsof -iTCP:25575 -P 2>/dev/null | grep -q LISTEN; do sleep 5; done
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "gacha version"
```

Expected: `[Gacha] Cobblemon Gacha v1.0.0`

- [ ] **Step 3: Verify table migration ran**

```bash
ls cobblemon-server/config/cobblemon-gacha/
cat cobblemon-server/config/cobblemon-gacha/config.json
head -30 cobblemon-server/config/cobblemon-gacha/tables/common.json
```

Expected: `config.json`, `players.json` (maybe absent until first interaction), and `tables/{common,rare,ultra}.json`.

- [ ] **Step 4: Have the user log in (any IGN — server can find them)**

Then test the daily login grant:

```bash
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "list"
```

Confirm the user received a Common Key (they should see a chat message and have a `trial_key` in their hotbar).

- [ ] **Step 5: Test the odds preview**

```bash
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "gacha odds common"
```

(The OddsMenu should open on the user's screen — confirm visually with them.)

- [ ] **Step 6: Bind a crate and test the full roll flow**

Have the user stand in front of any block (e.g., a chest they place). Then:

```bash
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "execute as <IGN> run gacha admin setcrate common"
```

Then the user right-clicks the block with their key in hand. Confirm:
1. Animation runs (~4s of items rolling in slot 4).
2. Menu closes, reward appears in inventory.
3. Chat broadcast appears for everyone.
4. If jackpot: firework spawns at crate, level-up sound plays.

- [ ] **Step 7: Test admin force**

```bash
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "gacha admin force <IGN> common"
```

Confirm a roll fires without consuming a key.

- [ ] **Step 8: Test admin grant**

```bash
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "gacha admin grant <IGN> rare 3"
```

Confirm user gets 3 `ominous_trial_key` items named "Rare Key" with the right lore.

- [ ] **Step 9: Test reload**

Edit `cobblemon-server/config/cobblemon-gacha/tables/common.json` — change a weightPct. Then:

```bash
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "gacha admin reload"
python3 /tmp/rcon_send.py localhost 25575 test-rcon-pw "gacha odds common"
```

Confirm the changed odds appear in the preview.

- [ ] **Step 10: Commit (if any tweaks were needed during testing)**

If everything works, no further commits. If issues found, fix them and commit the fixes individually using the same task pattern.

---

## Self-review checklist

Run these against the spec before declaring the plan complete:

1. **Daily login grant** → Task 16 (`KeyGrantHooks.tryGrantLogin`) ✓
2. **Daily ranked grant** → Task 16 (`KeyGrantHooks.registerCobblemonHooks`) ✓
3. **Admin grant command** → Task 17 ✓
4. **Crate right-click routing (with/without key)** → Task 16 (`CrateInteractionHandler`) ✓
5. **Rolling chest GUI** → Task 15 (`RollMenu`) ✓
6. **Odds preview GUI (read-only)** → Task 15 (`OddsMenu`) ✓
7. **Server-wide announcement with tier tag** → Task 13 (`PullAnnouncer`) ✓
8. **Firework + sound on jackpot** → Task 13 (`PullAnnouncer.spawnFirework`) ✓
9. **CSV → JSON migration** → Tasks 4–6 (`LootTableLoader`) ✓
10. **Pokémon-egg / voucher / TBD placeholders** → Task 10 (`PlaceholderItems`) ✓
11. **Persistence (config, players, tables)** → Tasks 5, 7, 8 ✓
12. **Commands** → Task 17 ✓
13. **Unit tests** → Tasks 4, 5, 6, 7, 11 ✓
14. **Server-only manifest** → Task 1 (`displayTest = IGNORE_ALL_VERSION`) ✓
15. **Cobblemon battle event coupling defensive** → Task 16 (Cobblemon event subscription) — note: if Cobblemon ever renames `BATTLE_VICTORY`, the mod fails to load with a clear error. Acceptable for v1.

---

## Notes for the implementer

- Always run `./gradlew test` after touching `LootTableLoader`, `RewardRoller`, or `PlayerGachaStore` — these are the only files with unit tests.
- The synthetic-class issue from `cobblemon-ranked`'s leaderboard (Kotlin's `sortedByDescending` failing under Sinytra Connector) does not apply here because this mod runs on NeoForge directly — Connector is only involved for Cobblemon Economy in the market mod. Still, avoid inline `sortedByDescending` in hot paths as a precaution; prefer explicit `Comparator { a, b -> ... }`.
- `RollMenu.openFor` calls `CobblemonGacha.config.animationTicks` — if you want to tune the animation duration, edit `cobblemon-server/config/cobblemon-gacha/config.json` then `/gacha admin reload`.
- The CSV item-name map in `LootTableLoader.KNOWN_ITEMS` is incomplete by design. When the actual Cobblemon item ids land (e.g., for Society Sunlit's specific evolution stones), extend the map and bump the version.
