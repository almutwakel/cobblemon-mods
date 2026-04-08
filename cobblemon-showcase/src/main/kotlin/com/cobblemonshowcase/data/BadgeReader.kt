package com.cobblemonshowcase.data

import com.cobblemonshowcase.CobblemonShowcase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
