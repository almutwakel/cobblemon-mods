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
