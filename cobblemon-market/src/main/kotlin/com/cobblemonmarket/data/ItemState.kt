package com.cobblemonmarket.data

data class Transaction(
    val type: String, // "buy" or "sell"
    val timestamp: Long
)

/**
 * One entry in the per-item price history. Recorded once per /market buy|sell batch
 * (not per unit) to keep the on-disk JSON manageable. `pricePerUnit` is the average
 * per-unit price for the batch.
 */
data class PriceTick(
    val type: String,           // "buy" or "sell"
    val timestamp: Long,
    val pricePerUnit: Int,
    val quantity: Int,
)

data class ItemState(
    var priceFactor: Double = 1.0,
    val transactions: MutableList<Transaction> = mutableListOf(),
    /** Bounded by MarketConfig.priceHistorySize. Older entries dropped from the head. */
    val priceHistory: MutableList<PriceTick> = mutableListOf(),
)
