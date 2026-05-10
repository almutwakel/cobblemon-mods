package com.cobblemonmarket.data

/**
 * One entry in the per-item price history. Recorded once per /market buy|sell batch
 * (not per unit) so the on-disk JSON stays manageable.
 *
 * `pricePerUnit` is the rounded average per-unit price for the batch.
 *
 * `priceBefore`/`priceAfter` capture the per-unit price for a one-unit trade at the
 * stock level in effect immediately before and after the batch, respectively. They drive
 * the open/close of the candlestick chart in `/market price`. For a buy these are
 * monotonically increasing; for a sell they're monotonically decreasing. Default to
 * `pricePerUnit` when reading old records that didn't have these fields (Gson populates
 * them with 0 in that case — the renderer treats 0 as "use pricePerUnit").
 *
 * `playerUuid`/`playerName` identify the trader, used to group consecutive same-player
 * same-day batches into a single candle.
 */
data class PriceTick(
    val type: String,           // "buy" or "sell"
    val timestamp: Long,
    val pricePerUnit: Int,
    val quantity: Int,
    val playerUuid: String = "",
    val playerName: String = "",
    val priceBefore: Int = 0,   // 0 = unknown (legacy record)
    val priceAfter: Int = 0,    // 0 = unknown (legacy record)
)

/**
 * Per-item market state. Stock is stored as a double so passive restock can apply
 * fractional increments per hour; `/market prices` and trade reports round for display.
 */
data class ItemState(
    var stock: Double = 0.0,
    /** Bounded by MarketConfig.priceHistorySize. Older entries dropped from the head. */
    val priceHistory: MutableList<PriceTick> = mutableListOf(),
)
