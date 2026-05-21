package com.cobblemongacha.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EggPoolLoaderTest {

    private val csv = """
        Pokemon,Rarity,Hidden Ability Needed
        Pikachu,Common,Yes
        Jangmo-o,Rare,No
        Mr. Mime,Uncommon,Yes (both forms)
        Rockruff,Uncommon,Own Tempo
        Beldum,Ultra Rare,No
    """.trimIndent()

    @Test
    fun `parses lowercase id with hyphen preserved`() {
        val pools = EggPoolLoader.parseCsv(csv)
        val rare = pools.byTier["rare"]!!
        assertEquals("jangmo-o", rare[0].id)
    }

    @Test
    fun `replaces dots and spaces in id with underscores and strips annotations`() {
        val pools = EggPoolLoader.parseCsv(csv)
        val uncommon = pools.byTier["uncommon"]!!.map { it.id }
        assertTrue("mr_mime" in uncommon)
        assertTrue("rockruff" in uncommon)
    }

    @Test
    fun `Yes and Own Tempo both flag hasHiddenAbility`() {
        val pools = EggPoolLoader.parseCsv(csv)
        val mrMime = pools.byTier["uncommon"]!!.first { it.id == "mr_mime" }
        val rockruff = pools.byTier["uncommon"]!!.first { it.id == "rockruff" }
        assertTrue(mrMime.hasHiddenAbility)
        assertTrue(rockruff.hasHiddenAbility)
    }

    @Test
    fun `normalises Ultra Rare tier key to ultra_rare`() {
        val pools = EggPoolLoader.parseCsv(csv)
        assertNotNull(pools.byTier["ultra_rare"])
        assertEquals("beldum", pools.byTier["ultra_rare"]!![0].id)
    }

    @Test
    fun `pick returns null on unknown tier`() {
        val pools = EggPoolLoader.parseCsv(csv)
        assertNull(pools.pick("legendary"))
    }

    @Test
    fun `pick with requireHiddenAbility filters non-HA species`() {
        val pools = EggPoolLoader.parseCsv(csv)
        // rare pool only has jangmo-o (No HA) → filtered to empty → null
        assertNull(pools.pick("rare", requireHiddenAbility = true))
        // common pool has pikachu (Yes HA) → pickable
        assertEquals("pikachu", pools.pick("common", requireHiddenAbility = true, random = Random(0)))
    }
}
