package com.cobblemonmarket.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One bar in the candlestick chart shown by `/market price`. See spec in `PriceTick`
 * for what counts as a "transaction group" (the basis for one candle).
 */
data class Candle(
    val open: Int,
    val close: Int,
    val high: Int,
    val low: Int,
    val volume: Int,
    val playerName: String,
    val timestamp: Long,
    val tickCount: Int,
)

object PriceHistory {

    /**
     * Groups a chronologically-ordered list of [PriceTick]s into [Candle]s.
     *
     * A group is a maximal run of consecutive ticks by the same player on the same
     * calendar day in [zoneId]. A different player's tick or a day rollover starts
     * a new group.
     */
    fun groupIntoCandles(
        history: List<PriceTick>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Candle> {
        if (history.isEmpty()) return emptyList()
        fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()

        val candles = mutableListOf<Candle>()
        var batch = mutableListOf<PriceTick>()
        for (tick in history) {
            if (batch.isEmpty()) {
                batch.add(tick)
                continue
            }
            val head = batch.first()
            val sameDay = dayOf(tick.timestamp) == dayOf(head.timestamp)
            val samePlayer = tick.playerUuid == head.playerUuid
            // Also break on direction flip: a buy run followed by a sell run from the
            // same player should render as two candles (green up, then red down) rather
            // than collapsing into one ambiguous candle whose open/close mix price kinds.
            val sameType = tick.type == head.type
            if (sameDay && samePlayer && sameType) {
                batch.add(tick)
            } else {
                candles.add(toCandle(batch))
                batch = mutableListOf(tick)
            }
        }
        if (batch.isNotEmpty()) candles.add(toCandle(batch))
        return candles
    }

    private fun toCandle(ticks: List<PriceTick>): Candle {
        // Fall back to pricePerUnit for legacy records where priceBefore/priceAfter are 0.
        fun before(t: PriceTick): Int = if (t.priceBefore > 0) t.priceBefore else t.pricePerUnit
        fun after(t: PriceTick): Int = if (t.priceAfter > 0) t.priceAfter else t.pricePerUnit

        val first = ticks.first()
        val last = ticks.last()
        var high = Int.MIN_VALUE
        var low = Int.MAX_VALUE
        var volume = 0
        for (t in ticks) {
            val b = before(t); val a = after(t)
            if (b > high) high = b
            if (a > high) high = a
            if (b < low) low = b
            if (a < low) low = a
            volume += t.quantity
        }
        return Candle(
            open = before(first),
            close = after(last),
            high = high,
            low = low,
            volume = volume,
            playerName = first.playerName,
            timestamp = first.timestamp,
            tickCount = ticks.size,
        )
    }
}
