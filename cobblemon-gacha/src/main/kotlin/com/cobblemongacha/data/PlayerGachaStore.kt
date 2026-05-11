package com.cobblemongacha.data

import com.cobblemongacha.CobblemonGacha
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Per-player gacha state, persisted as `config/cobblemon-gacha/players.json`. Pattern mirrors
 * `cobblemon-ranked`'s `EloStore`: load on startup, mutate in-memory, call `save()` after each grant.
 */
class PlayerGachaStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-gacha").resolve("players.json")
    private val players: MutableMap<String, PlayerGachaData> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, PlayerGachaData>>() {}.type
            val loaded: MutableMap<String, PlayerGachaData> = gson.fromJson(file.readText(), type)
            players.clear()
            players.putAll(loaded)
        } catch (e: Exception) {
            CobblemonGacha.logger.error("Failed to load player gacha data", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-gacha").createDirectories()
        file.writeText(gson.toJson(players))
    }

    fun getOrCreate(uuid: UUID, name: String): PlayerGachaData =
        players.getOrPut(uuid.toString()) { PlayerGachaData(name = name) }.also {
            if (it.name != name) it.name = name
        }

    fun get(uuid: UUID): PlayerGachaData? = players[uuid.toString()]

    fun getAll(): Map<String, PlayerGachaData> = players.toMap()
}
