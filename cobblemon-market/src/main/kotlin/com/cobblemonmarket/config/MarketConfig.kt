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
    val recoveryRatePerHour: Double = 0.04,
    val factorFloor: Double = 0.10,
    val factorCeiling: Double = 1.00,
    val sellDecay: Double = 0.98,
    val buyGrowth: Double = 1.02,
    val transactionWindowSize: Int = 50,
    val leaderboardSize: Int = 10,
    /** Cap on price-history entries per item (one entry per /market buy|sell batch). */
    val priceHistorySize: Int = 500,
    /**
     * IANA timezone used to decide calendar-day boundaries when grouping ticks into candles.
     * Defaults to "America/New_York" (EST/EDT) — change if your players live elsewhere.
     */
    val priceHistoryTimeZone: String = "America/New_York",
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
