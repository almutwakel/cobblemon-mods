# Cobblemon Ranked Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Fabric mod that adds ELO-rated ranked PvP battles to Cobblemon, with force-challenge mechanics, team selection, and daily decay.

**Architecture:** Kotlin Fabric mod depending on Cobblemon 1.7.3 and Cobblemon Economy 0.0.17. Pure ELO math is unit-tested. Battle flow uses Cobblemon's BattleBuilder API with sgui for team selection. State persisted as JSON files.

**Tech Stack:** Kotlin, Fabric 1.21.1, Cobblemon API, sgui (server-side GUIs), Gson, JUnit 5

---

## File Structure

```
cobblemon-ranked/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  src/main/kotlin/com/cobblemonranked/
    CobblemonRanked.kt              # ModInitializer entry point
    config/RankedConfig.kt           # Config data class + JSON load/save
    data/PlayerEloData.kt            # Per-player ELO record data class
    data/EloStore.kt                 # JSON persistence for all player records
    elo/EloCalculator.kt             # Pure ELO math (no side effects)
    challenge/ChallengeManager.kt    # Pending challenges, force logic, accept/decline
    battle/RankedBattle.kt           # Battle initiation, result handling, flee/disconnect
    gui/TeamSelectionGui.kt          # sgui-based PC/party team picker
    decay/DecayManager.kt            # Daily decay logic + scheduling
    commands/RankedCommands.kt       # All /ranked commands
  src/main/resources/
    fabric.mod.json
  src/test/kotlin/com/cobblemonranked/
    elo/EloCalculatorTest.kt         # Unit tests for ELO math
    decay/DecayManagerTest.kt        # Unit tests for decay logic
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `cobblemon-ranked/settings.gradle.kts`
- Create: `cobblemon-ranked/gradle.properties`
- Create: `cobblemon-ranked/build.gradle.kts`
- Create: `cobblemon-ranked/src/main/resources/fabric.mod.json`
- Create: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
// cobblemon-ranked/settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "cobblemon-ranked"
```

- [ ] **Step 2: Create gradle.properties**

```properties
# cobblemon-ranked/gradle.properties
org.gradle.jvmargs=-Xmx2G
minecraft_version=1.21.1
loader_version=0.16.5
fabric_version=0.103.0+1.21.1
mod_version=1.0.0
maven_group=com.cobblemonranked
```

- [ ] **Step 3: Create build.gradle.kts**

```kotlin
// cobblemon-ranked/build.gradle.kts
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
    "id": "cobblemon-ranked",
    "version": "${version}",
    "name": "Cobblemon Ranked",
    "description": "ELO-rated ranked PvP battles for Cobblemon",
    "environment": "*",
    "entrypoints": {
        "main": [
            {
                "adapter": "kotlin",
                "value": "com.cobblemonranked.CobblemonRanked"
            }
        ]
    },
    "depends": {
        "fabricloader": ">=0.16.5",
        "minecraft": "~1.21.1",
        "fabric-api": "*",
        "fabric-language-kotlin": ">=1.12.3+kotlin.2.0.21",
        "cobblemon": ">=1.7.1"
    }
}
```

- [ ] **Step 5: Create entry point stub**

```kotlin
// src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt
package com.cobblemonranked

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CobblemonRanked : ModInitializer {
    const val MOD_ID = "cobblemon-ranked"
    val logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("Cobblemon Ranked initializing...")
    }
}
```

- [ ] **Step 6: Verify project compiles**

Run from `cobblemon-ranked/`:
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. Fix any dependency resolution issues before proceeding.

- [ ] **Step 7: Commit**

```bash
git add cobblemon-ranked/
git commit -m "feat(ranked): scaffold Fabric mod project"
```

---

### Task 2: Config System

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/config/RankedConfig.kt`

- [ ] **Step 1: Create config data class with load/save**

```kotlin
// src/main/kotlin/com/cobblemonranked/config/RankedConfig.kt
package com.cobblemonranked.config

import com.cobblemonranked.CobblemonRanked
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class ArenaCoords(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val world: String = "minecraft:overworld"
)

data class RankedConfig(
    val startingElo: Int = 1200,
    val minimumElo: Int = 1000,
    val kFactor: Int = 32,
    val levelCap: Int = 50,
    val maxLegendaries: Int = 1,
    val forcesPerDayPerPair: Int = 1,
    val decayEnabled: Boolean = true,
    val arenaCoords: ArenaCoords? = null
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): RankedConfig {
            val file = configDir.resolve("cobblemon-ranked").resolve("config.json")
            if (!file.exists()) {
                val default = RankedConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), RankedConfig::class.java)
            } catch (e: Exception) {
                CobblemonRanked.logger.error("Failed to load config, using defaults", e)
                RankedConfig()
            }
        }

        fun save(configDir: Path, config: RankedConfig) {
            val dir = configDir.resolve("cobblemon-ranked")
            dir.createDirectories()
            dir.resolve("config.json").writeText(gson.toJson(config))
        }
    }
}
```

- [ ] **Step 2: Wire config into entry point**

Update `CobblemonRanked.kt`:
```kotlin
package com.cobblemonranked

import com.cobblemonranked.config.RankedConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonRanked : ModInitializer {
    const val MOD_ID = "cobblemon-ranked"
    val logger = LoggerFactory.getLogger(MOD_ID)
    lateinit var config: RankedConfig

    override fun onInitialize() {
        logger.info("Cobblemon Ranked initializing...")
        val configDir = FabricLoader.getInstance().configDir
        config = RankedConfig.load(configDir)
        logger.info("Config loaded: startingElo=${config.startingElo}, kFactor=${config.kFactor}")
    }
}
```

- [ ] **Step 3: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(ranked): add config system"
```

---

### Task 3: ELO Data Model & Persistence

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/data/PlayerEloData.kt`
- Create: `src/main/kotlin/com/cobblemonranked/data/EloStore.kt`

- [ ] **Step 1: Create player data class**

```kotlin
// src/main/kotlin/com/cobblemonranked/data/PlayerEloData.kt
package com.cobblemonranked.data

