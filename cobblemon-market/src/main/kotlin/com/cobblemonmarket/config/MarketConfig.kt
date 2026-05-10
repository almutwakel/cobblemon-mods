package com.cobblemonmarket.config

import com.cobblemonmarket.CobblemonMarket
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Server-wide market knobs.
 *
 * `restockRatePerHour` is applied once per real-time hour as
 *   stock += restockRatePerHour × (baseStock − stock)
 * so stock both refills (after heavy buying) and bleeds off (after heavy dumping)
 * back toward the per-item `baseStock`. 0.07 = ~7% gap closure per hour.
 */
data class MarketConfig(
    val restockRatePerHour: Double = 0.07,
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
