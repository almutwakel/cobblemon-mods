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