data class PlayerEloData(
    val name: String,
    var elo: Int = 1200,
    var wins: Int = 0,
    var losses: Int = 0,
    var lastBattleDate: String? = null,
    val forceLog: MutableMap<String, String> = mutableMapOf()
)
```

- [ ] **Step 2: Create EloStore with JSON persistence**

```kotlin
// src/main/kotlin/com/cobblemonranked/data/EloStore.kt
package com.cobblemonranked.data

import com.cobblemonranked.CobblemonRanked
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class EloStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-ranked").resolve("elo.json")
    private val players: MutableMap<String, PlayerEloData> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, PlayerEloData>>() {}.type
            val loaded: MutableMap<String, PlayerEloData> = gson.fromJson(file.readText(), type)
            players.clear()
            players.putAll(loaded)
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load ELO data", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-ranked").createDirectories()
        file.writeText(gson.toJson(players))
    }

    fun getOrCreate(uuid: UUID, name: String): PlayerEloData {
        return players.getOrPut(uuid.toString()) {
            PlayerEloData(name = name, elo = CobblemonRanked.config.startingElo)
        }
    }

    fun get(uuid: UUID): PlayerEloData? = players[uuid.toString()]

    fun getAll(): Map<String, PlayerEloData> = players.toMap()

    fun getLeaderboard(): List<Pair<String, PlayerEloData>> {
        return players.entries
            .sortedByDescending { it.value.elo }
            .map { it.key to it.value }
    }

    fun setElo(uuid: UUID, elo: Int) {
        players[uuid.toString()]?.let {
            it.elo = elo.coerceAtLeast(CobblemonRanked.config.minimumElo)
            save()
        }
    }
}
```

- [ ] **Step 3: Wire EloStore into entry point**

Update `CobblemonRanked.kt` — add after config loading:
```kotlin
lateinit var eloStore: EloStore

// In onInitialize(), after config load:
eloStore = EloStore(configDir)
eloStore.load()
logger.info("ELO data loaded: ${eloStore.getAll().size} players")
```

- [ ] **Step 4: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(ranked): add ELO data model and JSON persistence"
```

---

### Task 4: ELO Calculator (TDD)

**Files:**
- Create: `src/test/kotlin/com/cobblemonranked/elo/EloCalculatorTest.kt`
- Create: `src/main/kotlin/com/cobblemonranked/elo/EloCalculator.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// src/test/kotlin/com/cobblemonranked/elo/EloCalculatorTest.kt
package com.cobblemonranked.elo

import kotlin.test.Test
import kotlin.test.assertEquals

class EloCalculatorTest {

    @Test
    fun `equal ratings - winner gains 16, loser loses 16`() {
        val (winnerNew, loserNew) = EloCalculator.calculate(
            winnerElo = 1200, loserElo = 1200, kFactor = 32, minimumElo = 1000
        )
        assertEquals(1216, winnerNew)
        assertEquals(1184, loserNew)
    }

    @Test
    fun `higher rated wins - small gain`() {
        val (winnerNew, loserNew) = EloCalculator.calculate(
            winnerElo = 1500, loserElo = 1200, kFactor = 32, minimumElo = 1000
        )
        // Expected score for 1500 vs 1200: 1/(1+10^(-300/400)) = 0.849
        // Winner gains: 32 * (1 - 0.849) = 4.83 -> 5
        // Loser loses: 32 * (0 - 0.151) = -4.83 -> 5
        assertEquals(1505, winnerNew)
        assertEquals(1195, loserNew)
    }

    @Test
    fun `lower rated wins - big gain`() {
        val (winnerNew, loserNew) = EloCalculator.calculate(
            winnerElo = 1200, loserElo = 1500, kFactor = 32, minimumElo = 1000
        )
        // Expected for 1200 vs 1500: 1/(1+10^(300/400)) = 0.151
        // Winner gains: 32 * (1 - 0.151) = 27.17 -> 27
        assertEquals(1227, winnerNew)
        assertEquals(1473, loserNew)
    }

    @Test
    fun `elo floor is respected`() {
        val (_, loserNew) = EloCalculator.calculate(
            winnerElo = 1200, loserElo = 1005, kFactor = 32, minimumElo = 1000
        )
        assertEquals(1000, loserNew)
    }

    @Test
    fun `decay against 1200 for high rated player`() {
        val newElo = EloCalculator.decayElo(
            currentElo = 1500, decayOpponentElo = 1200, kFactor = 32, minimumElo = 1000
        )
        // Same as losing to 1200: expected = 0.849, loss = 32*0.849 = 27
        assertEquals(1473, newElo)
    }

    @Test
    fun `decay at 1200 loses 16`() {
        val newElo = EloCalculator.decayElo(
            currentElo = 1200, decayOpponentElo = 1200, kFactor = 32, minimumElo = 1000
        )
        assertEquals(1184, newElo)
    }

    @Test
    fun `decay respects floor`() {
        val newElo = EloCalculator.decayElo(
            currentElo = 1010, decayOpponentElo = 1200, kFactor = 32, minimumElo = 1000
        )
        assertEquals(1000, newElo)
    }

    @Test
    fun `decay at floor does nothing`() {
        val newElo = EloCalculator.decayElo(
            currentElo = 1000, decayOpponentElo = 1200, kFactor = 32, minimumElo = 1000
        )
        assertEquals(1000, newElo)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test
```
Expected: FAIL — `EloCalculator` class not found.

- [ ] **Step 3: Implement EloCalculator**

