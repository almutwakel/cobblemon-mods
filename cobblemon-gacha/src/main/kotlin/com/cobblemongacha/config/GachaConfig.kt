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
