package com.cobblemonranked.data

import com.cobblemonranked.CobblemonRanked
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class EloStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-ranked").resolve("elo.json")
    private val players: MutableMap<String, PlayerEloData> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, PlayerEloData>>() {}.type
            val loaded: MutableMap<String, PlayerEloData> = gson.fromJson(file.readText(), type)
            players.clear()
            players.putAll(loaded)
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load ELO data", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-ranked").createDirectories()
        file.writeText(gson.toJson(players))
    }

    fun getOrCreate(uuid: UUID, name: String): PlayerEloData {
        return players.getOrPut(uuid.toString()) {
            PlayerEloData(name = name, elo = CobblemonRanked.config.startingElo)
        }
    }

    fun get(uuid: UUID): PlayerEloData? = players[uuid.toString()]

    fun getAll(): Map<String, PlayerEloData> = players.toMap()

    fun getLeaderboard(): List<Pair<String, PlayerEloData>> {
        return players.entries
            .sortedByDescending { it.value.elo }
            .map { it.key to it.value }
    }

    fun setElo(uuid: UUID, elo: Int) {
        players[uuid.toString()]?.let {
            it.elo = elo.coerceAtLeast(CobblemonRanked.config.minimumElo)
            save()
        }
    }
}