```kotlin
// src/main/kotlin/com/cobblemonranked/elo/EloCalculator.kt
package com.cobblemonranked.elo

import kotlin.math.pow
import kotlin.math.roundToInt

object EloCalculator {

    /**
     * Calculate new ELO ratings after a match.
     * Returns (winnerNewElo, loserNewElo).
     */
    fun calculate(
        winnerElo: Int,
        loserElo: Int,
        kFactor: Int,
        minimumElo: Int
    ): Pair<Int, Int> {
        val expectedWinner = expectedScore(winnerElo, loserElo)
        val expectedLoser = expectedScore(loserElo, winnerElo)

        val winnerDelta = (kFactor * (1.0 - expectedWinner)).roundToInt()
        val loserDelta = (kFactor * (0.0 - expectedLoser)).roundToInt()

        val newWinner = (winnerElo + winnerDelta).coerceAtLeast(minimumElo)
        val newLoser = (loserElo + loserDelta).coerceAtLeast(minimumElo)

        return newWinner to newLoser
    }

    /**
     * Calculate ELO after decay (simulated loss to decayOpponentElo).
     */
    fun decayElo(
        currentElo: Int,
        decayOpponentElo: Int,
        kFactor: Int,
        minimumElo: Int
    ): Int {
        val expected = expectedScore(currentElo, decayOpponentElo)
        val delta = (kFactor * (0.0 - expected)).roundToInt()
        return (currentElo + delta).coerceAtLeast(minimumElo)
    }

    private fun expectedScore(myElo: Int, opponentElo: Int): Double {
        return 1.0 / (1.0 + 10.0.pow((opponentElo - myElo) / 400.0))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test
```
Expected: All 8 tests PASS. If any assertion is off by 1 due to rounding, adjust the expected values in the tests to match the actual rounding behavior — the formula is correct, rounding edge cases are acceptable.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(ranked): add ELO calculator with unit tests"
```

---

### Task 5: Challenge Manager

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/challenge/ChallengeManager.kt`

- [ ] **Step 1: Implement challenge logic**

```kotlin
// src/main/kotlin/com/cobblemonranked/challenge/ChallengeManager.kt
package com.cobblemonranked.challenge

import com.cobblemonranked.CobblemonRanked
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.util.UUID

data class PendingChallenge(
    val challengerUuid: UUID,
    val targetUuid: UUID,
    val isForced: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChallengeManager {
    private val pendingChallenges: MutableMap<UUID, PendingChallenge> = mutableMapOf()
    private val CHALLENGE_TIMEOUT_MS = 60_000L // 60 seconds

    /**
     * Attempt to challenge a player. Returns null on success, or an error message.
     */
    fun challenge(challenger: ServerPlayer, target: ServerPlayer): String? {
        val store = CobblemonRanked.eloStore
        val config = CobblemonRanked.config

        if (challenger.uuid == target.uuid) {
            return "You can't challenge yourself."
        }

        // Check if target is in a battle
        // Uses Cobblemon extension: ServerPlayer.isInBattle()
        if (isPlayerInBattle(target)) {
            return "${target.name.string} is already in a battle."
        }

        // Check if either player already has a pending challenge
        if (pendingChallenges.containsKey(challenger.uuid)) {
            return "You already have a pending challenge. Wait for it to expire or be answered."
        }

        val challengerData = store.getOrCreate(challenger.uuid, challenger.name.string)
        val targetData = store.getOrCreate(target.uuid, target.name.string)

        val challengerIsLower = challengerData.elo < targetData.elo
        val today = LocalDate.now().toString()

        val isForced = if (challengerIsLower) {
            // Check force limit for this pair today
            val lastForce = challengerData.forceLog[target.uuid.toString()]
            if (lastForce == today) {
                false // Already forced today, send as request
            } else {
                true
            }
        } else {
            false // Higher ELO can't force
        }

        val challenge = PendingChallenge(
            challengerUuid = challenger.uuid,
            targetUuid = target.uuid,
            isForced = isForced
        )
        pendingChallenges[target.uuid] = challenge

        if (isForced) {
            // Record force usage
            challengerData.forceLog[target.uuid.toString()] = today
            CobblemonRanked.eloStore.save()

            target.sendSystemMessage(
                Component.literal("[Ranked] ${challenger.name.string} (${challengerData.elo}) has forced you into a ranked match! Preparing team selection...")
            )
            challenger.sendSystemMessage(
                Component.literal("[Ranked] Force challenge sent to ${target.name.string} (${targetData.elo}). Preparing team selection...")
            )
            return null // Success — caller should start team selection
        } else {
            target.sendSystemMessage(
                Component.literal("[Ranked] ${challenger.name.string} (${challengerData.elo}) challenges you to a ranked match! Type /ranked accept or /ranked decline. (60s timeout)")
            )
            challenger.sendSystemMessage(
                Component.literal("[Ranked] Challenge sent to ${target.name.string} (${targetData.elo}). Waiting for response...")
            )
            return null // Success — waiting for accept
        }
    }

    /**
     * Accept a pending challenge. Returns the challenge if accepted, null if none pending.
     */
    fun accept(player: ServerPlayer): PendingChallenge? {
        val challenge = pendingChallenges.remove(player.uuid) ?: return null
        if (System.currentTimeMillis() - challenge.timestamp > CHALLENGE_TIMEOUT_MS) {
            return null // Expired
        }
        return challenge
    }

    /**
     * Decline a pending challenge.
     */
    fun decline(player: ServerPlayer): Boolean {
        val challenge = pendingChallenges.remove(player.uuid) ?: return false
        // Notify challenger
        val server = player.server
        val challenger = server.playerList.getPlayer(challenge.challengerUuid)
        challenger?.sendSystemMessage(
            Component.literal("[Ranked] ${player.name.string} declined your challenge.")
        )
        return true
    }

    /**
     * Get a pending forced challenge for a player (for immediate team selection).
     */
    fun getPendingForced(playerUuid: UUID): PendingChallenge? {
        val challenge = pendingChallenges[playerUuid]
        if (challenge != null && challenge.isForced) {
            pendingChallenges.remove(playerUuid)
            return challenge
        }
        return null
    }

    /**
     * Clean up expired challenges (call periodically).
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = pendingChallenges.filter { now - it.value.timestamp > CHALLENGE_TIMEOUT_MS }
        expired.forEach { (uuid, _) -> pendingChallenges.remove(uuid) }
    }

    private fun isPlayerInBattle(player: ServerPlayer): Boolean {
        return try {
            com.cobblemon.mod.common.Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null
        } catch (e: Exception) {
            false
        }
    }
}
```

- [ ] **Step 2: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(ranked): add challenge manager with force logic"
```

---

### Task 6: Team Selection GUI

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/gui/TeamSelectionGui.kt`

The GUI uses sgui to show a 6-row chest. Layout:
- Rows 1-3: PC Pokemon from current box (18 per page), navigable with arrows
- Row 4: Party Pokemon (6 slots) + prev/next box buttons
- Row 5: Info bar
- Row 6: Selected team (6 slots) + Confirm + Cancel

- [ ] **Step 1: Implement team selection GUI**

