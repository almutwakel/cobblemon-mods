package com.cobblemonmarket.pricing

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingEngineTest {

    // Default config constants mirroring MarketConfig defaults
    private val spreadBase = 3.0
    private val spreadExtra = 4.0
    private val sellDecay = 0.98
    private val buyGrowth = 1.02
    private val factorFloor = 0.10
    private val factorCeiling = 1.00

    // -------------------------------------------------------------------------
    // 1. Buy price with default factor = B × f × S_base
    // -------------------------------------------------------------------------
    @Test
    fun `buy price with factor 1_0 equals 3x base`() {
        // P_buy = 2000 * 1.0 * 3.0 = 6000
        val result = PricingEngine.buyPrice(baseSellPrice = 2000, priceFactor = 1.0, spreadBase = spreadBase)
        assertEquals(6000, result)
    }

    // -------------------------------------------------------------------------
    // 2. Sell price with balanced spread = P_buy / 3.0
    // -------------------------------------------------------------------------
    @Test
    fun `sell price with factor 1_0 and balanced spread equals base`() {
        // P_buy = 6000, spread = 3.0, P_sell = 6000 / 3.0 = 2000
        val result = PricingEngine.sellPrice(
            baseSellPrice = 2000, priceFactor = 1.0,
            sells = 0, buys = 0, spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(2000, result)
    }

    // -------------------------------------------------------------------------
    // 3. Spread fully one-sided sells (50 sells, 0 buys) → 7.0
    // -------------------------------------------------------------------------
    @Test
    fun `spread is 7_0 when fully sell-sided`() {
        val result = PricingEngine.calculateSpread(sells = 50, buys = 0, spreadBase = spreadBase, spreadExtra = spreadExtra)
        assertEquals(7.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 4. Spread balanced (25 sells / 25 buys) → 3.0
    // -------------------------------------------------------------------------
    @Test
    fun `spread is 3_0 when perfectly balanced`() {
        val result = PricingEngine.calculateSpread(sells = 25, buys = 25, spreadBase = spreadBase, spreadExtra = spreadExtra)
        assertEquals(3.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 5. Spread mostly buys (10 sells, 40 buys) → ≈ 4.44
    // -------------------------------------------------------------------------
    @Test
    fun `spread is approximately 4_44 when mostly buys`() {
        val result = PricingEngine.calculateSpread(sells = 10, buys = 40, spreadBase = spreadBase, spreadExtra = spreadExtra)
        assertEquals(4.44, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 6. Factor after sell decays: 1.0 → 0.98
    // -------------------------------------------------------------------------
    @Test
    fun `factor decays after sell`() {
        val result = PricingEngine.updateFactorOnSell(priceFactor = 1.0, sellDecay = sellDecay, factorFloor = factorFloor)
        assertEquals(0.98, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 7. Factor after buy grows: 0.5 → 0.51
    // -------------------------------------------------------------------------
    @Test
    fun `factor grows after buy`() {
        val result = PricingEngine.updateFactorOnBuy(priceFactor = 0.5, buyGrowth = buyGrowth, factorCeiling = factorCeiling)
        assertEquals(0.51, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 8. Factor floor is respected
    // -------------------------------------------------------------------------
    @Test
    fun `factor floor is respected on sell decay`() {
        val result = PricingEngine.updateFactorOnSell(priceFactor = 0.10, sellDecay = sellDecay, factorFloor = factorFloor)
        assertEquals(factorFloor, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 9. Factor ceiling is respected
    // -------------------------------------------------------------------------
    @Test
    fun `factor ceiling is respected on buy growth`() {
        val result = PricingEngine.updateFactorOnBuy(priceFactor = 1.0, buyGrowth = buyGrowth, factorCeiling = factorCeiling)
        assertEquals(factorCeiling, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 10. 50 consecutive sells crash factor to 0.98^50 ≈ 0.364
    // -------------------------------------------------------------------------
    @Test
    fun `50 consecutive sells crash factor to approximately 0_364`() {
        var factor = 1.0
        repeat(50) {
            factor = PricingEngine.updateFactorOnSell(priceFactor = factor, sellDecay = sellDecay, factorFloor = factorFloor)
        }
        val expected = 0.98.pow(50)
        assertEquals(expected, factor, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 11. Passive recovery at 4%: 0.50 → 0.52
    // -------------------------------------------------------------------------
    @Test
    fun `recovery moves factor toward ceiling`() {
        // 0.50 + 0.04 * (1.0 - 0.50) = 0.50 + 0.02 = 0.52
        val result = PricingEngine.applyRecovery(priceFactor = 0.50, recoveryRate = 0.04, factorCeiling = factorCeiling)
        assertEquals(0.52, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 12. Recovery is faster when factor is low
    // -------------------------------------------------------------------------
    @Test
    fun `recovery step is larger when factor is further from ceiling`() {
        val recoveryRate = 0.04
        val highResult = PricingEngine.applyRecovery(0.50, recoveryRate, factorCeiling)
        val lowResult = PricingEngine.applyRecovery(0.10, recoveryRate, factorCeiling)
        val highDelta = highResult - 0.50 // 0.02
        val lowDelta = lowResult - 0.10   // 0.036
        assertTrue(lowDelta > highDelta, "Recovery delta should be larger when factor is further from ceiling")
    }

    // -------------------------------------------------------------------------
    // 13. KEY GUARANTEE: every sell lowers buy price
    // -------------------------------------------------------------------------
    @Test
    fun `every sell always lowers buy price`() {
        // Buy price = B * f * S_base, and sell always decreases f, so buy price must decrease
        val base = 2000
        var factor = 1.0
        var prevBuyPrice = PricingEngine.buyPrice(base, factor, spreadBase)

        repeat(50) {
            factor = PricingEngine.updateFactorOnSell(factor, sellDecay, factorFloor)
            val newBuyPrice = PricingEngine.buyPrice(base, factor, spreadBase)
            assertTrue(
                newBuyPrice <= prevBuyPrice,
                "Buy price must never increase on sell: was $prevBuyPrice, now $newBuyPrice"
            )
            prevBuyPrice = newBuyPrice
        }
    }

    // -------------------------------------------------------------------------
    // 14. Sell price with lopsided spread is lower
    // -------------------------------------------------------------------------
    @Test
    fun `sell price decreases with wider spread`() {
        val balancedSell = PricingEngine.sellPrice(2000, 1.0, 25, 25, spreadBase, spreadExtra)
        val lopsidedSell = PricingEngine.sellPrice(2000, 1.0, 50, 0, spreadBase, spreadExtra)
        // Balanced: 6000/3.0 = 2000, Lopsided: 6000/7.0 ≈ 857
        assertEquals(2000, balancedSell)
        assertEquals(857, lopsidedSell)
        assertTrue(lopsidedSell < balancedSell)
    }

    // -------------------------------------------------------------------------
    // 15. Rare candy example from spec: 50 sells, f ≈ 0.364, spread = 7x
    // -------------------------------------------------------------------------
    @Test
    fun `rare candy 50 sells gives expected prices`() {
        val base = 2000
        var factor = 1.0
        repeat(50) { factor = PricingEngine.updateFactorOnSell(factor, sellDecay, factorFloor) }
        // f ≈ 0.364
        assertEquals(0.98.pow(50), factor, 1e-9)

        // P_buy = 2000 * 0.364 * 3 ≈ 2184
        val buyPrice = PricingEngine.buyPrice(base, factor, spreadBase)
        assertEquals((base * factor * spreadBase).toInt(), buyPrice)

        // P_sell = P_buy / 7.0 (50 sells, 0 buys → spread = 7)
        val sellPrice = PricingEngine.sellPrice(base, factor, 50, 0, spreadBase, spreadExtra)
        assertEquals((buyPrice.toDouble() / 7.0).toInt(), sellPrice)
    }

    // -------------------------------------------------------------------------
    // 16. Batch sell produces decreasing per-unit prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch sell produces decreasing per-unit prices`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 2000, startFactor = 1.0, quantity = 5,
            sellDecay = sellDecay, factorFloor = factorFloor,
            sells = 0, buys = 0, spreadBase = spreadBase, spreadExtra = spreadExtra
        )
        assertEquals(5, result.perUnitPrices.size)
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(result.perUnitPrices[i] <= result.perUnitPrices[i - 1])
        }
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
        assertEquals(0.98.pow(5), result.finalFactor, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 17. Batch buy at ceiling stays at ceiling
    // -------------------------------------------------------------------------
    @Test
    fun `batch buy at ceiling clamps factor to ceiling`() {
        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = 2000, startFactor = 1.0, quantity = 3,
            buyGrowth = buyGrowth, factorCeiling = factorCeiling,
            spreadBase = spreadBase
        )
        assertEquals(3, result.perUnitPrices.size)
        assertEquals(factorCeiling, result.finalFactor, 1e-9)
        // All prices should be the same since factor is clamped at 1.0
        assertTrue(result.perUnitPrices.all { it == 6000 })
    }

    // -------------------------------------------------------------------------
    // 18. Batch buy with depressed factor shows increasing prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch buy with depressed factor shows increasing prices`() {
        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = 2000, startFactor = 0.50, quantity = 5,
            buyGrowth = buyGrowth, factorCeiling = factorCeiling,
            spreadBase = spreadBase
        )
        assertEquals(5, result.perUnitPrices.size)
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(result.perUnitPrices[i] >= result.perUnitPrices[i - 1])
        }
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
        val expectedFactor = minOf(0.50 * 1.02.pow(5), factorCeiling)
        assertEquals(expectedFactor, result.finalFactor, 1e-9)
    }
}
