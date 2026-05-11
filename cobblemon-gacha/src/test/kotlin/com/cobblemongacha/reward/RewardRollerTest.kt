package com.cobblemongacha.reward

import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.LootTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RewardRollerTest {

    private fun mkEntry(label: String, weight: Double, tier: LootTier = LootTier.Floor) =
        LootEntry(tier, label, weight, listOf(ItemSpec.Vanilla("minecraft:stone", 1)))

    @Test
    fun `rolls only nonzero-weight entries`() {
        val table = LootTable(KeyTier.COMMON, 100.0, listOf(
            mkEntry("A", 0.0),
            mkEntry("B", 100.0),
        ))
        repeat(50) {
            val rolled = RewardRoller.roll(table, Random.Default)
            assertEquals("B", rolled.label, "0-weight entry must never be picked")
        }
    }

    @Test
    fun `deterministic with seeded random`() {
        val table = LootTable(KeyTier.COMMON, 100.0, listOf(
            mkEntry("A", 30.0),
            mkEntry("B", 70.0),
        ))
        val r1 = Random(42)
        val r2 = Random(42)
        repeat(20) {
            assertEquals(RewardRoller.roll(table, r1).label, RewardRoller.roll(table, r2).label)
        }
    }

    @Test
    fun `empirical distribution within tolerance over 100k rolls`() {
        val table = LootTable(KeyTier.COMMON, 100.0, listOf(
            mkEntry("A", 30.0),
            mkEntry("B", 70.0),
        ))
        val rand = Random(0)
        val counts = mutableMapOf<String, Int>()
        val n = 100_000
        repeat(n) {
            val label = RewardRoller.roll(table, rand).label
            counts.merge(label, 1) { a, _ -> a + 1 }
        }
        val pctA = (counts["A"] ?: 0).toDouble() / n
        val pctB = (counts["B"] ?: 0).toDouble() / n
        assertTrue(kotlin.math.abs(pctA - 0.30) < 0.01, "A pct $pctA")
        assertTrue(kotlin.math.abs(pctB - 0.70) < 0.01, "B pct $pctB")
    }

    @Test
    fun `throws if no positive-weight entries`() {
        val table = LootTable(KeyTier.COMMON, 0.0, listOf(mkEntry("A", 0.0)))
        try {
            RewardRoller.roll(table, Random.Default)
            assert(false) { "expected exception" }
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
