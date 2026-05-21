package com.cobblemongacha.config

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.EggPools
import com.cobblemongacha.data.EggSpecies
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Loads (or migrates) the Cobbreeding egg-species pools.
 *
 * On first boot, parses the bundled `resources/egg_pools.csv` (Cobblemon species CSV with
 * columns `Pokemon,Rarity,Hidden Ability Needed`) into `config/cobblemon-gacha/egg_pools.json`.
 * Subsequent boots read the JSON, so admins can edit the pools without touching the jar.
 *
 * Species names from the CSV are normalised to Cobblemon ids: lowercase, with hyphens kept
 * (e.g. "Jangmo-o" → "jangmo-o", "Mr. Mime" → "mr_mime"). Annotation suffixes like
 * "(both forms)" are stripped from the id and stored in `EggSpecies.notes`.
 */
object EggPoolLoader {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun loadAll(configDir: Path): EggPools {
        val dir = configDir.resolve("cobblemon-gacha")
        dir.createDirectories()
        val jsonFile = dir.resolve("egg_pools.json")
        return if (jsonFile.exists()) {
            loadJson(jsonFile)
        } else {
            val csv = readBundledCsv()
            val pools = parseCsv(csv)
            jsonFile.writeText(gson.toJson(pools.byTier))
            CobblemonGacha.logger.info(
                "Migrated bundled egg pool CSV to {} ({} tiers, {} species total)",
                jsonFile, pools.byTier.size, pools.byTier.values.sumOf { it.size },
            )
            pools
        }
    }

    private fun loadJson(path: Path): EggPools {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<EggSpecies>>>() {}.type
            val map: Map<String, List<EggSpecies>> = gson.fromJson(path.readText(), type)
            EggPools(map)
        } catch (e: Exception) {
            CobblemonGacha.logger.error("Failed to read egg_pools.json, falling back to bundled CSV", e)
            parseCsv(readBundledCsv())
        }
    }

    /**
     * Parse the CSV format: header row `Pokemon,Rarity,Hidden Ability Needed`, then one species
     * per line. The third column is "Yes" / "No" / free-form (e.g. "Own Tempo", "Yes (both forms)")
     * — anything starting with "Yes" or "Own" (Rockruff's Dusk-form HA) counts as HA-available.
     */
    fun parseCsv(csv: String): EggPools {
        val byTier = mutableMapOf<String, MutableList<EggSpecies>>()
        val lines = csv.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.size < 2) return EggPools(emptyMap())
        for (line in lines.drop(1)) {
            val cells = splitCsvLine(line)
            val name = cells.getOrNull(0)?.trim().orEmpty()
            val rarity = cells.getOrNull(1)?.trim().orEmpty()
            val haRaw = cells.getOrNull(2)?.trim().orEmpty()
            if (name.isEmpty() || rarity.isEmpty()) continue
            val key = EggPools.normaliseTier(rarity)
            val id = speciesId(name)
            val hasHA = haRaw.startsWith("Yes", ignoreCase = true) ||
                haRaw.startsWith("Own", ignoreCase = true)
            byTier.getOrPut(key) { mutableListOf() }.add(
                EggSpecies(id = id, hasHiddenAbility = hasHA, notes = haRaw),
            )
        }
        return EggPools(byTier.mapValues { it.value.toList() })
    }

    /**
     * Normalise the Pokémon name from the CSV into a Cobblemon species id.
     * Strips parenthetical annotations ("(both forms)") and trailing space, lowercases, and
     * replaces internal spaces/dots with underscores. Hyphens are preserved (jangmo-o, mr-mime).
     */
    private fun speciesId(name: String): String {
        val cleaned = name.replace(Regex("\\s*\\(.*?\\)\\s*"), "").trim()
        return cleaned.lowercase()
            .replace('.', '_')
            .replace(' ', '_')
            .replace(Regex("_+"), "_")
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(buf.toString()); buf.clear() }
                else -> buf.append(c)
            }
        }
        out.add(buf.toString())
        return out
    }

    private fun readBundledCsv(): String {
        val stream = EggPoolLoader::class.java.getResourceAsStream("/egg_pools.csv")
            ?: error("Bundled egg_pools.csv not found in jar")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
