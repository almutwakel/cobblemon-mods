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