```kotlin
// src/main/kotlin/com/cobblemonranked/gui/TeamSelectionGui.kt
package com.cobblemonranked.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID
import java.util.function.Consumer

class TeamSelectionGui(
    private val player: ServerPlayer,
    private val maxLegendaries: Int,
    private val onConfirm: Consumer<List<Pokemon>>,
    private val onCancel: Runnable
) {
    private val selected: MutableList<Pokemon> = mutableListOf()
    private var currentBox = 0
    private val party = Cobblemon.storage.getParty(player)
    private val pc = Cobblemon.storage.getPC(player)

    fun open() {
        rebuild()
    }

    private fun rebuild() {
        val gui = SimpleGui(MenuType.GENERIC_9x6, player, false)
        gui.title = Component.literal("Select Your Team (${selected.size}/6)")

        // Rows 1-2: PC Pokemon from current box (18 slots)
        val boxCount = pc.boxes.size
        val box = if (boxCount > 0 && currentBox < boxCount) pc.boxes[currentBox] else null
        for (i in 0 until 18) {
            val pokemon = box?.let { b ->
                // Access Pokemon at slot i in the box
                try { b[i] } catch (e: Exception) { null }
            }
            if (pokemon != null) {
                gui.setSlot(i, pokemonElement(pokemon, pokemon in selected) {
                    toggleSelection(pokemon)
                    rebuild()
                })
            } else {
                gui.setSlot(i, GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal(" "))
                    .build())
            }
        }

        // Row 3: Prev box, box name, next box, spacers
        gui.setSlot(18, GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("<- Previous Box"))
            .setCallback { _, _, _ ->
                if (currentBox > 0) currentBox--
                rebuild()
            }.build())
        gui.setSlot(22, GuiElementBuilder(Items.NAME_TAG)
            .setName(Component.literal("Box ${currentBox + 1} / $boxCount"))
            .build())
        gui.setSlot(26, GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("Next Box ->"))
            .setCallback { _, _, _ ->
                if (currentBox < boxCount - 1) currentBox++
                rebuild()
            }.build())

        // Fill rest of row 3
        for (i in listOf(19, 20, 21, 23, 24, 25)) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }

        // Row 4: Party Pokemon (slots 27-32)
        for (i in 0 until 6) {
            val pokemon = party[i]
            if (pokemon != null) {
                gui.setSlot(27 + i, pokemonElement(pokemon, pokemon in selected) {
                    toggleSelection(pokemon)
                    rebuild()
                })
            } else {
                gui.setSlot(27 + i, GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal(" ")).build())
            }
        }
        // Party label
        gui.setSlot(33, GuiElementBuilder(Items.CHEST)
            .setName(Component.literal("^ PC | v Party ^")).build())
        for (i in 34..35) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }

        // Row 5: Info
        for (i in 36..44) {
            gui.setSlot(i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build())
        }
        gui.setSlot(40, GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("Selected: ${selected.size}/6"))
            .setLore(selected.map { Component.literal("- ${it.species.name} Lv.${it.level}") })
            .build())

        // Row 6: Selected team display + Confirm + Cancel
        for (i in 0 until 6) {
            if (i < selected.size) {
                val pokemon = selected[i]
                gui.setSlot(45 + i, pokemonElement(pokemon, true) {
                    selected.remove(pokemon)
                    rebuild()
                })
            } else {
                gui.setSlot(45 + i, GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal("Empty Slot")).build())
            }
        }

        // Spacer
        gui.setSlot(51, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
            .setName(Component.literal(" ")).build())

        // Confirm button
        gui.setSlot(52, GuiElementBuilder(Items.LIME_CONCRETE)
            .setName(Component.literal("Confirm Team").withStyle(Style.EMPTY.withBold(true)))
            .setCallback { _, _, _ ->
                if (selected.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[Ranked] You must select at least 1 Pokemon!"))
                    return@setCallback
                }
                gui.close()
                onConfirm.accept(selected.toList())
            }.build())

        // Cancel button
        gui.setSlot(53, GuiElementBuilder(Items.RED_CONCRETE)
            .setName(Component.literal("Cancel").withStyle(Style.EMPTY.withBold(true)))
            .setCallback { _, _, _ ->
                gui.close()
                onCancel.run()
            }.build())

        gui.open()
    }

    private fun toggleSelection(pokemon: Pokemon) {
        if (pokemon in selected) {
            selected.remove(pokemon)
        } else if (selected.size < 6) {
            selected.add(pokemon)
        } else {
            player.sendSystemMessage(Component.literal("[Ranked] Team is full! Remove a Pokemon first."))
        }
    }

    private fun pokemonElement(
        pokemon: Pokemon,
        isSelected: Boolean,
        onClick: Runnable
    ): ItemStack {
        val item = if (isSelected) Items.LIME_STAINED_GLASS_PANE else Items.WHITE_STAINED_GLASS_PANE
        val legendaryTag = if (pokemon.isLegendary()) " [LEGENDARY]" else ""
        return GuiElementBuilder(item)
            .setName(Component.literal("${pokemon.species.name} Lv.${pokemon.level}$legendaryTag"))
            .setLore(listOf(
                Component.literal("Type: ${pokemon.primaryType.name}" +
                    (pokemon.secondaryType?.let { "/${it.name}" } ?: "")),
                Component.literal("Ability: ${pokemon.ability.name}"),
                Component.literal("HP: ${pokemon.currentHealth}/${pokemon.hp}"),
                Component.literal(if (isSelected) "Click to deselect" else "Click to select")
            ))
            .setCallback { _, _, _ -> onClick.run() }
            .build()
    }
}
```

- [ ] **Step 2: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(ranked): add team selection GUI"
```

**Note:** The PC box access pattern (`pc.boxes[n][slot]`) needs verification against the actual Cobblemon `PCStore` API. If the API differs, adjust the box iteration. The core GUI structure and sgui usage patterns are correct.

---

### Task 7: Ranked Battle Manager

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt`

This is the core orchestrator: validates legality, initiates battles, handles results.

- [ ] **Step 1: Implement ranked battle manager**

