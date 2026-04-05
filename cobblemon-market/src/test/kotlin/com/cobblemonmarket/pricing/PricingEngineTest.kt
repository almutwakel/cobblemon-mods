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
    // 1. Sell price with default factor
    // -------------------------------------------------------------------------
    @Test
    fun `sell price with factor 1_0 equals base price`() {
        val result = PricingEngine.sellPrice(baseSellPrice = 2000, priceFactor = 1.0)
        assertEquals(2000, result)
    }

    // -------------------------------------------------------------------------
    // 2. Buy price with default factor and balanced spread (0 transactions)
    // -------------------------------------------------------------------------
    @Test
    fun `buy price with factor 1_0 and zero transactions equals 3x base`() {
        // 0 sells, 0 buys → total < 2 → skew = 0.5 → spread = spreadBase = 3.0
        // buyPrice = 2000 * 1.0 * 3.0 = 6000
        val result = PricingEngine.buyPrice(
            baseSellPrice = 2000,
            priceFactor = 1.0,
            sells = 0,
            buys = 0,
            spreadBase = spreadBase,
            spreadExtra = spreadExtra
        )
        assertEquals(6000, result)
    }

    // -------------------------------------------------------------------------
    // 3. Spread fully one-sided sells (50 sells, 0 buys) → 7.0
    // -------------------------------------------------------------------------
    @Test
    fun `spread is 7_0 when fully sell-sided`() {
        // skew = 50/50 = 1.0 → spread = 3.0 + 4.0 * (2.0 * |1.0 - 0.5|)^2 = 3.0 + 4.0 * 1.0 = 7.0
        val result = PricingEngine.calculateSpread(
            sells = 50,
            buys = 0,
            spreadBase = spreadBase,
            spreadExtra = spreadExtra
        )
        assertEquals(7.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 4. Spread balanced (25 sells / 25 buys) → 3.0
    // -------------------------------------------------------------------------
    @Test
    fun `spread is 3_0 when perfectly balanced`() {
        val result = PricingEngine.calculateSpread(
            sells = 25,
            buys = 25,
            spreadBase = spreadBase,
            spreadExtra = spreadExtra
        )
        assertEquals(3.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 5. Spread mostly buys (10 sells, 40 buys) → ≈ 4.44
    // -------------------------------------------------------------------------
    @Test
    fun `spread is approximately 4_44 when mostly buys`() {
        // total=50, skew=10/50=0.2
        // spread = 3.0 + 4.0 * (2.0 * |0.2 - 0.5|)^2 = 3.0 + 4.0 * (0.6)^2 = 3.0 + 4.0 * 0.36 = 4.44
        val result = PricingEngine.calculateSpread(
            sells = 10,
            buys = 40,
            spreadBase = spreadBase,
            spreadExtra = spreadExtra
        )
        assertEquals(4.44, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 6. Factor after sell decays: 1.0 → 0.98
    // -------------------------------------------------------------------------
    @Test
    fun `factor decays after sell`() {
        val result = PricingEngine.updateFactorOnSell(
            priceFactor = 1.0,
            sellDecay = sellDecay,
            factorFloor = factorFloor
        )
        assertEquals(0.98, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 7. Factor after buy grows: 0.5 → 0.51
    // -------------------------------------------------------------------------
    @Test
    fun `factor grows after buy`() {
        val result = PricingEngine.updateFactorOnBuy(
            priceFactor = 0.5,
            buyGrowth = buyGrowth,
            factorCeiling = factorCeiling
        )
        assertEquals(0.51, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 8. Factor floor is respected
    // -------------------------------------------------------------------------
    @Test
    fun `factor floor is respected on sell decay`() {
        // Starting very close to the floor: floor=0.10, decay=0.98
        // 0.10 * 0.98 = 0.098 < floor → should clamp to 0.10
        val result = PricingEngine.updateFactorOnSell(
            priceFactor = 0.10,
            sellDecay = sellDecay,
            factorFloor = factorFloor
        )
        assertEquals(factorFloor, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 9. Factor ceiling is respected
    // -------------------------------------------------------------------------
    @Test
    fun `factor ceiling is respected on buy growth`() {
        // 1.0 * 1.02 = 1.02 > ceiling=1.0 → should clamp to 1.0
        val result = PricingEngine.updateFactorOnBuy(
            priceFactor = 1.0,
            buyGrowth = buyGrowth,
            factorCeiling = factorCeiling
        )
        assertEquals(factorCeiling, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 10. 50 consecutive sells crash factor to 0.98^50 ≈ 0.364
    // -------------------------------------------------------------------------
    @Test
    fun `50 consecutive sells crash factor to approximately 0_364`() {
        var factor = 1.0
        repeat(50) {
            factor = PricingEngine.updateFactorOnSell(
                priceFactor = factor,
                sellDecay = sellDecay,
                factorFloor = factorFloor
            )
        }
        val expected = 0.98.pow(50)
        assertEquals(expected, factor, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 11. Passive recovery moves toward ceiling: 0.50 → 0.505
    // -------------------------------------------------------------------------
    @Test
    fun `recovery moves factor toward ceiling`() {
        // 0.50 + 0.01 * (1.0 - 0.50) = 0.50 + 0.005 = 0.505
        val result = PricingEngine.applyRecovery(
            priceFactor = 0.50,
            recoveryRate = 0.01,
            factorCeiling = factorCeiling
        )
        assertEquals(0.505, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 12. Recovery is faster when factor is low
    // -------------------------------------------------------------------------
    @Test
    fun `recovery step is larger when factor is further from ceiling`() {
        val recoveryRate = 0.01
        val highFactor = 0.50
        val lowFactor = 0.10

        val highResult = PricingEngine.applyRecovery(highFactor, recoveryRate, factorCeiling)
        val lowResult = PricingEngine.applyRecovery(lowFactor, recoveryRate, factorCeiling)

        val highDelta = highResult - highFactor // 0.005
        val lowDelta = lowResult - lowFactor    // 0.009

        assertTrue(lowDelta > highDelta, "Recovery delta should be larger when factor is further from ceiling")
    }

    // -------------------------------------------------------------------------
    // 13. Batch sell shows decreasing prices: 5 sells starting at f=1.0
    // -------------------------------------------------------------------------
    @Test
    fun `batch sell produces decreasing per-unit prices`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 2000,
            startFactor = 1.0,
            quantity = 5,
            sellDecay = sellDecay,
            factorFloor = factorFloor
        )

        assertEquals(5, result.perUnitPrices.size)

        // Each successive price should be <= the previous
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(
                result.perUnitPrices[i] <= result.perUnitPrices[i - 1],
                "Price at index $i (${result.perUnitPrices[i]}) should be <= price at ${i - 1} (${result.perUnitPrices[i - 1]})"
            )
        }

        // Total should equal sum of per-unit prices
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)

        // First price: 2000 * 1.0 = 2000; second: 2000 * 0.98 = 1960
        assertEquals(2000, result.perUnitPrices[0])
        assertEquals(1960, result.perUnitPrices[1])

        // Final factor: 0.98^5
        assertEquals(0.98.pow(5), result.finalFactor, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 14. Batch buy at ceiling stays at ceiling (f=1.0, growth capped)
    // -------------------------------------------------------------------------
    @Test
    fun `batch buy at ceiling clamps factor to ceiling`() {
        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = 2000,
            startFactor = 1.0,
            quantity = 3,
            buyGrowth = buyGrowth,
            factorCeiling = factorCeiling,
            sells = 0,
            buys = 0,
            spreadBase = spreadBase,
            spreadExtra = spreadExtra
        )

        assertEquals(3, result.perUnitPrices.size)

        // Factor never exceeds ceiling
        assertEquals(factorCeiling, result.finalFactor, 1e-9)

        // Total = sum of per-unit prices
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
    }

    // -------------------------------------------------------------------------
    // 15. Batch buy with depressed factor shows increasing prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch buy with depressed factor shows increasing prices`() {
        // Start at factor=0.50, well below ceiling=1.0
        // Each buy grows the factor, so successive unit prices should be increasing
        val result = PricingEngine.simulateBatchBuy(
            baseSellPrice = 2000,
            startFactor = 0.50,
            quantity = 5,
            buyGrowth = buyGrowth,
            factorCeiling = factorCeiling,
            sells = 0,
            buys = 0,
            spreadBase = spreadBase,
            spreadExtra = spreadExtra
        )

        assertEquals(5, result.perUnitPrices.size)

        // Each successive price should be >= the previous
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(
                result.perUnitPrices[i] >= result.perUnitPrices[i - 1],
                "Price at index $i (${result.perUnitPrices[i]}) should be >= price at ${i - 1} (${result.perUnitPrices[i - 1]})"
            )
        }

        // Total = sum of per-unit prices
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)

        // Final factor should be 0.50 * 1.02^5 (still below ceiling)
        val expectedFactor = minOf(0.50 * 1.02.pow(5), factorCeiling)
        assertEquals(expectedFactor, result.finalFactor, 1e-9)
    }
}
