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
    fun `Shiny Egg label parses as CobbreedingEgg shiny=true`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        val egg = table.entries[3].items[0] as ItemSpec.CobbreedingEgg
        assertTrue(egg.shiny)
        // Bare "Shiny Egg" with no tier word defaults to the rare pool.
        assertEquals("rare", egg.pool)
    }

    @Test
    fun `High-tier egg label routes to ultra_rare pool`() {
        val csv = """
            Tier,Item,Chance %,Notes,
            High,1 High-Tier Egg,2.0%,,
        """.trimIndent()
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, csv)
        val egg = table.entries[0].items[0] as ItemSpec.CobbreedingEgg
        assertEquals("ultra_rare", egg.pool)
    }

    @Test
    fun `Monument label routes to RandomItem over pedestal ids`() {
        val csv = """
            Tier,Item,Chance %,Notes,
            Jackpot,1 Legendary Monument,1.0%,,
        """.trimIndent()
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, csv)
        val rand = table.entries[0].items[0] as ItemSpec.RandomItem
        assertTrue(rand.ids.isNotEmpty())
        assertTrue(rand.ids.all { it.startsWith("legendarymonuments:") })
    }

    @Test
    fun `CobbreedingEgg + RandomItem round-trip through gson`() {
        val csv = """
            Tier,Item,Chance %,Notes,
            Jackpot,1 Shiny Egg,1.0%,,
            ,1 Legendary Monument,1.0%,,
        """.trimIndent()
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, csv)
        val rebuilt = LootTableLoader.fromJson(LootTableLoader.toJson(table))
        val egg = rebuilt.entries[0].items[0] as ItemSpec.CobbreedingEgg
        assertTrue(egg.shiny)
        val rand = rebuilt.entries[1].items[0] as ItemSpec.RandomItem
        assertTrue(rand.ids.isNotEmpty())
    }

    @Test
    fun `totalWeightPct reflects sum of entry weightPcts`() {
        val table = LootTableLoader.parseCsv(KeyTier.ULTRA, ultraCsv)
        assertEquals(28.0, table.entries.sumOf { it.weightPct }, 1e-9)
        assertEquals(98.0, table.totalWeightPct, 1e-9)
    }

    @Test
    fun `parseCsv result round-trips through full gson with ItemSpec adapter`() {
        val table = LootTableLoader.parseCsv(KeyTier.COMMON, commonCsv)
        val json = LootTableLoader.toJson(table)
        val rebuilt = LootTableLoader.fromJson(json)
        assertEquals(table.entries.size, rebuilt.entries.size)
        assertEquals(table.totalWeightPct, rebuilt.totalWeightPct, 1e-9)
        val ball = rebuilt.entries[0].items[0] as ItemSpec.Vanilla
        assertEquals("cobblemon:poke_ball", ball.id)
        assertEquals(20, ball.count)
        val key = rebuilt.entries[3].items[0] as ItemSpec.GachaKeyRef
        assertEquals(KeyTier.RARE, key.tier)
    }
}