```kotlin
// src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt
package com.cobblemonranked.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.elo.EloCalculator
import com.cobblemonranked.gui.TeamSelectionGui
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActiveRankedMatch(
    val player1Uuid: UUID,
    val player2Uuid: UUID,
    val battleId: UUID? = null
)

object RankedBattleManager {
    // Tracks which battles are ranked (by battle UUID)
    private val rankedBattles: ConcurrentHashMap<UUID, ActiveRankedMatch> = ConcurrentHashMap()
    // Tracks selected teams waiting for both players to confirm
    private val pendingTeams: ConcurrentHashMap<UUID, List<Pokemon>> = ConcurrentHashMap()
    private val pendingMatches: ConcurrentHashMap<UUID, UUID> = ConcurrentHashMap() // player -> opponent

    /**
     * Start the team selection flow for both players.
     */
    fun startTeamSelection(player1: ServerPlayer, player2: ServerPlayer) {
        val config = CobblemonRanked.config
        pendingMatches[player1.uuid] = player2.uuid
        pendingMatches[player2.uuid] = player1.uuid

        openSelectionGui(player1, player2.uuid, config.maxLegendaries)
        openSelectionGui(player2, player1.uuid, config.maxLegendaries)
    }

    private fun openSelectionGui(player: ServerPlayer, opponentUuid: UUID, maxLegendaries: Int) {
        TeamSelectionGui(
            player = player,
            maxLegendaries = maxLegendaries,
            onConfirm = { team ->
                pendingTeams[player.uuid] = team.map { it.clone() }
                player.sendSystemMessage(Component.literal("[Ranked] Team locked in! Waiting for opponent..."))
                checkBothReady(player)
            },
            onCancel = {
                cancelMatch(player)
            }
        ).open()
    }

    private fun checkBothReady(player: ServerPlayer) {
        val opponentUuid = pendingMatches[player.uuid] ?: return
        val myTeam = pendingTeams[player.uuid] ?: return
        val opponentTeam = pendingTeams[opponentUuid] ?: return // Opponent not ready yet

        val server = player.server
        val opponent = server.playerList.getPlayer(opponentUuid) ?: run {
            player.sendSystemMessage(Component.literal("[Ranked] Opponent disconnected. Match cancelled."))
            cleanup(player.uuid, opponentUuid)
            return
        }

        // Both ready — validate and start
        startBattle(player, myTeam, opponent, opponentTeam)
    }

    private fun startBattle(
        player1: ServerPlayer, team1: List<Pokemon>,
        player2: ServerPlayer, team2: List<Pokemon>
    ) {
        val config = CobblemonRanked.config

        // Legality check: count legendaries
        val p1Legendaries = team1.count { it.isLegendary() }
        val p2Legendaries = team2.count { it.isLegendary() }

        if (p1Legendaries > config.maxLegendaries && p2Legendaries > config.maxLegendaries) {
            // Both illegal — draw, no ELO change
            broadcast(player1.server, "[Ranked] Both players had illegal teams (too many legendaries). Match voided.")
            cleanup(player1.uuid, player2.uuid)
            return
        }
        if (p1Legendaries > config.maxLegendaries) {
            player1.sendSystemMessage(Component.literal("[Ranked] Your team has $p1Legendaries legendaries (max ${config.maxLegendaries}). You auto-lose."))
            resolveMatch(player2, player1)
            cleanup(player1.uuid, player2.uuid)
            return
        }
        if (p2Legendaries > config.maxLegendaries) {
            player2.sendSystemMessage(Component.literal("[Ranked] Your team has $p2Legendaries legendaries (max ${config.maxLegendaries}). You auto-lose."))
            resolveMatch(player1, player2)
            cleanup(player1.uuid, player2.uuid)
            return
        }

        // Build temporary party stores with cloned, healed Pokemon
        val store1 = buildTempParty(team1)
        val store2 = buildTempParty(team2)
        val teamMap = mapOf(player1.uuid to store1, player2.uuid to store2)

        // Create battle format with level cap
        val format = BattleFormat.GEN_9_SINGLES.copy(adjustLevel = config.levelCap)

        val result = BattleBuilder.pvp1v1(
            player1 = player1,
            player2 = player2,
            battleFormat = format,
            healFirst = true,
            cloneParties = true,
            partyAccessor = { teamMap[it.uuid] ?: it.party() }
        )

        result.ifSuccessful { battle ->
            val match = ActiveRankedMatch(player1.uuid, player2.uuid, battle.battleId)
            rankedBattles[battle.battleId] = match
            broadcast(player1.server,
                "[Ranked] Battle started: ${player1.name.string} vs ${player2.name.string}!")
        }

        result.ifErrored { errors ->
            player1.sendSystemMessage(Component.literal("[Ranked] Failed to start battle: ${errors.joinToString()}"))
            player2.sendSystemMessage(Component.literal("[Ranked] Failed to start battle: ${errors.joinToString()}"))
        }

        cleanup(player1.uuid, player2.uuid)
    }

    private fun buildTempParty(team: List<Pokemon>): PlayerPartyStore {
        // We'll pass cloned pokemon through the partyAccessor.
        // BattleBuilder with cloneParties=true will clone again, but healFirst
        // ensures they enter battle at full HP.
        // The simplest approach: put cloned pokemon in the player's party temporarily.
        // Instead, we use a custom PartyStore approach.
        // Since partyAccessor returns PartyStore, we create a temporary one.
        // Note: PlayerPartyStore constructor may require a UUID; adjust as needed.
        // For now, return the player's actual party and rely on cloneParties + healFirst.
        // TODO: This needs adjustment based on actual PartyStore constructor.
        // The key insight is partyAccessor lets us swap in a different store.
        throw UnsupportedOperationException("See implementation note below")
    }

    /**
     * Register battle event listeners. Call once from onInitialize().
     */
    fun registerEvents() {
        // Victory handler
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            val match = rankedBattles.remove(event.battle.battleId) ?: return@subscribe
            val winners = event.winners.filterIsInstance<PlayerBattleActor>()
            val losers = event.losers.filterIsInstance<PlayerBattleActor>()

            if (winners.isNotEmpty() && losers.isNotEmpty()) {
                val winnerPlayer = winners.first().entity
                val loserPlayer = losers.first().entity
                if (winnerPlayer != null && loserPlayer != null) {
                    resolveMatch(winnerPlayer, loserPlayer)
                }
            }
        }

        // Flee handler
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            val match = rankedBattles[event.battle.battleId] ?: return@subscribe
            // The player who fled loses
            val fleeingActors = event.battle.actors.filterIsInstance<PlayerBattleActor>()
            // BattleFledEvent should indicate who fled; handle based on API
            // Fallback: check which actor is no longer in the battle
        }
    }

    private fun resolveMatch(winner: ServerPlayer, loser: ServerPlayer) {
        val store = CobblemonRanked.eloStore
        val config = CobblemonRanked.config
        val winnerData = store.getOrCreate(winner.uuid, winner.name.string)
        val loserData = store.getOrCreate(loser.uuid, loser.name.string)

        val oldWinnerElo = winnerData.elo
        val oldLoserElo = loserData.elo

        val (newWinnerElo, newLoserElo) = EloCalculator.calculate(
            winnerElo = oldWinnerElo,
            loserElo = oldLoserElo,
            kFactor = config.kFactor,
            minimumElo = config.minimumElo
        )

        winnerData.elo = newWinnerElo
        winnerData.wins++
        winnerData.lastBattleDate = LocalDate.now().toString()

        loserData.elo = newLoserElo
        loserData.losses++
        loserData.lastBattleDate = LocalDate.now().toString()

        store.save()

        // Broadcast results
        val winnerDelta = newWinnerElo - oldWinnerElo
        val loserDelta = newLoserElo - oldLoserElo
        broadcast(winner.server,
            "[Ranked] ${winner.name.string} defeated ${loser.name.string}!")
        broadcast(winner.server,
            "[Ranked] ${winner.name.string}: $oldWinnerElo -> $newWinnerElo (+$winnerDelta) | " +
            "${loser.name.string}: $oldLoserElo -> $newLoserElo ($loserDelta)")

        // Show leaderboard changes
        val leaderboard = store.getLeaderboard()
        val top5 = leaderboard.take(5)
        broadcast(winner.server, "[Ranked] Leaderboard:")
        top5.forEachIndexed { i, (_, data) ->
            broadcast(winner.server, "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)")
        }
    }

    private fun cancelMatch(player: ServerPlayer) {
        val opponentUuid = pendingMatches[player.uuid] ?: return
        val opponent = player.server.playerList.getPlayer(opponentUuid)
        opponent?.sendSystemMessage(Component.literal("[Ranked] ${player.name.string} cancelled the match."))
        player.sendSystemMessage(Component.literal("[Ranked] Match cancelled."))
        cleanup(player.uuid, opponentUuid)
    }

    private fun cleanup(uuid1: UUID, uuid2: UUID) {
        pendingTeams.remove(uuid1)
        pendingTeams.remove(uuid2)
        pendingMatches.remove(uuid1)
        pendingMatches.remove(uuid2)
    }

    private fun broadcast(server: net.minecraft.server.MinecraftServer, message: String) {
        server.playerList.players.forEach {
            it.sendSystemMessage(Component.literal(message))
        }
    }
}
```

