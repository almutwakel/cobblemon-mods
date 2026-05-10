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
     * Returns the price at which a player buys one unit from the shop.
     * Buy price is driven purely by the factor and constant spreadBase:
     *   P_buy = baseSellPrice × priceFactor × spreadBase
     *
     * This guarantees that every sell (which lowers priceFactor) always lowers the buy price.
     */
    fun buyPrice(baseSellPrice: Int, priceFactor: Double, spreadBase: Double): Int =
        (baseSellPrice * priceFactor * spreadBase).roundToInt()

    /**
     * Returns the price at which the shop buys one unit from the player (sell side).
     * Sell price = buyPrice / dynamicSpread.
     *
     * The dynamic spread only punishes sellers during lopsided activity —
     * it never raises the cost for buyers.
     */
    fun sellPrice(
        baseSellPrice: Int,
        priceFactor: Double,
        sells: Int,
        buys: Int,
        spreadBase: Double,
        spreadExtra: Double
    ): Int {
        val buy = buyPrice(baseSellPrice, priceFactor, spreadBase)
        val spread = calculateSpread(sells, buys, spreadBase, spreadExtra)
        return (buy.toDouble() / spread).roundToInt()
    }

    /**
     * Computes the bid-ask spread multiplier based on the skew of recent sells vs buys.
     *
     * spread = spreadBase + spreadExtra × (2 × |skew − 0.5|)²
     * Minimum (spreadBase) when balanced, maximum (spreadBase + spreadExtra) when one-sided.
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
     * Uses exponential smoothing: factor += recoveryRate × (ceiling − factor).
     */
    fun applyRecovery(priceFactor: Double, recoveryRate: Double, factorCeiling: Double): Double =
        priceFactor + recoveryRate * (factorCeiling - priceFactor)

    /**
     * Simulates selling [quantity] units in sequence.
     * For each unit: records the sell price at the current factor/spread, then decays the factor.
     */
    fun simulateBatchSell(
        baseSellPrice: Int,
        startFactor: Double,
        quantity: Int,
        sellDecay: Double,
        factorFloor: Double,
        sells: Int,
        buys: Int,
        spreadBase: Double,
        spreadExtra: Double
    ): BatchResult {
        val prices = mutableListOf<Int>()
        var factor = startFactor
        var currentSells = sells

        repeat(quantity) {
            prices.add(sellPrice(baseSellPrice, factor, currentSells, buys, spreadBase, spreadExtra))
            factor = updateFactorOnSell(factor, sellDecay, factorFloor)
            currentSells++
        }

        return BatchResult(
            perUnitPrices = prices,
            totalPrice = prices.sum(),
            finalFactor = factor
        )
    }

    /**
     * Simulates buying [quantity] units in sequence.
     * For each unit: records the buy price at the current factor (using constant spreadBase),
     * then grows the factor.
     */
    fun simulateBatchBuy(
        baseSellPrice: Int,
        startFactor: Double,
        quantity: Int,
        buyGrowth: Double,
        factorCeiling: Double,
        spreadBase: Double
    ): BatchResult {
        val prices = mutableListOf<Int>()
        var factor = startFactor

        repeat(quantity) {
            prices.add(buyPrice(baseSellPrice, factor, spreadBase))
            factor = updateFactorOnBuy(factor, buyGrowth, factorCeiling)
        }

        return BatchResult(
            perUnitPrices = prices,
            totalPrice = prices.sum(),
            finalFactor = factor
        )
    }
}
