package com.cobblemongacha.config

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.LootTier

/**
 * Parses Society Sunlit-style loot CSVs into structured `LootTable` objects.
 *
 * Columns (CSV header is consumed and discarded): `Tier, Item, Chance %, Notes, <trailing empty>`.
 * Blank `Tier` cells inherit from the row above (the CSVs use this for compactness).
 * `TOTAL` rows are recognised and stored as `totalWeightPct` but never become a `LootEntry`.
 * The `Item` cell is parsed into a list of `ItemSpec`s via [parseItemLabel].
 */
object LootTableLoader {

    private val log = org.slf4j.LoggerFactory.getLogger("cobblemon-gacha/loot-loader")

    private val KNOWN_ITEMS: List<Pair<String, String>> = listOf(
        "Master Ball" to "cobblemon:master_ball",
        "Ultra Ball" to "cobblemon:ultra_ball",
        "Great Ball" to "cobblemon:great_ball",
        "Quick Ball" to "cobblemon:quick_ball",
        "Quick balls" to "cobblemon:quick_ball",
        "Poké Ball" to "cobblemon:poke_ball",
        "Poke Ball" to "cobblemon:poke_ball",
        "Max Revive" to "cobblemon:max_revive",
        "Max Potion" to "cobblemon:max_potion",
        "Revive" to "cobblemon:revive",
        "Exp Candy XL" to "cobblemon:exp_candy_xl",
        "EXP Candy XL" to "cobblemon:exp_candy_xl",
        "Exp Candy L" to "cobblemon:exp_candy_l",
        "EXP Candy L" to "cobblemon:exp_candy_l",
        "EXP candy XL" to "cobblemon:exp_candy_xl",
        "Exp Candy M" to "cobblemon:exp_candy_m",
        "Exp Candy S" to "cobblemon:exp_candy_s",
        "Rare Candy" to "cobblemon:rare_candy",
        "Rare candy" to "cobblemon:rare_candy",
        "Lucky Egg" to "cobblemon:lucky_egg",
        "Lucky egg" to "cobblemon:lucky_egg",
        "Exp Share" to "cobblemon:exp_share",
        "EXP share" to "cobblemon:exp_share",
        "Bottle Cap" to "cobblemon:bottle_cap",
        "Gold Bottle Cap" to "cobblemon:gold_bottle_cap",
        "Ability Patch" to "cobblemon:ability_patch",
        "Focus" to "cobblemon:focus_sash",
        "Leftovers" to "cobblemon:leftovers",
        "PP Up" to "cobblemon:pp_up",
        "Nature Mint" to "cobblemon:nature_mint",
        "Nature mints" to "cobblemon:nature_mint",
        "Nature mint" to "cobblemon:nature_mint",
        "Mint seed" to "minecraft:wheat_seeds",
        "Silk Touch Book" to "minecraft:enchanted_book",
        "Nether wart" to "minecraft:nether_wart",
        "Evolution Stone" to "cobblemon:fire_stone",
        "EV Vitamin" to "cobblemon:hp_up",
        "IV Candy" to "cobblemon:rare_candy",
        "Bee egg" to "minecraft:egg",
    )

    fun parseCsv(tier: KeyTier, csv: String): LootTable {
        val lines = csv.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        if (lines.size < 2) return LootTable(tier, 0.0, emptyList())
        val dataLines = lines.drop(1)

        val entries = mutableListOf<LootEntry>()
        var currentTier: LootTier = LootTier.Floor
        var totalWeight = 0.0
        var tbdCounter = 1

        for (line in dataLines) {
            val cells = splitCsvLine(line)
            val tierCell = cells.getOrNull(0)?.trim().orEmpty()
            val itemCell = cells.getOrNull(1)?.trim().orEmpty()
            val pctCell = cells.getOrNull(2)?.trim().orEmpty()
            val notesCell = cells.getOrNull(3)?.trim().orEmpty()

            if (itemCell.equals("TOTAL", ignoreCase = true)) {
                totalWeight = parsePct(pctCell)
                continue
            }

            if (tierCell.isNotEmpty()) {
                currentTier = parseTier(tierCell) ?: currentTier
            }
            val weight = parsePct(pctCell)

            val items: List<ItemSpec> = parseItemLabel(itemCell, weight, tbdCounter)
            if (itemCell.isBlank()) tbdCounter++

            entries.add(LootEntry(
                lootTier = currentTier,
                label = if (itemCell.isBlank()) "TBD Ultra Reward #${tbdCounter - 1}" else itemCell,
                weightPct = weight,
                items = items,
                notes = notesCell,
            ))
        }

        return LootTable(tier, totalWeight, entries)
    }

