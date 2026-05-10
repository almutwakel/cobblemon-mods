package com.cobblemonmarket.pricing

import kotlin.math.pow
import kotlin.math.roundToInt

data class BatchResult(
    val perUnitPrices: List<Int>,
    val totalPrice: Int,
    val finalStock: Double,
)

/**
 * Stock-based pricing.
 *
 *   ratio = (stock + 1) / (baseStock + 1)
 *   scale = ratio^(-elasticity)
 *   buyPrice  = baseBuyPrice  × scale
 *   sellPrice = baseSellPrice × scale
 *
 * The +1 keeps the ratio finite when stock hits 0 — at stock=0 the price scale is
 * (baseStock+1)^elasticity rather than infinity.
 *
 * Per-item `elasticity` controls volatility: <1 for stable consumables (Poké Balls),
 * 1.0 for "ordinary" items, >1 for rare items where heavy buying should spike the price
 * sharply. Buys decrement stock by 1; sells increment by 1.
 */
object PricingEngine {

    fun buyPrice(baseBuyPrice: Int, stock: Double, baseStock: Int, elasticity: Double): Int =
        (baseBuyPrice * scaleFor(stock, baseStock, elasticity)).roundToInt()

    fun sellPrice(baseSellPrice: Int, stock: Double, baseStock: Int, elasticity: Double): Int =
        (baseSellPrice * scaleFor(stock, baseStock, elasticity)).roundToInt()

    /**
     * Pulls stock toward `baseStock` by [restockRatePerHour] of the gap, applied once per
     * real-time hour. Symmetric: stock above target also bleeds back down (oversupply
     * doesn't linger forever after a sell-off).
     */
    fun applyRestock(stock: Double, baseStock: Int, restockRatePerHour: Double): Double =
        stock + restockRatePerHour * (baseStock - stock)

    /**
     * Simulates buying [quantity] units in sequence. For each unit: record the buy price
     * at the current stock, then decrement stock by 1. Caller must have already verified
     * stock ≥ quantity (see TradeOps.buy → OutOfStock).
     */
    fun simulateBatchBuy(
        baseBuyPrice: Int, baseStock: Int, elasticity: Double,
        startStock: Double, quantity: Int,
    ): BatchResult {
        val prices = ArrayList<Int>(quantity)
        var stock = startStock
        repeat(quantity) {
            prices.add(buyPrice(baseBuyPrice, stock, baseStock, elasticity))
            stock -= 1.0
        }
        return BatchResult(prices, prices.sum(), stock)
    }

    /**
     * Simulates selling [quantity] units in sequence. For each unit: record the sell price
     * at the current stock, then increment stock by 1. Caller must have already verified
     * stock + quantity ≤ maxStock (see TradeOps.sell → MarketSaturated).
     */
    fun simulateBatchSell(
        baseSellPrice: Int, baseStock: Int, elasticity: Double,
        startStock: Double, quantity: Int,
    ): BatchResult {
        val prices = ArrayList<Int>(quantity)
        var stock = startStock
        repeat(quantity) {
            prices.add(sellPrice(baseSellPrice, stock, baseStock, elasticity))
            stock += 1.0
        }
        return BatchResult(prices, prices.sum(), stock)
    }

    private fun scaleFor(stock: Double, baseStock: Int, elasticity: Double): Double {
        val s = stock.coerceAtLeast(0.0)
        val ratio = (s + 1.0) / (baseStock + 1.0)
        return ratio.pow(-elasticity)
    }
}
