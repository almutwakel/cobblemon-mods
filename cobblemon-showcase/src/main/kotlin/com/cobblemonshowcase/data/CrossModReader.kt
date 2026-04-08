package com.cobblemonshowcase.data

import com.cobblemonshowcase.CobblemonShowcase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import java.math.BigDecimal
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Reads data from cobblemon-ranked, cobblemon-market, and cobblemon-economy
 * via JSON files and reflection. All methods return nullable/default values
 * if the source mod is not installed.
 */
object CrossModReader {
    private val gson: Gson = GsonBuilder().create()
    private val configDir = FabricLoader.getInstance().configDir

    // --- Ranked mod data ---

    data class RankedPlayerData(
        val name: String,
        val elo: Int = 1200,
        val wins: Int = 0,
        val losses: Int = 0,
        val lastBattleDate: String? = null
    )

    fun getEloData(uuid: UUID): RankedPlayerData? {
        val file = configDir.resolve("cobblemon-ranked").resolve("elo.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<Map<String, RankedPlayerData>>() {}.type
            val all: Map<String, RankedPlayerData> = gson.fromJson(file.readText(), type)
            all[uuid.toString()]
        } catch (e: Exception) {
            CobblemonShowcase.logger.error("Failed to read ranked elo data", e)
            null
        }
    }

    fun getEloRank(uuid: UUID): Int? {
        val file = configDir.resolve("cobblemon-ranked").resolve("elo.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<Map<String, RankedPlayerData>>() {}.type
            val all: Map<String, RankedPlayerData> = gson.fromJson(file.readText(), type)
            val sorted = all.entries.sortedByDescending { it.value.elo }
            val index = sorted.indexOfFirst { it.key == uuid.toString() }
            if (index >= 0) index + 1 else null
        } catch (e: Exception) {
            null
        }
    }

    data class TeamPokemonData(
        val species: String,
        val level: Int,
        val nickname: String?
    )

    data class SavedTeam(
        val team: List<TeamPokemonData>,
        val timestamp: String
    )

    fun getLastTeam(uuid: UUID): SavedTeam? {
        val file = configDir.resolve("cobblemon-ranked").resolve("teams").resolve("$uuid.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), SavedTeam::class.java)
        } catch (e: Exception) {
            CobblemonShowcase.logger.error("Failed to read team data for $uuid", e)
            null
        }
    }

    // --- Market mod data ---

    data class MarketPlayerSpend(
        val name: String,
        val totalSpend: Int = 0
    )

    fun getMarketSpend(uuid: UUID): Int {
        val file = configDir.resolve("cobblemon-market").resolve("player_spend.json")
        if (!file.exists()) return 0
        return try {
            val type = object : TypeToken<Map<String, MarketPlayerSpend>>() {}.type
            val all: Map<String, MarketPlayerSpend> = gson.fromJson(file.readText(), type)
            all[uuid.toString()]?.totalSpend ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // --- Economy mod data (reflection) ---

    fun getBalance(uuid: UUID): Int {
        return try {
            val loader = FabricLoader.getInstance()
            val entrypoints = loader.getEntrypointContainers("main", ModInitializer::class.java)
            val economyEntry = entrypoints.firstOrNull { it.provider.metadata.id == "cobblemon-economy" }
                ?: return 0
            val economyInstance = economyEntry.entrypoint
            val manager = economyInstance.javaClass.getMethod("getEconomyManager").invoke(economyInstance)
            val method = manager.javaClass.getMethod("getBalance", java.util.UUID::class.java)
            val balance = method.invoke(manager, uuid) as BigDecimal
            balance.toInt()
        } catch (e: Exception) {
            0
        }
    }

    // --- Minecraft stats ---

    fun getPlaytimeHours(player: ServerPlayer): Int {
        val ticks = player.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))
        return ticks / 20 / 3600
    }
}