    private fun parseTier(s: String): LootTier? = when (s.lowercase()) {
        "floor" -> LootTier.Floor
        "mid" -> LootTier.Mid
        "high" -> LootTier.High
        "jackpot" -> LootTier.Jackpot
        else -> null
    }

    private fun parsePct(s: String): Double = s.removeSuffix("%").trim().toDoubleOrNull() ?: 0.0

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

    private fun parseItemLabel(label: String, weight: Double, tbdIndex: Int): List<ItemSpec> {
        if (label.isBlank()) {
            return listOf(ItemSpec.Placeholder("tbd_ultra", "TBD Ultra Reward #$tbdIndex", 1))
        }
        val count = "^\\s*(\\d+)".toRegex().find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val lower = label.lowercase()

        if (lower.contains("ultra key")) return listOf(ItemSpec.GachaKeyRef(KeyTier.ULTRA, count))
        if (lower.contains("rare key"))  return listOf(ItemSpec.GachaKeyRef(KeyTier.RARE, count))
        if (lower.contains("common key")) return listOf(ItemSpec.GachaKeyRef(KeyTier.COMMON, count))

        if (lower.contains("egg") && !lower.contains("lucky egg") && !lower.contains("bee egg")) {
            return listOf(ItemSpec.Placeholder("pokemon_egg", label, 1))
        }

        if (lower.contains("voucher") || lower.contains("fragment") || lower.contains("monument")) {
            return listOf(ItemSpec.Placeholder("voucher", label, 1))
        }

        val sortedKnown = KNOWN_ITEMS.sortedByDescending { it.first.length }
        for ((needle, id) in sortedKnown) {
            if (lower.contains(needle.lowercase())) {
                return listOf(ItemSpec.Vanilla(id, count))
            }
        }

        log.warn("LootTableLoader: unknown item label '{}' (weight={}); using TBD placeholder", label, weight)
        return listOf(ItemSpec.Placeholder("tbd_ultra", label, 1))
    }

    private object ItemSpecAdapter // placeholder; Task 6 implements

    private val gson: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .setPrettyPrinting()
            .create()
    }

    /**
     * Loads (or migrates) all three loot tables for the running mod.
     *
     * For each tier, prefers the on-disk JSON at `<configDir>/cobblemon-gacha/tables/<tier>.json`.
     * If that file is missing, parses the bundled CSV from the jar's resources and writes the
     * resulting `LootTable` to disk as JSON.
     */
    fun loadAll(configDir: java.nio.file.Path): Map<KeyTier, LootTable> {
        val tablesDir = configDir.resolve("cobblemon-gacha").resolve("tables")
        java.nio.file.Files.createDirectories(tablesDir)
        val out = mutableMapOf<KeyTier, LootTable>()
        for (tier in KeyTier.entries) {
            val jsonFile = tablesDir.resolve("${tier.key}.json")
            val table = if (java.nio.file.Files.exists(jsonFile)) {
                loadJson(tier, jsonFile)
            } else {
                val csv = readBundledCsv(tier)
                val parsed = parseCsv(tier, csv)
                writeJson(parsed, jsonFile)
                CobblemonGacha.logger.info("Migrated bundled {} CSV to {}", tier.key, jsonFile)
                parsed
            }
            out[tier] = table
        }
        return out
    }

    private fun loadJson(tier: KeyTier, path: java.nio.file.Path): LootTable {
        return try {
            gson.fromJson(java.nio.file.Files.readString(path), LootTable::class.java)
        } catch (e: Exception) {
            CobblemonGacha.logger.error("Failed to read {} table json, falling back to bundled CSV", tier.key, e)
            val csv = readBundledCsv(tier)
            parseCsv(tier, csv)
        }
    }

    /**
     * NOTE: Without the `ItemSpecAdapter` registered (next task), Gson cannot deserialise the
     * sealed `ItemSpec` hierarchy. Task 6 wires that in.
     */
    private fun writeJson(table: LootTable, path: java.nio.file.Path) {
        java.nio.file.Files.writeString(path, gson.toJson(table))
    }

    private fun readBundledCsv(tier: KeyTier): String {
        val resource = "/tables/${tier.key}.csv"
        val stream = LootTableLoader::class.java.getResourceAsStream(resource)
            ?: error("Bundled loot table resource not found: $resource")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Public test seam — serialises a LootTable to JSON using the configured adapter. */
    fun toJson(table: LootTable): String = gson.toJson(table)

    /** Public test seam — deserialises a LootTable from JSON using the configured adapter. */
    fun fromJson(json: String): LootTable = gson.fromJson(json, LootTable::class.java)
}
