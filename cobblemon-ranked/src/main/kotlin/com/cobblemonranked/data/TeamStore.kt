package com.cobblemonranked.data

import com.cobblemonranked.CobblemonRanked
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class TeamPokemonData(
    val species: String,
    val level: Int,
    val nickname: String?
)

data class SavedTeam(
    val team: List<TeamPokemonData>,
    val timestamp: String
)

class TeamStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val teamsDir = configDir.resolve("cobblemon-ranked").resolve("teams")

    fun saveTeam(uuid: UUID, pokemonList: List<Pokemon>) {
        teamsDir.createDirectories()
        val data = SavedTeam(
            team = pokemonList.map { pokemon ->
                TeamPokemonData(
                    species = pokemon.species.name,
                    level = pokemon.level,
                    nickname = pokemon.nickname?.string
                )
            },
            timestamp = Instant.now().toString()
        )
        teamsDir.resolve("$uuid.json").writeText(gson.toJson(data))
    }

    fun loadTeam(uuid: UUID): SavedTeam? {
        val file = teamsDir.resolve("$uuid.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), SavedTeam::class.java)
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load team for $uuid", e)
            null
        }
    }
}