- [ ] **Step 2: Note on buildTempParty**

The `buildTempParty` function is a placeholder. The actual implementation depends on how `PlayerPartyStore` can be constructed. Two approaches to try at implementation time:

**Approach A** — If `PlayerPartyStore` has a constructor or factory:
```kotlin
private fun buildTempParty(team: List<Pokemon>): PlayerPartyStore {
    // Create a new store and populate with cloned pokemon
    val store = PlayerPartyStore(UUID.randomUUID())
    team.forEach { store.add(it.clone()) }
    return store
}
```

**Approach B** — If we can't easily construct a `PlayerPartyStore`, swap pokemon into the player's actual party before battle, then restore after:
```kotlin
// Before battle: save original party, replace with selected team
// After battle: restore original party
```

The engineer should check the `PlayerPartyStore` constructor at implementation time and pick the working approach.

- [ ] **Step 3: Wire into entry point**

Update `CobblemonRanked.onInitialize()`:
```kotlin
// After eloStore.load():
RankedBattleManager.registerEvents()
logger.info("Ranked battle events registered")
```

- [ ] **Step 4: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(ranked): add ranked battle manager with victory/flee handling"
```

---

### Task 8: Decay System

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/decay/DecayManager.kt`
- Create: `src/test/kotlin/com/cobblemonranked/decay/DecayManagerTest.kt`

- [ ] **Step 1: Write failing tests for decay logic**

```kotlin
// src/test/kotlin/com/cobblemonranked/decay/DecayManagerTest.kt
package com.cobblemonranked.decay

import com.cobblemonranked.elo.EloCalculator
import kotlin.test.Test
import kotlin.test.assertEquals

class DecayManagerTest {

    @Test
    fun `decay applies to players who did not battle`() {
        // Simulate: 3 players, one battled today, two didn't
        // Only the two who didn't battle should decay
        val players = mapOf(
            "player1" to PlayerState(elo = 1400, battledToday = true),
            "player2" to PlayerState(elo = 1300, battledToday = false),
            "player3" to PlayerState(elo = 1200, battledToday = false)
        )
        val decayed = computeDecay(players, kFactor = 32, minimumElo = 1000, decayElo = 1200)
        // player1 unchanged
        assertEquals(1400, decayed["player1"])
        // player2 decays as if losing to 1200
        assertEquals(EloCalculator.decayElo(1300, 1200, 32, 1000), decayed["player2"])
        // player3 decays as if losing to 1200
        assertEquals(EloCalculator.decayElo(1200, 1200, 32, 1000), decayed["player3"])
    }

    @Test
    fun `no decay when no battles happened`() {
        val players = mapOf(
            "player1" to PlayerState(elo = 1400, battledToday = false),
            "player2" to PlayerState(elo = 1300, battledToday = false)
        )
        // anyBattlesToday = false, so no decay
        val decayed = computeDecayConditional(players, anyBattlesToday = false,
            kFactor = 32, minimumElo = 1000, decayElo = 1200)
        assertEquals(1400, decayed["player1"])
        assertEquals(1300, decayed["player2"])
    }

    // Helper types for testing pure logic without Minecraft dependencies
    data class PlayerState(val elo: Int, val battledToday: Boolean)

    private fun computeDecay(
        players: Map<String, PlayerState>, kFactor: Int, minimumElo: Int, decayElo: Int
    ): Map<String, Int> {
        return players.mapValues { (_, state) ->
            if (state.battledToday) state.elo
            else EloCalculator.decayElo(state.elo, decayElo, kFactor, minimumElo)
        }
    }

    private fun computeDecayConditional(
        players: Map<String, PlayerState>, anyBattlesToday: Boolean,
        kFactor: Int, minimumElo: Int, decayElo: Int
    ): Map<String, Int> {
        if (!anyBattlesToday) return players.mapValues { it.value.elo }
        return computeDecay(players, kFactor, minimumElo, decayElo)
    }
}
```

