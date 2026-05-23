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

/**
 * Per-item market parameters.
 *
 * - `baseBuyPrice` / `baseSellPrice` — prices when stock equals `baseStock` (the natural
 *   spread between them is the merchant's margin).
 * - `baseStock` — target/equilibrium quantity. Restock pulls stock toward this value.
 * - `elasticity` — how sharply price reacts to deviation from `baseStock`.
 *   1.0 ≈ inversely proportional, <1 = stable (consumables like Poké Balls), >1 = volatile (rare items).
 * - `maxStockMultiplier` — sells are rejected when stock would exceed `baseStock × maxStockMultiplier`.
 */
data class ItemEntry(
    val baseBuyPrice: Int,
    val baseSellPrice: Int,
    val baseStock: Int = 100,
    val elasticity: Double = 1.0,
    val maxStockMultiplier: Double = 10.0,
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

    // baseBuyPrice ≈ 3× baseSellPrice mirrors the previous spread; tune in items.json.
    private fun defaultItems(): Map<String, ItemEntry> = mapOf(
        "cobblemon:rare_candy" to ItemEntry(baseBuyPrice = 6000, baseSellPrice = 2000, elasticity = 2.0),
        "cobblemon:ultra_ball" to ItemEntry(baseBuyPrice = 900, baseSellPrice = 300, elasticity = 0.5),
        "cobblemon:great_ball" to ItemEntry(baseBuyPrice = 300, baseSellPrice = 100, elasticity = 1.0),
        "cobblemon:poke_ball" to ItemEntry(baseBuyPrice = 90, baseSellPrice = 30, elasticity = 0.3),
        "cobblemon:revive" to ItemEntry(baseBuyPrice = 1500, baseSellPrice = 500, elasticity = 1.0),
        // High-volume staple. cobblemon-carrots' healer buys at current price when the player
        // is short carrots — replaces the flat `carrotPrice` config when this entry exists.
        "minecraft:carrot" to ItemEntry(baseBuyPrice = 6, baseSellPrice = 3, baseStock = 1000, elasticity = 0.7),
    )
}
