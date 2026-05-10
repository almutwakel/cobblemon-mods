package com.cobblemonmarket.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.ZoneId

class PriceHistoryTest {

    /** Tests pin the zone to UTC so day-boundary math is unambiguous regardless of host. */
    private val UTC = ZoneId.of("UTC")
    private val day0Noon = 1_700_000_000_000L              // some Tuesday around noon UTC
    private val day0Later = day0Noon + 60 * 60 * 1000L     // +1h, same UTC day
    private val day1Noon = day0Noon + 24L * 60 * 60 * 1000L // +24h → next UTC day

    private fun tick(
        ts: Long, player: String, type: String,
        priceBefore: Int, priceAfter: Int, qty: Int = 1,
    ) = PriceTick(
        type = type, timestamp = ts, pricePerUnit = (priceBefore + priceAfter) / 2,
        quantity = qty, playerUuid = player, playerName = player,
        priceBefore = priceBefore, priceAfter = priceAfter,
    )

    @Test
    fun `empty history produces no candles`() {
        assertTrue(PriceHistory.groupIntoCandles(emptyList(), UTC).isEmpty())
    }

    @Test
    fun `single tick produces one candle with open=before and close=after`() {
        val history = listOf(tick(day0Noon, "p1", "buy", priceBefore = 100, priceAfter = 110, qty = 5))
        val candles = PriceHistory.groupIntoCandles(history, UTC)
        assertEquals(1, candles.size)
        val c = candles[0]
        assertEquals(100, c.open)
        assertEquals(110, c.close)
        assertEquals(110, c.high)
        assertEquals(100, c.low)
        assertEquals(5, c.volume)
    }

    @Test
    fun `consecutive ticks same player same day form one candle`() {
        val history = listOf(
            tick(day0Noon, "p1", "buy", 100, 110, qty = 3),
            tick(day0Later, "p1", "buy", 110, 125, qty = 2),
        )
        val candles = PriceHistory.groupIntoCandles(history, UTC)
        assertEquals(1, candles.size)
        assertEquals(100, candles[0].open)
        assertEquals(125, candles[0].close)
        assertEquals(125, candles[0].high)
        assertEquals(100, candles[0].low)
        assertEquals(5, candles[0].volume)
        assertEquals(2, candles[0].tickCount)
    }

    @Test
    fun `different player splits into separate candles`() {
        val history = listOf(
            tick(day0Noon, "p1", "buy", 100, 110),
            tick(day0Later, "p2", "buy", 110, 120),
        )
        val candles = PriceHistory.groupIntoCandles(history, UTC)
        assertEquals(2, candles.size)
        assertEquals("p1", candles[0].playerName)
        assertEquals("p2", candles[1].playerName)
    }

    @Test
    fun `day rollover splits into separate candles even for same player`() {
        val history = listOf(
            tick(day0Noon, "p1", "buy", 100, 110),
            tick(day1Noon, "p1", "buy", 110, 120),
        )
        val candles = PriceHistory.groupIntoCandles(history, UTC)
        assertEquals(2, candles.size)
    }

    @Test
    fun `interleaved players produce three candles`() {
        // p1, p1, p2, p1, p1 — same day — should split into [p1×2, p2×1, p1×2]
        val history = listOf(
            tick(day0Noon + 1000, "p1", "buy", 100, 110),
            tick(day0Noon + 2000, "p1", "buy", 110, 120),
            tick(day0Noon + 3000, "p2", "sell", 120, 115),
            tick(day0Noon + 4000, "p1", "buy", 115, 125),
            tick(day0Noon + 5000, "p1", "buy", 125, 135),
        )
        val candles = PriceHistory.groupIntoCandles(history, UTC)
        assertEquals(3, candles.size)
        assertEquals("p1", candles[0].playerName); assertEquals(2, candles[0].tickCount)
        assertEquals("p2", candles[1].playerName); assertEquals(1, candles[1].tickCount)
        assertEquals("p1", candles[2].playerName); assertEquals(2, candles[2].tickCount)
    }

    @Test
    fun `sell candle has close below open (red)`() {
        val history = listOf(tick(day0Noon, "p1", "sell", priceBefore = 200, priceAfter = 180, qty = 4))
        val c = PriceHistory.groupIntoCandles(history, UTC).first()
        assertTrue(c.close < c.open)
        assertEquals(200, c.high)
        assertEquals(180, c.low)
    }

    @Test
    fun `direction flip splits same-player same-day ticks into separate candles`() {
        // p1 buys, then p1 sells — should produce two candles so the chart shows the
        // up-then-down motion instead of collapsing into one ambiguous candle.
        val history = listOf(
            tick(day0Noon + 1000, "p1", "buy", 100, 110, qty = 2),
            tick(day0Noon + 2000, "p1", "buy", 110, 120, qty = 1),
            tick(day0Noon + 3000, "p1", "sell", 120, 115, qty = 1),
            tick(day0Noon + 4000, "p1", "sell", 115, 105, qty = 2),
        )
        val candles = PriceHistory.groupIntoCandles(history, UTC)
        assertEquals(2, candles.size)
        assertEquals("buy", history[0].type)
        // First candle: the buy run — close > open (price rose)
        assertTrue(candles[0].close > candles[0].open)
        assertEquals(2, candles[0].tickCount)
        // Second candle: the sell run — close < open (price fell)
        assertTrue(candles[1].close < candles[1].open)
        assertEquals(2, candles[1].tickCount)
    }

    @Test
    fun `legacy records without priceBefore or priceAfter fall back to pricePerUnit`() {
        val legacy = PriceTick(
            type = "buy", timestamp = day0Noon, pricePerUnit = 150, quantity = 3,
            playerUuid = "p1", playerName = "p1", priceBefore = 0, priceAfter = 0,
        )
        val c = PriceHistory.groupIntoCandles(listOf(legacy), UTC).first()
        assertEquals(150, c.open)
        assertEquals(150, c.close)
        assertEquals(150, c.high)
        assertEquals(150, c.low)
    }
}