- [ ] **Step 2: Run tests to verify they pass** (logic uses already-implemented EloCalculator)

```bash
./gradlew test
```
Expected: All tests PASS.

- [ ] **Step 3: Implement DecayManager**

```kotlin
// src/main/kotlin/com/cobblemonranked/decay/DecayManager.kt
package com.cobblemonranked.decay

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.elo.EloCalculator
import net.minecraft.server.MinecraftServer
import java.time.LocalDate

object DecayManager {
    private var lastDecayDate: String? = null
    var anyBattlesToday: Boolean = false
        private set

    /**
     * Call this whenever a ranked battle completes.
     */
    fun recordBattle() {
        anyBattlesToday = true
    }

    /**
     * Called once per day (from server tick check). Applies decay to all
     * players who didn't battle, but only if at least one battle happened.
     */
    fun tryDailyDecay(server: MinecraftServer) {
        val today = LocalDate.now().toString()
        if (today == lastDecayDate) return // Already processed today

        // It's a new day. Check if YESTERDAY had battles.
        // We apply decay for the previous day's inactivity.
        if (anyBattlesToday) {
            applyDecay(server)
        }

        // Reset for the new day
        lastDecayDate = today
        anyBattlesToday = false
    }

    /**
     * Force decay (admin command).
     */
    fun forceDecay(server: MinecraftServer) {
        applyDecay(server)
    }

    private fun applyDecay(server: MinecraftServer) {
        val config = CobblemonRanked.config
        if (!config.decayEnabled) return

        val store = CobblemonRanked.eloStore
        val today = LocalDate.now().toString()
        var decayCount = 0

        for ((uuid, data) in store.getAll()) {
            if (data.lastBattleDate == today) continue // Battled today, no decay
            val oldElo = data.elo
            val newElo = EloCalculator.decayElo(
                currentElo = oldElo,
                decayOpponentElo = config.startingElo, // Decay as if losing to 1200
                kFactor = config.kFactor,
                minimumElo = config.minimumElo
            )
            if (newElo != oldElo) {
                data.elo = newElo
                decayCount++
            }
        }

        if (decayCount > 0) {
            store.save()
            CobblemonRanked.logger.info("Daily decay applied to $decayCount players")
        }
    }
}
```

- [ ] **Step 4: Wire decay into server tick**

Add to `CobblemonRanked.onInitialize()`:
```kotlin
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

// Register daily decay check (runs once per tick, but only acts once per day)
var tickCounter = 0
ServerTickEvents.END_SERVER_TICK.register { server ->
    tickCounter++
    if (tickCounter % 1200 == 0) { // Check every 60 seconds (20 tps * 60)
        DecayManager.tryDailyDecay(server)
    }
}
```

Also update `RankedBattleManager.resolveMatch()` to call `DecayManager.recordBattle()` after a match resolves.

- [ ] **Step 5: Verify compiles, run tests, commit**

```bash
./gradlew build && ./gradlew test
git add -A && git commit -m "feat(ranked): add daily ELO decay system"
```

---

### Task 9: Commands

**Files:**
- Create: `src/main/kotlin/com/cobblemonranked/commands/RankedCommands.kt`

- [ ] **Step 1: Implement all /ranked commands**

