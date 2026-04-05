package com.cobblemonmarket.pricing

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class BatchResult(
    val perUnitPrices: List<Int>,
    val totalPrice: Int,
    val finalFactor: Double
)

object PricingEngine {

    /**
     * Returns the price at which the shop buys one unit from the player (sell side),
     * scaled by the current price factor.
     */
    fun sellPrice(baseSellPrice: Int, priceFactor: Double): Int =
        (baseSellPrice * priceFactor).roundToInt()

    /**
     * Returns the price at which a player buys one unit from the shop (buy side),
     * incorporating the spread multiplier derived from recent transaction history.
     */
    fun buyPrice(
        baseSellPrice: Int,
        priceFactor: Double,
        sells: Int,
        buys: Int,
        spreadBase: Double,
        spreadExtra: Double
    ): Int {
        val spread = calculateSpread(sells, buys, spreadBase, spreadExtra)
        return (baseSellPrice * priceFactor * spread).roundToInt()
    }

    /**
     * Computes the bid-ask spread multiplier based on the skew of recent sells vs buys.
     *
     * The spread is minimum (spreadBase) when sells and buys are balanced, and maximum
     * (spreadBase + spreadExtra) when activity is entirely one-sided.
     *
     * Formula:
     *   total = sells + buys
     *   skew  = if total < 2 then 0.5 else sells / total
     *   spread = spreadBase + spreadExtra * (2 * |skew - 0.5|)^2
     */
    fun calculateSpread(sells: Int, buys: Int, spreadBase: Double, spreadExtra: Double): Double {
        val total = sells + buys
        val skew = if (total < 2) 0.5 else sells.toDouble() / total
        return spreadBase + spreadExtra * (2.0 * abs(skew - 0.5)).pow(2)
    }

    /**
     * Decays the price factor after a player sells an item to the shop.
     * The factor never drops below factorFloor.
     */
    fun updateFactorOnSell(priceFactor: Double, sellDecay: Double, factorFloor: Double): Double =
        maxOf(priceFactor * sellDecay, factorFloor)

    /**
     * Grows the price factor after a player buys an item from the shop.
     * The factor never exceeds factorCeiling.
     */
    fun updateFactorOnBuy(priceFactor: Double, buyGrowth: Double, factorCeiling: Double): Double =
        minOf(priceFactor * buyGrowth, factorCeiling)

    /**
     * Passively recovers the price factor toward the ceiling over time.
     * Uses exponential smoothing: factor += recoveryRate * (ceiling - factor).
     */
    fun applyRecovery(priceFactor: Double, recoveryRate: Double, factorCeiling: Double): Double =
        priceFactor + recoveryRate * (factorCeiling - priceFactor)

    /**
     * Simulates selling [quantity] units in sequence.
     *
     * For each unit: records the sell price at the current factor, then decays the factor.
     * Returns the list of per-unit prices, their sum, and the factor after all sells.
     */
    fun simulateBatchSell(
        baseSellPrice: Int,
        startFactor: Double,
        quantity: Int,
        sellDecay: Double,
        factorFloor: Double
    ): BatchResult {
        val prices = mutableListOf<Int>()
        var factor = startFactor

        repeat(quantity) {
            prices.add(sellPrice(baseSellPrice, factor))
            factor = updateFactorOnSell(factor, sellDecay, factorFloor)
        }

        return BatchResult(
            perUnitPrices = prices,
            totalPrice = prices.sum(),
            finalFactor = factor
        )
    }

    /**
     * Simulates buying [quantity] units in sequence.
     *
     * For each unit: records the buy price at the current factor and current spread
     * (spread is recalculated each iteration as the running buys counter increments),
     * then grows the factor.
     *
     * [sells] and [buys] represent the pre-existing transaction counts before this batch.
     */
    fun simulateBatchBuy(
        baseSellPrice: Int,
        startFactor: Double,
        quantity: Int,
        buyGrowth: Double,
        factorCeiling: Double,
        sells: Int,
        buys: Int,
        spreadBase: Double,
        spreadExtra: Double
    ): BatchResult {
        val prices = mutableListOf<Int>()
        var factor = startFactor
        var currentBuys = buys

        repeat(quantity) {
            prices.add(buyPrice(baseSellPrice, factor, sells, currentBuys, spreadBase, spreadExtra))
            factor = updateFactorOnBuy(factor, buyGrowth, factorCeiling)
            currentBuys++
        }

        return BatchResult(
            perUnitPrices = prices,
            totalPrice = prices.sum(),
            finalFactor = factor
        )
    }
}
