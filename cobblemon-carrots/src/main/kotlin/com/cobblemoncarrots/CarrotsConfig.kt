package com.cobblemoncarrots

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Carrot healing knobs. JSON file at `config/cobblemon-carrots/config.json`. Generated on
 * first boot with the defaults below; edit and restart to apply.
 *
 * Healing model:
 *   - Right-clicking a non-fainted Pokémon with a carrot heals [hpPerCarrot] HP and consumes
 *     one carrot.
 *   - Reviving from inventory is disabled by default ([allowInventoryRevive] = false). Use a
 *     Poké Healer for revives. Set to true to bring back the shift-right-click flow.
 *   - A Poké Healer block charges at least [healerMinCarrots] carrots per use (or more if the
 *     party deficit is bigger) and revives any fainted mons at [healerReviveCarrotCost] each.
 *   - The healer treats fainted mons as starting at [hpPerCarrot] HP for the heal-portion
 *     deficit calculation (since the revive cost includes that first chunk).
 */
data class CarrotsConfig(
    val hpPerCarrot: Int = 30,
    val healerReviveCarrotCost: Int = 3,
    val healerMinCarrots: Int = 4,
    val carrotPrice: Int = 5,
    val allowInventoryRevive: Boolean = false,
    val inventoryReviveCarrotCost: Int = 4,
) {
    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-carrots/config")
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private const val FILE_NAME = "config.json"
        private const val DIR_NAME = "cobblemon-carrots"

        fun load(configDir: Path): CarrotsConfig {
            val dir = configDir.resolve(DIR_NAME)
            Files.createDirectories(dir)
            val file = dir.resolve(FILE_NAME)
            if (!Files.exists(file)) {
                val defaults = CarrotsConfig()
                Files.writeString(file, gson.toJson(defaults))
                log.info("Wrote default config to $file")
                return defaults
            }
            return try {
                gson.fromJson(Files.readString(file), CarrotsConfig::class.java) ?: CarrotsConfig()
            } catch (e: Exception) {
                log.error("Failed to parse $file; using defaults", e)
                CarrotsConfig()
            }
        }
    }
}
