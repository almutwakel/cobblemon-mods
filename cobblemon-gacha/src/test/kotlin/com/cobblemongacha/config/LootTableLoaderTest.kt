package com.cobblemongacha.config

import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LootTableLoaderTest {

    private val commonCsv = """
        Tier,Item,Chance %,Notes,
        Floor,20 Poké Balls,18.0%,"Bread and butter, always useful",
        ,10 Great Balls,15.0%,,
        ,3 Revives,12.0%,,
        Jackpot,1 Rare Key,0.5%,Upgrade,
        ,TOTAL,100.0%,,
    """.trimIndent()

    private val ultraCsv = """
        Tier,Item,Chance %,Notes,
        Floor,1 Ability Patch,12.0%,Rare high-tier is ultra floor,
        ,,8.0%,Farming combo,
        ,,7.0%,,
        Jackpot,1 Shiny Egg,1.0%,Best shiny egg in the game,
        ,TOTAL,98.0%,,
    """.trimIndent()

    @Test
    fun `parses Common header row and skips TOTAL`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        assertEquals(4, table.entries.size)
    }

    @Test
    fun `propagates Tier column when blank`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        assertEquals(LootTier.Floor, table.entries[0].lootTier)
        assertEquals(LootTier.Floor, table.entries[1].lootTier)
        assertEquals(LootTier.Floor, table.entries[2].lootTier)
        assertEquals(LootTier.Jackpot, table.entries[3].lootTier)
    }

    @Test
    fun `parses count and item id for Pokeballs`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val entry = table.entries[0]
        assertEquals("20 Poké Balls", entry.label)
        assertEquals(1, entry.items.size)
        val item = entry.items[0] as ItemSpec.Vanilla
        assertEquals("cobblemon:poke_ball", item.id)
        assertEquals(20, item.count)
    }

    @Test
    fun `parses GachaKeyRef for jackpot Rare Key`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val key = table.entries[3].items[0] as ItemSpec.GachaKeyRef
        assertEquals(KeyTier.RARE, key.tier)
        assertEquals(1, key.count)
    }

    @Test
    fun `parses weight percentages`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        assertEquals(18.0, table.entries[0].weightPct, 1e-9)
        assertEquals(0.5, table.entries[3].weightPct, 1e-9)
    }

    @Test
    fun `blank Item cell becomes TBD ultra placeholder`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        val tbd1 = table.entries[1].items[0] as ItemSpec.Placeholder
        val tbd2 = table.entries[2].items[0] as ItemSpec.Placeholder
        assertEquals("tbd_ultra", tbd1.kind)
        assertEquals("tbd_ultra", tbd2.kind)
        assertTrue(tbd1.label.contains("TBD"))
    }

    @Test
    fun `Shiny Egg label parses as pokemon_egg placeholder`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        val egg = table.entries[3].items[0] as ItemSpec.Placeholder
        assertEquals("pokemon_egg", egg.kind)
        assertTrue(egg.label.contains("Shiny Egg"))
    }

    @Test
    fun `totalWeightPct reflects sum of entry weightPcts`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        assertEquals(28.0, table.entries.sumOf { it.weightPct }, 1e-9)
        assertEquals(98.0, table.totalWeightPct, 1e-9)
    }
}
