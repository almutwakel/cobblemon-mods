package com.cobblemonmarket.data

import com.cobblemonmarket.CobblemonMarket
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class PlayerSpendData(
    val name: String,
    var totalSpend: Int = 0
)

class PlayerSpendStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-market").resolve("player_spend.json")
    private val players: MutableMap<String, PlayerSpendData> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, PlayerSpendData>>() {}.type
            val loaded: MutableMap<String, PlayerSpendData> = gson.fromJson(file.readText(), type)
            players.clear()
            players.putAll(loaded)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load player spend data", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-market").createDirectories()
        file.writeText(gson.toJson(players))
    }

    fun recordSpend(uuid: UUID, name: String, amount: Int) {
        val data = players.getOrPut(uuid.toString()) { PlayerSpendData(name = name) }
        data.totalSpend += amount
        save()
    }

    fun getSpend(uuid: UUID): PlayerSpendData? = players[uuid.toString()]

    fun getAllKnownUuids(): Set<String> = players.keys.toSet()

    fun getAll(): Map<String, PlayerSpendData> = players.toMap()
}
