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
        // Avoid Kotlin's `sortedByDescending` because its synthetic `$$inlined$sortedByDescending$1`
        // helper class fails to load under NeoForge + Sinytra Connector's class transformation
        // pipeline. SAM-converting to a Comparator goes through invokedynamic / LambdaMetafactory
        // and produces no synthetic inner class.
        val result = ArrayList<Pair<String, PlayerEloData>>(players.size)
        for ((key, value) in players) result.add(key to value)
        result.sortWith(Comparator { a, b -> b.second.elo.compareTo(a.second.elo) })
        return result
    }

    /**
     * Sets a player's ELO. If no record exists yet, creates one with the given [name];
     * the prior implementation silently no-op'd on missing records, which made
     * `/ranked admin setelo` look successful while doing nothing.
     */
    fun setElo(uuid: UUID, name: String, elo: Int) {
        val data = getOrCreate(uuid, name)
        data.elo = elo.coerceAtLeast(CobblemonRanked.config.minimumElo)
        save()
    }
}