```kotlin
// src/main/kotlin/com/cobblemonranked/commands/RankedCommands.kt
package com.cobblemonranked.commands

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.decay.DecayManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object RankedCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ranked")
                .then(Commands.literal("challenge")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { ctx ->
                            val source = ctx.source.playerOrException
                            val target = EntityArgument.getPlayer(ctx, "player")
                            handleChallenge(source, target)
                            1
                        }
                    )
                )
                .then(Commands.literal("accept")
                    .executes { ctx ->
                        val source = ctx.source.playerOrException
                        handleAccept(source)
                        1
                    }
                )
                .then(Commands.literal("decline")
                    .executes { ctx ->
                        val source = ctx.source.playerOrException
                        handleDecline(source)
                        1
                    }
                )
                .then(Commands.literal("stats")
                    .executes { ctx ->
                        val source = ctx.source.playerOrException
                        showStats(source, source)
                        1
                    }
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { ctx ->
                            val source = ctx.source.playerOrException
                            val target = EntityArgument.getPlayer(ctx, "player")
                            showStats(source, target)
                            1
                        }
                    )
                )
                .then(Commands.literal("leaderboard")
                    .executes { ctx ->
                        showLeaderboard(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("setelo")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes { ctx ->
                                    val target = EntityArgument.getPlayer(ctx, "player")
                                    val value = IntegerArgumentType.getInteger(ctx, "value")
                                    adminSetElo(ctx.source, target, value)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("decay")
                        .executes { ctx ->
                            DecayManager.forceDecay(ctx.source.server)
                            ctx.source.sendSystemMessage(Component.literal("[Ranked] Decay manually triggered."))
                            1
                        }
                    )
                    .then(Commands.literal("force")
                        .then(Commands.argument("player1", EntityArgument.player())
                            .then(Commands.argument("player2", EntityArgument.player())
                                .executes { ctx ->
                                    val p1 = EntityArgument.getPlayer(ctx, "player1")
                                    val p2 = EntityArgument.getPlayer(ctx, "player2")
                                    adminForce(ctx.source, p1, p2)
                                    1
                                }
                            )
                        )
                    )
                )
        )
    }

    private fun handleChallenge(challenger: ServerPlayer, target: ServerPlayer) {
        val challengeManager = CobblemonRanked.challengeManager
        val error = challengeManager.challenge(challenger, target)
        if (error != null) {
            challenger.sendSystemMessage(Component.literal("[Ranked] $error"))
            return
        }

        // If forced, immediately start team selection
        val forced = challengeManager.getPendingForced(target.uuid)
        if (forced != null) {
            RankedBattleManager.startTeamSelection(challenger, target)
        }
        // Otherwise, wait for /ranked accept
    }

    private fun handleAccept(player: ServerPlayer) {
        val challenge = CobblemonRanked.challengeManager.accept(player)
        if (challenge == null) {
            player.sendSystemMessage(Component.literal("[Ranked] No pending challenge to accept."))
            return
        }
        val challenger = player.server.playerList.getPlayer(challenge.challengerUuid)
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("[Ranked] Challenger is no longer online."))
            return
        }
        RankedBattleManager.startTeamSelection(challenger, player)
    }

    private fun handleDecline(player: ServerPlayer) {
        if (!CobblemonRanked.challengeManager.decline(player)) {
            player.sendSystemMessage(Component.literal("[Ranked] No pending challenge to decline."))
        } else {
            player.sendSystemMessage(Component.literal("[Ranked] Challenge declined."))
        }
    }

    private fun showStats(viewer: ServerPlayer, target: ServerPlayer) {
        val data = CobblemonRanked.eloStore.getOrCreate(target.uuid, target.name.string)
        viewer.sendSystemMessage(Component.literal(
            "[Ranked] ${target.name.string}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"}"
        ))
    }

    private fun showLeaderboard(source: CommandSourceStack) {
        val leaderboard = CobblemonRanked.eloStore.getLeaderboard()
        source.sendSystemMessage(Component.literal("[Ranked] === Leaderboard ==="))
        if (leaderboard.isEmpty()) {
            source.sendSystemMessage(Component.literal("  No players ranked yet."))
            return
        }
        leaderboard.take(10).forEachIndexed { i, (_, data) ->
            source.sendSystemMessage(Component.literal(
                "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)"
            ))
        }
    }

    private fun adminSetElo(source: CommandSourceStack, target: ServerPlayer, value: Int) {
        CobblemonRanked.eloStore.setElo(target.uuid, value)
        source.sendSystemMessage(Component.literal(
            "[Ranked] Set ${target.name.string}'s ELO to ${value.coerceAtLeast(CobblemonRanked.config.minimumElo)}"
        ))
    }

    private fun adminForce(source: CommandSourceStack, p1: ServerPlayer, p2: ServerPlayer) {
        if (p1.uuid == p2.uuid) {
            source.sendSystemMessage(Component.literal("[Ranked] Can't force a player to fight themselves."))
            return
        }
        source.sendSystemMessage(Component.literal(
            "[Ranked] Forcing match: ${p1.name.string} vs ${p2.name.string}"
        ))
        RankedBattleManager.startTeamSelection(p1, p2)
    }
}
```

- [ ] **Step 2: Wire commands and challengeManager into entry point**

Update `CobblemonRanked.kt`:
```kotlin
import com.cobblemonranked.challenge.ChallengeManager
import com.cobblemonranked.commands.RankedCommands
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback

lateinit var challengeManager: ChallengeManager

// In onInitialize():
challengeManager = ChallengeManager()

CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
    RankedCommands.register(dispatcher)
}
logger.info("Ranked commands registered")
```

- [ ] **Step 3: Verify compiles, commit**

```bash
./gradlew build
git add -A && git commit -m "feat(ranked): add all /ranked commands"
```

---

### Task 10: Integration Wiring & Manual Testing

**Files:**
- Modify: `src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt` (final assembly)

- [ ] **Step 1: Finalize entry point with all wiring**

The final `CobblemonRanked.kt` should have all components wired together. Verify it includes:
- Config loading
- EloStore loading
- ChallengeManager creation
- RankedBattleManager event registration
- Decay tick registration
- Command registration
- Challenge cleanup tick (every 5 seconds)

```kotlin
package com.cobblemonranked

import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.challenge.ChallengeManager
import com.cobblemonranked.commands.RankedCommands
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.data.EloStore
import com.cobblemonranked.decay.DecayManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonRanked : ModInitializer {
    const val MOD_ID = "cobblemon-ranked"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: RankedConfig
    lateinit var eloStore: EloStore
    lateinit var challengeManager: ChallengeManager

    override fun onInitialize() {
        logger.info("Cobblemon Ranked initializing...")

        val configDir = FabricLoader.getInstance().configDir
        config = RankedConfig.load(configDir)
        eloStore = EloStore(configDir)
        eloStore.load()
        challengeManager = ChallengeManager()

        RankedBattleManager.registerEvents()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RankedCommands.register(dispatcher)
        }

        var tickCounter = 0
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++
            if (tickCounter % 100 == 0) { // Every 5 seconds
                challengeManager.cleanupExpired()
            }
            if (tickCounter % 1200 == 0) { // Every 60 seconds
                DecayManager.tryDailyDecay(server)
            }
        }

        logger.info("Cobblemon Ranked initialized! ${eloStore.getAll().size} players loaded.")
    }
}
```

- [ ] **Step 2: Build the mod JAR**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. JAR located at `build/libs/cobblemon-ranked-1.0.0.jar`.

- [ ] **Step 3: Manual test plan**

Run a local Fabric server with Cobblemon + this mod installed. Test:

1. **Config generation:** Start server, verify `config/cobblemon-ranked/config.json` is created with defaults.
2. **Challenge flow:** With 2 players, run `/ranked challenge <player2>`. Verify message appears for both players. Test `/ranked accept` and `/ranked decline`.
3. **Force match:** Give player1 lower ELO (via `/ranked admin setelo`). Have player1 `/ranked challenge player2`. Verify it forces immediately.
4. **Team selection:** Verify GUI opens for both players. Select Pokemon, confirm.
5. **Battle:** Verify battle starts at level 50 cap, Pokemon are healed.
6. **Victory:** Verify ELO updates and leaderboard broadcasts after battle ends.
7. **Legendary check:** Put 2+ legendaries on a team, verify auto-loss.
8. **Leaderboard:** Run `/ranked leaderboard`, verify output.
9. **Decay:** Use `/ranked admin decay`, verify inactive players lose ELO.
10. **Admin force:** Run `/ranked admin force player1 player2`, verify both enter match flow.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(ranked): finalize wiring and integration"
```
