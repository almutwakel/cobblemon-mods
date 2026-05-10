# Cobblemon Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a player profile showcase mod, team saving to ranked, per-player spend tracking + leaderboards to both ranked and market mods.

**Architecture:** Three independent mods communicate via JSON files on disk and reflection. No compile-time dependencies between them. The showcase mod reads data from ranked (elo.json, teams/*.json), market (player_spend.json), and economy (reflection) to present a unified player profile GUI.

**Tech Stack:** Fabric 1.21.1, Kotlin, Architectury Loom, sgui, Cobblemon 1.7.3, Gson

---

## File Map

### cobblemon-ranked (modifications)

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/com/cobblemonranked/data/TeamStore.kt` | Create | Save/load last PVP team per player |
| `src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt` | Modify | Save teams after battle resolves |
| `src/main/kotlin/com/cobblemonranked/commands/RankedCommands.kt` | Modify | Enhanced leaderboard with self-rank |
| `src/main/kotlin/com/cobblemonranked/config/RankedConfig.kt` | Modify | Add leaderboardSize config |
| `src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt` | Modify | Initialize TeamStore |

### cobblemon-market (modifications)

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/com/cobblemonmarket/data/PlayerSpendStore.kt` | Create | Track per-player market spend |
| `src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt` | Modify | Record spend on buy |
| `src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt` | Modify | Add leaderboard command |
| `src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt` | Modify | Add leaderboardSize config |
| `src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt` | Modify | Initialize PlayerSpendStore |

### cobblemon-showcase (new mod)

| File | Action | Purpose |
|------|--------|---------|
| `build.gradle.kts` | Create | Build config (copy from market mod) |
| `settings.gradle.kts` | Create | Project name |
| `gradle.properties` | Create | Version and group |
| `src/main/resources/fabric.mod.json` | Create | Mod metadata |
| `src/main/resources/cobblemon-showcase.mixins.json` | Create | Mixin config for chat |
| `src/main/kotlin/com/cobblemonshowcase/CobblemonShowcase.kt` | Create | Mod entry point |
| `src/main/kotlin/com/cobblemonshowcase/config/ShowcaseConfig.kt` | Create | Config with teams list, cooldown |
| `src/main/kotlin/com/cobblemonshowcase/data/PlayerDataStore.kt` | Create | Player team affiliation + cooldown |
| `src/main/kotlin/com/cobblemonshowcase/data/BadgeReader.kt` | Create | Read badge JSONs |
| `src/main/kotlin/com/cobblemonshowcase/data/CrossModReader.kt` | Create | Read ranked elo/teams, market spend, economy balance |
| `src/main/kotlin/com/cobblemonshowcase/gui/ShowcaseGui.kt` | Create | 6-row profile GUI |
| `src/main/kotlin/com/cobblemonshowcase/commands/ShowcaseCommands.kt` | Create | /showcase and /team commands |
| `src/main/kotlin/com/cobblemonshowcase/mixin/ChatMessageMixin.java` | Create | Make player names clickable |

---

### Task 1: cobblemon-ranked — TeamStore and team saving

**Files:**
- Create: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/data/TeamStore.kt`
- Modify: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt:186-227`
- Modify: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt:27-29`

- [ ] **Step 1: Create TeamStore**

Create `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/data/TeamStore.kt`:

```kotlin
package com.cobblemonranked.data

import com.cobblemonranked.CobblemonRanked
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class TeamPokemonData(
    val species: String,
    val level: Int,
    val nickname: String?
)

data class SavedTeam(
    val team: List<TeamPokemonData>,
    val timestamp: String
)

class TeamStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val teamsDir = configDir.resolve("cobblemon-ranked").resolve("teams")

    fun saveTeam(uuid: UUID, pokemonList: List<Pokemon>) {
        teamsDir.createDirectories()
        val data = SavedTeam(
            team = pokemonList.map { pokemon ->
                TeamPokemonData(
                    species = pokemon.species.name,
                    level = pokemon.level,
                    nickname = pokemon.nickname?.string
                )
            },
            timestamp = Instant.now().toString()
        )
        teamsDir.resolve("$uuid.json").writeText(gson.toJson(data))
    }

    fun loadTeam(uuid: UUID): SavedTeam? {
        val file = teamsDir.resolve("$uuid.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), SavedTeam::class.java)
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load team for $uuid", e)
            null
        }
    }
}
```

- [ ] **Step 2: Initialize TeamStore in CobblemonRanked**

In `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt`, add the field and initialization:

Add field after `lateinit var challengeManager`:
```kotlin
lateinit var teamStore: TeamStore
```

Add in `onInitialize()` after `challengeManager = ChallengeManager()`:
```kotlin
teamStore = TeamStore(configDir)
```

- [ ] **Step 3: Save teams in resolveMatch and startBattle**

In `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt`:

In `startBattle()`, right before `val format = BattleFormat.GEN_9_SINGLES...` (after legality checks pass), add:
```kotlin
        // Save teams for showcase
        CobblemonRanked.teamStore.saveTeam(player1.uuid, team1)
        CobblemonRanked.teamStore.saveTeam(player2.uuid, team2)
```

Add the import at the top of the file:
```kotlin
import com.cobblemonranked.data.TeamStore
```

- [ ] **Step 4: Build and verify**

Run: `export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home" && cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-ranked && ./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add cobblemon-ranked/src/main/kotlin/com/cobblemonranked/data/TeamStore.kt cobblemon-ranked/src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt cobblemon-ranked/src/main/kotlin/com/cobblemonranked/CobblemonRanked.kt
git commit -m "feat(ranked): save last PVP team to disk after ranked battles"
```

---

### Task 2: cobblemon-ranked — Enhanced leaderboard with self-rank

**Files:**
- Modify: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/commands/RankedCommands.kt:139-151`
- Modify: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/config/RankedConfig.kt:19-27`
- Modify: `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt:220-226`

- [ ] **Step 1: Add leaderboardSize to RankedConfig**

In `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/config/RankedConfig.kt`, add field to the data class:

```kotlin
data class RankedConfig(
    val startingElo: Int = 1200,
    val minimumElo: Int = 1000,
    val kFactor: Int = 32,
    val levelCap: Int = 50,
    val maxLegendaries: Int = 1,
    val forcesPerDayPerPair: Int = 1,
    val decayEnabled: Boolean = true,
    val leaderboardSize: Int = 10,
    val arenaCoords: ArenaCoords? = null
)
```

- [ ] **Step 2: Update showLeaderboard in RankedCommands**

Replace the `showLeaderboard` function in `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/commands/RankedCommands.kt`:

```kotlin
    private fun showLeaderboard(source: CommandSourceStack) {
        val config = CobblemonRanked.config
        val leaderboard = CobblemonRanked.eloStore.getLeaderboard()
        source.sendSystemMessage(Component.literal("[Ranked] === ELO Leaderboard ==="))
        if (leaderboard.isEmpty()) {
            source.sendSystemMessage(Component.literal("  No players ranked yet."))
            return
        }
        val topN = leaderboard.take(config.leaderboardSize)
        topN.forEachIndexed { i, (_, data) ->
            source.sendSystemMessage(Component.literal(
                "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)"
            ))
        }

        // Show caller's rank if not in top N
        val player = source.player ?: return
        val playerUuid = player.uuid.toString()
        val playerIndex = leaderboard.indexOfFirst { it.first == playerUuid }
        if (playerIndex >= config.leaderboardSize) {
            val (_, playerData) = leaderboard[playerIndex]
            source.sendSystemMessage(Component.literal("  ---"))
            source.sendSystemMessage(Component.literal(
                "  ${playerIndex + 1}. ${playerData.name}: ${playerData.elo} (${playerData.wins}W/${playerData.losses}L)"
            ))
        }
    }
```

- [ ] **Step 3: Update post-match broadcast leaderboard**

In `cobblemon-ranked/src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt`, replace the leaderboard broadcast at the end of `resolveMatch()` (lines 220-226):

```kotlin
        val leaderboard = store.getLeaderboard()
        val top5 = leaderboard.take(config.leaderboardSize)
        broadcast(winner.server, "[Ranked] Leaderboard:")
        top5.forEachIndexed { i, (_, data) ->
            broadcast(winner.server, "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)")
        }
```

- [ ] **Step 4: Build and verify**

Run: `export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home" && cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-ranked && ./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add cobblemon-ranked/src/main/kotlin/com/cobblemonranked/commands/RankedCommands.kt cobblemon-ranked/src/main/kotlin/com/cobblemonranked/config/RankedConfig.kt cobblemon-ranked/src/main/kotlin/com/cobblemonranked/battle/RankedBattle.kt
git commit -m "feat(ranked): enhanced leaderboard with configurable size and self-rank"
```

---

### Task 3: cobblemon-market — PlayerSpendStore

**Files:**
- Create: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/PlayerSpendStore.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt:20-31`

- [ ] **Step 1: Create PlayerSpendStore**

Create `cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/PlayerSpendStore.kt`:

```kotlin
package com.cobblemonmarket.data

import com.cobblemonmarket.CobblemonMarket
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class PlayerSpendData(
    val name: String,
    var totalSpend: Int = 0
)

class PlayerSpendStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-market").resolve("player_spend.json")
    private val players: MutableMap<String, PlayerSpendData> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, PlayerSpendData>>() {}.type
            val loaded: MutableMap<String, PlayerSpendData> = gson.fromJson(file.readText(), type)
            players.clear()
            players.putAll(loaded)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load player spend data", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-market").createDirectories()
        file.writeText(gson.toJson(players))
    }

    fun recordSpend(uuid: UUID, name: String, amount: Int) {
        val data = players.getOrPut(uuid.toString()) { PlayerSpendData(name = name) }
        data.totalSpend += amount
        save()
    }

    fun getSpend(uuid: UUID): PlayerSpendData? = players[uuid.toString()]

    fun getAllKnownUuids(): Set<String> = players.keys.toSet()

    fun getAll(): Map<String, PlayerSpendData> = players.toMap()
}
```

- [ ] **Step 2: Initialize PlayerSpendStore in CobblemonMarket**

In `cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt`, add field after `lateinit var marketStore`:

```kotlin
lateinit var playerSpendStore: PlayerSpendStore
```

Add import:
```kotlin
import com.cobblemonmarket.data.PlayerSpendStore
```

Add in `onInitialize()` after `marketStore.load()`:
```kotlin
playerSpendStore = PlayerSpendStore(configDir)
playerSpendStore.load()
```

- [ ] **Step 3: Build and verify**

Run: `export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home" && cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market && ./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add cobblemon-market/src/main/kotlin/com/cobblemonmarket/data/PlayerSpendStore.kt cobblemon-market/src/main/kotlin/com/cobblemonmarket/CobblemonMarket.kt
git commit -m "feat(market): add per-player spend tracking store"
```

---

### Task 4: cobblemon-market — Record spend on buy + leaderboard command

**Files:**
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt:182-186`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt`
- Modify: `cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt:12-21`

- [ ] **Step 1: Record spend in TransactionGui**

In `cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt`, in the `executeTransaction()` method's buy branch, after the line `subtractBalance(result.totalPrice)`, add:

```kotlin
            CobblemonMarket.playerSpendStore.recordSpend(player.uuid, player.name.string, result.totalPrice)
```

- [ ] **Step 2: Add leaderboardSize to MarketConfig**

In `cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt`, add field:

```kotlin
data class MarketConfig(
    val spreadBase: Double = 3.0,
    val spreadExtra: Double = 4.0,
    val recoveryRatePerHour: Double = 0.04,
    val factorFloor: Double = 0.10,
    val factorCeiling: Double = 1.00,
    val sellDecay: Double = 0.98,
    val buyGrowth: Double = 1.02,
    val transactionWindowSize: Int = 50,
    val leaderboardSize: Int = 10
)
```

- [ ] **Step 3: Add leaderboard command to MarketCommands**

In `cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt`, add the command registration after the `version` literal block:

```kotlin
                .then(Commands.literal("leaderboard")
                    .executes { ctx ->
                        showLeaderboard(ctx.source)
                        1
                    }
                )
```

Add the `showLeaderboard` function and the economy helper. Add these imports at the top:

```kotlin
import java.math.BigDecimal
import java.util.UUID
```

Add the function:

```kotlin
    private fun showLeaderboard(source: CommandSourceStack) {
        val config = CobblemonMarket.config
        val knownUuids = CobblemonMarket.playerSpendStore.getAllKnownUuids()

        if (knownUuids.isEmpty()) {
            source.sendSystemMessage(Component.literal("[Market] No players have used the market yet."))
            return
        }

        // Query balances for all known players
        val balances = mutableListOf<Triple<String, String, Int>>() // uuid, name, balance
        for (uuidStr in knownUuids) {
            val spendData = CobblemonMarket.playerSpendStore.getAll()[uuidStr] ?: continue
            val balance = getBalanceForUuid(UUID.fromString(uuidStr))
            balances.add(Triple(uuidStr, spendData.name, balance))
        }

        balances.sortByDescending { it.third }

        source.sendSystemMessage(Component.literal("[Market] === Wealth Leaderboard ==="))
        val topN = balances.take(config.leaderboardSize)
        topN.forEachIndexed { i, (_, name, balance) ->
            source.sendSystemMessage(Component.literal(
                "  ${i + 1}. $name: $balance PokeDollars"
            ))
        }

        // Show caller's rank if not in top N
        val player = source.player ?: return
        val playerUuid = player.uuid.toString()
        val playerIndex = balances.indexOfFirst { it.first == playerUuid }
        if (playerIndex >= config.leaderboardSize) {
            val (_, name, balance) = balances[playerIndex]
            source.sendSystemMessage(Component.literal("  ---"))
            source.sendSystemMessage(Component.literal(
                "  ${playerIndex + 1}. $name: $balance PokeDollars"
            ))
        }
    }

    private fun getBalanceForUuid(uuid: UUID): Int {
        return try {
            val loader = FabricLoader.getInstance()
            val entrypoints = loader.getEntrypointContainers("main", net.fabricmc.api.ModInitializer::class.java)
            val economyEntry = entrypoints.firstOrNull { it.provider.metadata.id == "cobblemon-economy" }
                ?: return 0
            val economyInstance = economyEntry.entrypoint
            val manager = economyInstance.javaClass.getMethod("getEconomyManager").invoke(economyInstance)
            val method = manager.javaClass.getMethod("getBalance", java.util.UUID::class.java)
            val balance = method.invoke(manager, uuid) as BigDecimal
            balance.toInt()
        } catch (e: Exception) {
            0
        }
    }
```

Add the import for `net.fabricmc.api.ModInitializer`:
```kotlin
import net.fabricmc.api.ModInitializer
```

- [ ] **Step 4: Build and verify**

Run: `export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home" && cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market && ./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add cobblemon-market/src/main/kotlin/com/cobblemonmarket/gui/TransactionGui.kt cobblemon-market/src/main/kotlin/com/cobblemonmarket/commands/MarketCommands.kt cobblemon-market/src/main/kotlin/com/cobblemonmarket/config/MarketConfig.kt
git commit -m "feat(market): record per-player spend and add wealth leaderboard command"
```

---

### Task 5: cobblemon-showcase — Scaffold mod

**Files:**
- Create: `cobblemon-showcase/build.gradle.kts`
- Create: `cobblemon-showcase/settings.gradle.kts`
- Create: `cobblemon-showcase/gradle.properties`
- Create: `cobblemon-showcase/src/main/resources/fabric.mod.json`
- Create: `cobblemon-showcase/src/main/resources/cobblemon-showcase.mixins.json`
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/CobblemonShowcase.kt`

- [ ] **Step 1: Create gradle.properties**

Create `cobblemon-showcase/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4G
minecraft_version=1.21.1
loader_version=0.17.2
fabric_version=0.116.6+1.21.1
mod_version=1.0.0
maven_group=com.cobblemonshowcase
```

- [ ] **Step 2: Create settings.gradle.kts**

Create `cobblemon-showcase/settings.gradle.kts`:

```kotlin
rootProject.name = "cobblemon-showcase"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
    }
}
```

- [ ] **Step 3: Create build.gradle.kts**

Create `cobblemon-showcase/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    kotlin("jvm") version "2.2.20"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    maven("https://maven.nucleoid.xyz")
    maven("https://artefacts.cobblemon.com/releases")
    mavenCentral()
}

dependencies {
    minecraft("net.minecraft:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation(fabricApi.module("fabric-command-api-v2", project.property("fabric_version") as String))
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", project.property("fabric_version") as String))

    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.6+kotlin.2.2.20")

    modImplementation("com.cobblemon:mod:1.7.3+1.21.1") { isTransitive = false }
    modImplementation("com.cobblemon:fabric:1.7.3+1.21.1")

    modImplementation(include("eu.pb4:sgui:1.6.1+1.21.1")!!)
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(project.properties)
        }
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

- [ ] **Step 4: Create fabric.mod.json**

Create `cobblemon-showcase/src/main/resources/fabric.mod.json`:

```json
{
    "schemaVersion": 1,
    "id": "cobblemon-showcase",
    "version": "${version}",
    "name": "Cobblemon Showcase",
    "description": "Player profile showcase with badges, teams, and stats",
    "environment": "*",
    "entrypoints": {
        "main": [
            {
                "adapter": "kotlin",
                "value": "com.cobblemonshowcase.CobblemonShowcase"
            }
        ]
    },
    "mixins": [
        "cobblemon-showcase.mixins.json"
    ],
    "depends": {
        "fabricloader": ">=0.16.5",
        "minecraft": "~1.21.1",
        "fabric-api": "*",
        "fabric-language-kotlin": ">=1.12.3+kotlin.2.0.21"
    },
    "suggests": {
        "cobblemon-ranked": "*",
        "cobblemon-market": "*",
        "cobblemon-economy": "*"
    }
}
```

- [ ] **Step 5: Create mixin config**

Create `cobblemon-showcase/src/main/resources/cobblemon-showcase.mixins.json`:

```json
{
    "required": true,
    "package": "com.cobblemonshowcase.mixin",
    "compatibilityLevel": "JAVA_21",
    "mixins": [
        "ServerPlayerMixin"
    ],
    "injectors": {
        "defaultRequire": 1
    }
}
```

- [ ] **Step 6: Create mod entry point**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/CobblemonShowcase.kt`:

```kotlin
package com.cobblemonshowcase

import com.cobblemonshowcase.commands.ShowcaseCommands
import com.cobblemonshowcase.config.ShowcaseConfig
import com.cobblemonshowcase.data.PlayerDataStore
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonShowcase : ModInitializer {
    const val MOD_ID = "cobblemon-showcase"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: ShowcaseConfig
    lateinit var playerDataStore: PlayerDataStore

    override fun onInitialize() {
        logger.info("Cobblemon Showcase initializing...")

        val configDir = FabricLoader.getInstance().configDir
        config = ShowcaseConfig.load(configDir)
        playerDataStore = PlayerDataStore(configDir)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ShowcaseCommands.register(dispatcher)
        }

        logger.info("Cobblemon Showcase initialized!")
    }
}
```

- [ ] **Step 7: Copy Gradle wrapper from existing mod**

```bash
cp -r /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market/gradle /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase/
cp /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market/gradlew /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase/
cp /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market/gradlew.bat /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase/
```

- [ ] **Step 8: Commit**

```bash
git add cobblemon-showcase/
git commit -m "feat(showcase): scaffold mod with build config and entry point"
```

---

### Task 6: cobblemon-showcase — Config and data stores

**Files:**
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/config/ShowcaseConfig.kt`
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/PlayerDataStore.kt`
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/BadgeReader.kt`

- [ ] **Step 1: Create ShowcaseConfig**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/config/ShowcaseConfig.kt`:

```kotlin
package com.cobblemonshowcase.config

import com.cobblemonshowcase.CobblemonShowcase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class ShowcaseConfig(
    val teams: List<String> = listOf("Valor", "Instinct", "Mystic"),
    val teamSwitchCooldownHours: Int = 24,
    val badgesDir: String = "config/cobblemon-showcase/badges"
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): ShowcaseConfig {
            val file = configDir.resolve("cobblemon-showcase").resolve("config.json")
            if (!file.exists()) {
                val default = ShowcaseConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), ShowcaseConfig::class.java)
            } catch (e: Exception) {
                CobblemonShowcase.logger.error("Failed to load showcase config, using defaults", e)
                ShowcaseConfig()
            }
        }

        fun save(configDir: Path, config: ShowcaseConfig) {
            val dir = configDir.resolve("cobblemon-showcase")
            dir.createDirectories()
            dir.resolve("config.json").writeText(gson.toJson(config))
        }
    }
}
```

- [ ] **Step 2: Create PlayerDataStore**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/PlayerDataStore.kt`:

```kotlin
package com.cobblemonshowcase.data

import com.cobblemonshowcase.CobblemonShowcase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class PlayerShowcaseData(
    var team: String? = null,
    var lastTeamSwitch: String? = null
)

class PlayerDataStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val playersDir = configDir.resolve("cobblemon-showcase").resolve("players")

    fun load(uuid: UUID): PlayerShowcaseData {
        val file = playersDir.resolve("$uuid.json")
        if (!file.exists()) return PlayerShowcaseData()
        return try {
            gson.fromJson(file.readText(), PlayerShowcaseData::class.java)
        } catch (e: Exception) {
            CobblemonShowcase.logger.error("Failed to load player data for $uuid", e)
            PlayerShowcaseData()
        }
    }

    fun save(uuid: UUID, data: PlayerShowcaseData) {
        playersDir.createDirectories()
        playersDir.resolve("$uuid.json").writeText(gson.toJson(data))
    }

    fun setTeam(uuid: UUID, team: String) {
        val data = load(uuid)
        data.team = team
        data.lastTeamSwitch = Instant.now().toString()
        save(uuid, data)
    }

    fun canSwitchTeam(uuid: UUID): Boolean {
        val data = load(uuid)
        val lastSwitch = data.lastTeamSwitch ?: return true
        return try {
            val last = Instant.parse(lastSwitch)
            val cooldown = Duration.ofHours(CobblemonShowcase.config.teamSwitchCooldownHours.toLong())
            Instant.now().isAfter(last.plus(cooldown))
        } catch (e: Exception) {
            true
        }
    }

    fun getTimeUntilSwitch(uuid: UUID): String {
        val data = load(uuid)
        val lastSwitch = data.lastTeamSwitch ?: return "now"
        return try {
            val last = Instant.parse(lastSwitch)
            val cooldown = Duration.ofHours(CobblemonShowcase.config.teamSwitchCooldownHours.toLong())
            val ready = last.plus(cooldown)
            val remaining = Duration.between(Instant.now(), ready)
            if (remaining.isNegative) "now"
            else "${remaining.toHours()}h ${remaining.toMinutesPart()}m"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
```

- [ ] **Step 3: Create BadgeReader**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/BadgeReader.kt`:

```kotlin
package com.cobblemonshowcase.data

import com.cobblemonshowcase.CobblemonShowcase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText

data class Badge(
    val id: String,
    val name: String,
    val description: String
)

data class BadgeFile(
    val badges: List<Badge> = emptyList()
)

object BadgeReader {
    private val gson: Gson = GsonBuilder().create()

    fun loadBadges(uuid: UUID): List<Badge> {
        val badgesDir = Paths.get(CobblemonShowcase.config.badgesDir)
        val file = badgesDir.resolve("$uuid.json")
        if (!file.exists()) return emptyList()
        return try {
            val data = gson.fromJson(file.readText(), BadgeFile::class.java)
            data.badges
        } catch (e: Exception) {
            CobblemonShowcase.logger.error("Failed to load badges for $uuid", e)
            emptyList()
        }
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home" && cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase && ./gradlew build`

Expected: BUILD SUCCESSFUL (will fail on missing commands/gui/mixin — that's fine, we just need compilation to not error on the data classes themselves. If it fails, create stub files for the missing classes referenced in CobblemonShowcase.kt and proceed.)

- [ ] **Step 5: Commit**

```bash
git add cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/config/ cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/
git commit -m "feat(showcase): add config, player data store, and badge reader"
```

---

### Task 7: cobblemon-showcase — CrossModReader

**Files:**
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/CrossModReader.kt`

- [ ] **Step 1: Create CrossModReader**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/CrossModReader.kt`:

```kotlin
package com.cobblemonshowcase.data

import com.cobblemonshowcase.CobblemonShowcase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import java.math.BigDecimal
import java.util.UUID

/**
 * Reads data from cobblemon-ranked, cobblemon-market, and cobblemon-economy
 * via JSON files and reflection. All methods return nullable/default values
 * if the source mod is not installed.
 */
object CrossModReader {
    private val gson: Gson = GsonBuilder().create()
    private val configDir = FabricLoader.getInstance().configDir

    // --- Ranked mod data ---

    data class RankedPlayerData(
        val name: String,
        val elo: Int = 1200,
        val wins: Int = 0,
        val losses: Int = 0,
        val lastBattleDate: String? = null
    )

    fun getEloData(uuid: UUID): RankedPlayerData? {
        val file = configDir.resolve("cobblemon-ranked").resolve("elo.json")
        if (!file.toFile().exists()) return null
        return try {
            val type = object : TypeToken<Map<String, RankedPlayerData>>() {}.type
            val all: Map<String, RankedPlayerData> = gson.fromJson(file.toFile().readText(), type)
            all[uuid.toString()]
        } catch (e: Exception) {
            CobblemonShowcase.logger.error("Failed to read ranked elo data", e)
            null
        }
    }

    fun getEloRank(uuid: UUID): Int? {
        val file = configDir.resolve("cobblemon-ranked").resolve("elo.json")
        if (!file.toFile().exists()) return null
        return try {
            val type = object : TypeToken<Map<String, RankedPlayerData>>() {}.type
            val all: Map<String, RankedPlayerData> = gson.fromJson(file.toFile().readText(), type)
            val sorted = all.entries.sortedByDescending { it.value.elo }
            val index = sorted.indexOfFirst { it.key == uuid.toString() }
            if (index >= 0) index + 1 else null
        } catch (e: Exception) {
            null
        }
    }

    data class TeamPokemonData(
        val species: String,
        val level: Int,
        val nickname: String?
    )

    data class SavedTeam(
        val team: List<TeamPokemonData>,
        val timestamp: String
    )

    fun getLastTeam(uuid: UUID): SavedTeam? {
        val file = configDir.resolve("cobblemon-ranked").resolve("teams").resolve("$uuid.json")
        if (!file.toFile().exists()) return null
        return try {
            gson.fromJson(file.toFile().readText(), SavedTeam::class.java)
        } catch (e: Exception) {
            CobblemonShowcase.logger.error("Failed to read team data for $uuid", e)
            null
        }
    }

    // --- Market mod data ---

    data class MarketPlayerSpend(
        val name: String,
        val totalSpend: Int = 0
    )

    fun getMarketSpend(uuid: UUID): Int {
        val file = configDir.resolve("cobblemon-market").resolve("player_spend.json")
        if (!file.toFile().exists()) return 0
        return try {
            val type = object : TypeToken<Map<String, MarketPlayerSpend>>() {}.type
            val all: Map<String, MarketPlayerSpend> = gson.fromJson(file.toFile().readText(), type)
            all[uuid.toString()]?.totalSpend ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // --- Economy mod data (reflection) ---

    fun getBalance(uuid: UUID): Int {
        return try {
            val loader = FabricLoader.getInstance()
            val entrypoints = loader.getEntrypointContainers("main", net.fabricmc.api.ModInitializer::class.java)
            val economyEntry = entrypoints.firstOrNull { it.provider.metadata.id == "cobblemon-economy" }
                ?: return 0
            val economyInstance = economyEntry.entrypoint
            val manager = economyInstance.javaClass.getMethod("getEconomyManager").invoke(economyInstance)
            val method = manager.javaClass.getMethod("getBalance", java.util.UUID::class.java)
            val balance = method.invoke(manager, uuid) as BigDecimal
            balance.toInt()
        } catch (e: Exception) {
            0
        }
    }

    // --- Minecraft stats ---

    fun getPlaytimeHours(player: ServerPlayer): Int {
        val ticks = player.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))
        return ticks / 20 / 3600 // ticks -> seconds -> hours
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/data/CrossModReader.kt
git commit -m "feat(showcase): add cross-mod data reader for ranked, market, economy"
```

---

### Task 8: cobblemon-showcase — ShowcaseGui

**Files:**
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/gui/ShowcaseGui.kt`

- [ ] **Step 1: Create ShowcaseGui**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/gui/ShowcaseGui.kt`:

```kotlin
package com.cobblemonshowcase.gui

import com.cobblemonshowcase.CobblemonShowcase
import com.cobblemonshowcase.data.BadgeReader
import com.cobblemonshowcase.data.CrossModReader
import com.mojang.authlib.GameProfile
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Optional
import java.util.UUID

class ShowcaseGui(
    private val viewer: ServerPlayer,
    private val targetUuid: UUID,
    private val targetName: String,
    private val server: MinecraftServer
) {
    fun open() {
        val gui = SimpleGui(MenuType.GENERIC_9x6, viewer, false)
        gui.title = Component.literal("$targetName's Profile")

        val playerData = CobblemonShowcase.playerDataStore.load(targetUuid)
        val eloData = CrossModReader.getEloData(targetUuid)
        val eloRank = CrossModReader.getEloRank(targetUuid)
        val lastTeam = CrossModReader.getLastTeam(targetUuid)
        val badges = BadgeReader.loadBadges(targetUuid)
        val balance = CrossModReader.getBalance(targetUuid)
        val marketSpend = CrossModReader.getMarketSpend(targetUuid)

        // Get playtime - need the target player to be online
        val targetPlayer = server.playerList.getPlayer(targetUuid)
        val playtimeHours = if (targetPlayer != null) CrossModReader.getPlaytimeHours(targetPlayer) else null

        // === Row 1: Identity ===
        fillRow(gui, 0)

        // Player head at slot 4
        val headBuilder = GuiElementBuilder(Items.PLAYER_HEAD)
            .setName(Component.literal(targetName))
        headBuilder.setComponent(
            net.minecraft.core.component.DataComponents.PROFILE,
            ResolvableProfile(Optional.of(targetName), Optional.of(targetUuid), com.mojang.authlib.properties.PropertyMap())
        )
        gui.setSlot(4, headBuilder.build())

        // Team affiliation at slot 6
        val teamName = playerData.team
        if (teamName != null) {
            gui.setSlot(6, GuiElementBuilder(Items.RED_BANNER)
                .setName(Component.literal("Team: $teamName"))
                .build())
        } else {
            gui.setSlot(6, GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                .setName(Component.literal("No Team"))
                .build())
        }

        // === Row 2: PVP Stats ===
        fillRow(gui, 1)

        val eloLore = if (eloData != null) {
            buildList {
                add(Component.literal("ELO: ${eloData.elo}"))
                add(Component.literal("Record: ${eloData.wins}W / ${eloData.losses}L"))
                if (eloRank != null) add(Component.literal("Rank: #$eloRank"))
            }
        } else {
            listOf(Component.literal("N/A — Ranked mod not found"))
        }
        gui.setSlot(13, GuiElementBuilder(Items.DIAMOND_SWORD)
            .setName(Component.literal("PVP Stats"))
            .setLore(eloLore)
            .hideDefaultTooltip()
            .build())

        // === Row 3: Economy & Activity ===
        fillRow(gui, 2)

        gui.setSlot(20, GuiElementBuilder(Items.GOLD_INGOT)
            .setName(Component.literal("Balance"))
            .setLore(listOf(Component.literal("$balance PokeDollars")))
            .build())

        gui.setSlot(22, GuiElementBuilder(Items.EMERALD)
            .setName(Component.literal("Market Spend"))
            .setLore(listOf(Component.literal("$marketSpend PokeDollars spent")))
            .build())

        val playtimeText = if (playtimeHours != null) "${playtimeHours}h" else "Offline"
        gui.setSlot(24, GuiElementBuilder(Items.CLOCK)
            .setName(Component.literal("Playtime"))
            .setLore(listOf(Component.literal(playtimeText)))
            .build())

        // === Row 4: Last PVP Team ===
        fillRow(gui, 3)

        if (lastTeam != null) {
            for (i in 0 until 6) {
                val slot = 29 + i
                if (i < lastTeam.team.size) {
                    val pokemon = lastTeam.team[i]
                    val displayName = pokemon.nickname ?: pokemon.species
                    gui.setSlot(slot, GuiElementBuilder(Items.FIRE_CHARGE)
                        .setName(Component.literal(displayName))
                        .setLore(listOf(
                            Component.literal("Species: ${pokemon.species}"),
                            Component.literal("Level: ${pokemon.level}")
                        ))
                        .build())
                } else {
                    gui.setSlot(slot, GuiElementBuilder(Items.BARRIER)
                        .setName(Component.literal("Empty Slot"))
                        .build())
                }
            }
        } else {
            for (i in 0 until 6) {
                gui.setSlot(29 + i, GuiElementBuilder(Items.BARRIER)
                    .setName(Component.literal("No PVP Team"))
                    .build())
            }
        }

        // === Rows 5-6: Badges ===
        fillRow(gui, 4)
        fillRow(gui, 5)

        for (i in 0 until 18) {
            val slot = 36 + i
            if (i < badges.size) {
                val badge = badges[i]
                gui.setSlot(slot, GuiElementBuilder(Items.NETHER_STAR)
                    .setName(Component.literal(badge.name))
                    .setLore(listOf(Component.literal(badge.description)))
                    .build())
            } else {
                gui.setSlot(slot, GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal(" "))
                    .build())
            }
        }

        gui.open()
    }

    private fun fillRow(gui: SimpleGui, row: Int) {
        for (i in 0 until 9) {
            gui.setSlot(row * 9 + i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" "))
                .build())
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/gui/ShowcaseGui.kt
git commit -m "feat(showcase): add 6-row profile GUI with stats, team, badges"
```

---

### Task 9: cobblemon-showcase — Commands

**Files:**
- Create: `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/commands/ShowcaseCommands.kt`

- [ ] **Step 1: Create ShowcaseCommands**

Create `cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/commands/ShowcaseCommands.kt`:

```kotlin
package com.cobblemonshowcase.commands

import com.cobblemonshowcase.CobblemonShowcase
import com.cobblemonshowcase.gui.ShowcaseGui
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component

object ShowcaseCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("showcase")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes { ctx ->
                        val viewer = ctx.source.playerOrException
                        val target = EntityArgument.getPlayer(ctx, "player")
                        ShowcaseGui(viewer, target.uuid, target.name.string, viewer.server).open()
                        1
                    }
                )
        )

        dispatcher.register(
            Commands.literal("team")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .suggests { _, builder ->
                        CobblemonShowcase.config.teams.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val player = ctx.source.playerOrException
                        val teamName = StringArgumentType.getString(ctx, "name")
                        joinTeam(player, teamName)
                        1
                    }
                )
        )

        dispatcher.register(
            Commands.literal("team")
                .then(Commands.literal("set")
                    .requires { it.hasPermission(4) }
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .suggests { _, builder ->
                                CobblemonShowcase.config.teams.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes { ctx ->
                                val target = EntityArgument.getPlayer(ctx, "player")
                                val teamName = StringArgumentType.getString(ctx, "name")
                                adminSetTeam(ctx.source, target, teamName)
                                1
                            }
                        )
                    )
                )
        )
    }

    private fun joinTeam(player: net.minecraft.server.level.ServerPlayer, teamName: String) {
        val config = CobblemonShowcase.config
        val store = CobblemonShowcase.playerDataStore

        if (teamName !in config.teams) {
            player.sendSystemMessage(Component.literal(
                "[Showcase] Invalid team. Options: ${config.teams.joinToString(", ")}"))
            return
        }

        if (!store.canSwitchTeam(player.uuid)) {
            val timeLeft = store.getTimeUntilSwitch(player.uuid)
            player.sendSystemMessage(Component.literal(
                "[Showcase] You can switch teams in $timeLeft."))
            return
        }

        store.setTeam(player.uuid, teamName)
        player.sendSystemMessage(Component.literal("[Showcase] You joined Team $teamName!"))
    }

    private fun adminSetTeam(source: CommandSourceStack, target: net.minecraft.server.level.ServerPlayer, teamName: String) {
        val config = CobblemonShowcase.config

        if (teamName !in config.teams) {
            source.sendSystemMessage(Component.literal(
                "[Showcase] Invalid team. Options: ${config.teams.joinToString(", ")}"))
            return
        }

        CobblemonShowcase.playerDataStore.setTeam(target.uuid, teamName)
        source.sendSystemMessage(Component.literal(
            "[Showcase] Set ${target.name.string}'s team to $teamName."))
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add cobblemon-showcase/src/main/kotlin/com/cobblemonshowcase/commands/ShowcaseCommands.kt
git commit -m "feat(showcase): add /showcase and /team commands"
```

---

### Task 10: cobblemon-showcase — Chat mixin for clickable names

**Files:**
- Create: `cobblemon-showcase/src/main/java/com/cobblemonshowcase/mixin/ServerPlayerMixin.java`

- [ ] **Step 1: Create the mixin**

Mixins must be written in Java. This mixin intercepts `ServerPlayer.createPlayerChatMessage()` or the chat decoration to add click events. However, the simplest approach is to mixin into the chat message decoration.

Create `cobblemon-showcase/src/main/java/com/cobblemonshowcase/mixin/ServerPlayerMixin.java`:

```java
package com.cobblemonshowcase.mixin;

import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.FilteredText;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowcase$decorateChat(PlayerChatMessage message, CallbackInfo ci) {
        // Replace the broadcast with a custom one that has clickable player name
        ServerPlayer sender = this.player;
        var server = sender.getServer();
        if (server == null) return;

        String senderName = sender.getName().getString();
        MutableComponent nameComponent = Component.literal("<" + senderName + ">")
            .withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/showcase " + senderName))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view profile")))
            );

        Component content = message.decoratedContent();
        MutableComponent fullMessage = Component.empty()
            .append(nameComponent)
            .append(Component.literal(" "))
            .append(content);

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            recipient.sendSystemMessage(fullMessage);
        }

        ci.cancel();
    }
}
```

- [ ] **Step 2: Build the full showcase mod**

Run: `export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home" && cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase && ./gradlew build`

