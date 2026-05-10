package com.cobblemonmarket.pricing

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingEngineTest {

    // -------------------------------------------------------------------------
    // 1. At baseStock, prices equal the base prices (scale = 1.0)
    // -------------------------------------------------------------------------
    @Test
    fun `prices at base stock equal base prices`() {
        val buy = PricingEngine.buyPrice(baseBuyPrice = 90, stock = 100.0, baseStock = 100, elasticity = 1.0)
        val sell = PricingEngine.sellPrice(baseSellPrice = 30, stock = 100.0, baseStock = 100, elasticity = 1.0)
        assertEquals(90, buy)
        assertEquals(30, sell)
    }

    // -------------------------------------------------------------------------
    // 2. Empty stock with elasticity 1.0 scales by (baseStock+1)
    // -------------------------------------------------------------------------
    @Test
    fun `empty stock scales price by base stock plus one when elasticity is one`() {
        // ratio = (0+1)/(100+1) = 1/101; scale = 101 → 90 × 101 = 9090
        val buy = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 1.0)
        assertEquals(9090, buy)
    }

    // -------------------------------------------------------------------------
    // 3. Elasticity 0.3 (Poké Ball) keeps prices stable even at low stock
    // -------------------------------------------------------------------------
    @Test
    fun `low elasticity dampens price spike at low stock`() {
        // ratio ≈ 1/101, scale = (1/101)^(-0.3) = 101^0.3 ≈ 4.0
        val buy = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 0.3)
        val expected = (90 * 101.0.pow(0.3)).toInt()
        assertTrue(kotlin.math.abs(buy - expected) <= 1, "Got $buy, expected ≈ $expected")
        assertTrue(buy in 300..400, "Poké ball at zero stock should be a few hundred, got $buy")
    }

    // -------------------------------------------------------------------------
    // 4. Elasticity 2.0 (rare candy) makes prices spike sharply when scarce
    // -------------------------------------------------------------------------
    @Test
    fun `high elasticity amplifies price spike at low stock`() {
        // ratio = 1/101, scale = 101^2 = 10201; 6000 * 10201 ≈ 61,206,000
        val buy = PricingEngine.buyPrice(6000, stock = 0.0, baseStock = 100, elasticity = 2.0)
        val expected = (6000 * 101.0.pow(2.0)).roundToInt()
        assertEquals(expected, buy)
    }

    // -------------------------------------------------------------------------
    // 5. Oversupply pulls price below base
    // -------------------------------------------------------------------------
    @Test
    fun `oversupply scales prices below base`() {
        // stock = 1000 (10x), ratio = 1001/101 ≈ 9.91, scale = 1/9.91 ≈ 0.10
        val buy = PricingEngine.buyPrice(90, stock = 1000.0, baseStock = 100, elasticity = 1.0)
        assertTrue(buy in 8..10, "Oversupplied price should be ~10% of base, got $buy")
    }

    // -------------------------------------------------------------------------
    // 6. Buy/sell prices share the same scale (spread = baseBuyPrice / baseSellPrice)
    // -------------------------------------------------------------------------
    @Test
    fun `buy and sell prices share the same scale factor`() {
        val stock = 50.0
        val baseBuy = 90; val baseSell = 30
        val buy = PricingEngine.buyPrice(baseBuy, stock, baseStock = 100, elasticity = 1.0)
        val sell = PricingEngine.sellPrice(baseSell, stock, baseStock = 100, elasticity = 1.0)
        // Both scaled by the same ratio so the buy/sell ratio matches the base ratio.
        assertEquals(baseBuy.toDouble() / baseSell, buy.toDouble() / sell, 0.05)
    }

    // -------------------------------------------------------------------------
    // 7. Buying decreases stock → next buy is more expensive
    // -------------------------------------------------------------------------
    @Test
    fun `every buy raises buy price`() {
        val baseBuy = 90; val baseStock = 100; val elasticity = 1.0
        var stock = 100.0
        var prev = PricingEngine.buyPrice(baseBuy, stock, baseStock, elasticity)
        repeat(50) {
            stock -= 1.0
            val next = PricingEngine.buyPrice(baseBuy, stock, baseStock, elasticity)
            assertTrue(next >= prev, "Buy price must never decrease as stock drops: $prev → $next at stock=$stock")
            prev = next
        }
    }

    // -------------------------------------------------------------------------
    // 8. Selling increases stock → next sell pays less
    // -------------------------------------------------------------------------
    @Test
    fun `every sell lowers sell price`() {
        val baseSell = 30; val baseStock = 100; val elasticity = 1.0
        var stock = 100.0
        var prev = PricingEngine.sellPrice(baseSell, stock, baseStock, elasticity)
        repeat(50) {
            stock += 1.0
            val next = PricingEngine.sellPrice(baseSell, stock, baseStock, elasticity)
            assertTrue(next <= prev, "Sell price must never increase as stock rises: $prev → $next at stock=$stock")
            prev = next
        }
    }

    // -------------------------------------------------------------------------
    // 9. Restock pulls stock toward baseStock (refill)
    // -------------------------------------------------------------------------
    @Test
    fun `restock refills depleted stock toward base`() {
        // 50 + 0.07 × (100 - 50) = 53.5
        val result = PricingEngine.applyRestock(stock = 50.0, baseStock = 100, restockRatePerHour = 0.07)
        assertEquals(53.5, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 10. Restock pulls oversupplied stock down toward baseStock (bleed-off)
    // -------------------------------------------------------------------------
    @Test
    fun `restock bleeds oversupply back toward base`() {
        // 200 + 0.07 × (100 - 200) = 193
        val result = PricingEngine.applyRestock(stock = 200.0, baseStock = 100, restockRatePerHour = 0.07)
        assertEquals(193.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 11. Restock at baseStock leaves stock unchanged
    // -------------------------------------------------------------------------
    @Test
    fun `restock is a no-op at base stock`() {
        val result = PricingEngine.applyRestock(stock = 100.0, baseStock = 100, restockRatePerHour = 0.07)
        assertEquals(100.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 12. Restock step is larger when stock is further from base
    // -------------------------------------------------------------------------
    @Test
    fun `restock step grows with distance from base`() {
        val nearBase = PricingEngine.applyRestock(stock = 95.0, baseStock = 100, restockRatePerHour = 0.07) - 95.0
        val farFromBase = PricingEngine.applyRestock(stock = 10.0, baseStock = 100, restockRatePerHour = 0.07) - 10.0
        assertTrue(farFromBase > nearBase, "Refill rate should grow with distance: near=$nearBase far=$farFromBase")
    }

    // -------------------------------------------------------------------------
    // 13. Batch buy yields monotonically increasing per-unit prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch buy produces monotonically increasing prices`() {
        val result = PricingEngine.simulateBatchBuy(
            baseBuyPrice = 90, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 5,
        )
        assertEquals(5, result.perUnitPrices.size)
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(result.perUnitPrices[i] >= result.perUnitPrices[i - 1])
        }
        assertEquals(95.0, result.finalStock, 1e-9)
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
    }

    // -------------------------------------------------------------------------
    // 14. Batch sell yields monotonically decreasing per-unit prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch sell produces monotonically decreasing prices`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 30, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 5,
        )
        assertEquals(5, result.perUnitPrices.size)
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(result.perUnitPrices[i] <= result.perUnitPrices[i - 1])
        }
        assertEquals(105.0, result.finalStock, 1e-9)
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
    }

    // -------------------------------------------------------------------------
    // 15. Batch sell with elasticity 1.0 from base: prices match formula
    // -------------------------------------------------------------------------
    @Test
    fun `batch sell from base matches per-step formula`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 30, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 3,
        )
        // Step 1: stock=100, ratio=101/101=1, scale=1.0, sell=30
        // Step 2: stock=101, ratio=102/101, scale=101/102, sell=30*101/102≈29.7→30
        // Step 3: stock=102, ratio=103/101, scale=101/103, sell=30*101/103≈29.4→29
        assertEquals(30, result.perUnitPrices[0])
        assertTrue(result.perUnitPrices[1] in 29..30)
        assertTrue(result.perUnitPrices[2] in 29..30)
    }

    // -------------------------------------------------------------------------
    // 16. Stock at zero handled gracefully (no infinity from ratio)
    // -------------------------------------------------------------------------
    @Test
    fun `stock at zero gives finite prices`() {
        val buy = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 1.0)
        val sell = PricingEngine.sellPrice(30, stock = 0.0, baseStock = 100, elasticity = 1.0)
        // Int values can't be infinite — guarding against the bug where ratio→0 would
        // overflow Int via Double.POSITIVE_INFINITY.toInt() → Int.MAX_VALUE.
        assertTrue(buy > 0 && buy < Int.MAX_VALUE)
        assertTrue(sell > 0 && sell < Int.MAX_VALUE)
    }

    // -------------------------------------------------------------------------
    // 17. Negative stock is clamped to zero (defensive — TradeOps prevents it)
    // -------------------------------------------------------------------------
    @Test
    fun `negative stock is clamped to zero`() {
        val atZero = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 1.0)
        val belowZero = PricingEngine.buyPrice(90, stock = -5.0, baseStock = 100, elasticity = 1.0)
        assertEquals(atZero, belowZero)
    }

    // -------------------------------------------------------------------------
    // 18. Per-unit scale is symmetric: doubling baseStock at same stock-ratio gives same scale
    // -------------------------------------------------------------------------
    @Test
    fun `same stock ratio yields same price scale`() {
        // (50+1)/(100+1) ≈ (100+1)/(200+1) but not exactly — the +1 makes them differ slightly.
        // Test with larger numbers where +1 is negligible.
        val small = PricingEngine.buyPrice(100, stock = 500.0, baseStock = 1000, elasticity = 1.0)
        val large = PricingEngine.buyPrice(100, stock = 5000.0, baseStock = 10000, elasticity = 1.0)
        assertTrue(kotlin.math.abs(small - large) <= 1, "Same ratio should give same price: $small vs $large")
    }
}
