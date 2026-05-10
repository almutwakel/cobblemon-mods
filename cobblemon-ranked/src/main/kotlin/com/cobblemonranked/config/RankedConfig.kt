package com.cobblemonranked.config

import com.cobblemonranked.CobblemonRanked
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A single teleport point for a ranked-match arena.
 *
 * `world` is a namespaced dimension id (`minecraft:overworld`, `minecraft:the_nether`, etc.).
 * `yaw`/`pitch` set the player's facing on arrival — typically each player faces the other.
 */
data class ArenaPos(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val world: String = "minecraft:overworld",
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f
)

data class RankedConfig(
    val startingElo: Int = 1200,
    val minimumElo: Int = 1000,
    val kFactor: Int = 32,
    val levelCap: Int = 50,
    val maxLegendaries: Int = 1,
    val forcesPerDayPerPair: Int = 1,
    val decayEnabled: Boolean = true,
    val leaderboardSize: Int = 10,
    /**
     * Optional arena positions. When **both** are non-null, players are teleported there
     * at battle start and back to their original locations on victory/flee/cancel.
     * Leave either null to disable arena teleport (battles run wherever players are).
     */
    val arenaPos1: ArenaPos? = null,
    val arenaPos2: ArenaPos? = null
) {
    fun isArenaConfigured(): Boolean = arenaPos1 != null && arenaPos2 != null
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