Expected: BUILD SUCCESSFUL

If the mixin class doesn't compile due to API differences (e.g., `broadcastChatMessage` method name is different in 1.21.1 mappings), check the actual method name by inspecting the `ServerGamePacketListenerImpl` class and adjust the `@Inject` target accordingly.

- [ ] **Step 3: Commit**

```bash
git add cobblemon-showcase/src/main/java/com/cobblemonshowcase/mixin/ServerPlayerMixin.java
git commit -m "feat(showcase): add chat mixin for clickable player names"
```

---

### Task 11: Build all three mods and deploy

**Files:**
- No new files

- [ ] **Step 1: Build cobblemon-ranked**

```bash
export JAVA_HOME="/Users/almutwakel/Library/Application Support/ModrinthApp/meta/java_versions/zulu21.42.19-ca-jre21.0.7-macosx_aarch64/zulu-21.jre/Contents/Home"
cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-ranked && ./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Build cobblemon-market**

```bash
cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market && ./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build cobblemon-showcase**

```bash
cd /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase && ./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Deploy all three JARs**

```bash
cp /Users/almutwakel/Documents/Projects/minecraft/cobblemon-ranked/build/libs/cobblemon-ranked-1.0.0.jar "/Users/almutwakel/Library/Application Support/ModrinthApp/profiles/cobblemon_market/mods/"
cp /Users/almutwakel/Documents/Projects/minecraft/cobblemon-market/build/libs/cobblemon-market-1.0.0.jar "/Users/almutwakel/Library/Application Support/ModrinthApp/profiles/cobblemon_market/mods/"
cp /Users/almutwakel/Documents/Projects/minecraft/cobblemon-showcase/build/libs/cobblemon-showcase-1.0.0.jar "/Users/almutwakel/Library/Application Support/ModrinthApp/profiles/cobblemon_market/mods/"
```

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: showcase mod complete, ranked team saving, market leaderboard"
```
