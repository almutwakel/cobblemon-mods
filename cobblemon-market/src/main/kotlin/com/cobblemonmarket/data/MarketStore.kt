package com.cobblemonmarket.data

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MarketStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = configDir.resolve("cobblemon-market").resolve("state.json")
    private val states: MutableMap<String, ItemState> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, ItemState>>() {}.type
            val loaded: MutableMap<String, ItemState> = gson.fromJson(file.readText(), type)
            states.clear()
            states.putAll(loaded)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load market state", e)
        }
    }

    fun save() {
        configDir.resolve("cobblemon-market").createDirectories()
        file.writeText(gson.toJson(states))
    }

    fun getOrCreate(itemId: String): ItemState {
        return states.getOrPut(itemId) { ItemState() }
    }

    fun getAll(): Map<String, ItemState> = states.toMap()

    /**
     * Initializes any items that don't yet have an entry — sets stock to baseStock so a
     * fresh server starts at equilibrium prices instead of zero stock (which would scale
     * everything by ~baseStock^elasticity on the first /market prices).
     */
    fun ensureInitialized(items: Map<String, ItemEntry>) {
        var changed = false
        for ((itemId, entry) in items) {
            if (!states.containsKey(itemId)) {
                states[itemId] = ItemState(stock = entry.baseStock.toDouble())
                changed = true
            }
        }
        if (changed) save()
    }

    fun setStock(itemId: String, stock: Double) {
        getOrCreate(itemId).stock = stock.coerceAtLeast(0.0)
        save()
    }

    /**
     * Records one batch-level price-history entry for the chart shown by `/market price`.
     * Bounded by [com.cobblemonmarket.config.MarketConfig.priceHistorySize]; oldest entries
     * are dropped when the cap is hit.
     *
     * `priceBefore`/`priceAfter` should be the per-unit one-trade price at the stock in
     * effect immediately before and immediately after the batch — they drive the open/close
     * of each candle. `playerUuid`/`playerName` identify the trader for the same-player
     * same-day grouping logic.
     */
    fun recordPriceTick(
        itemId: String, type: String,
        playerUuid: String, playerName: String,
        pricePerUnit: Int, priceBefore: Int, priceAfter: Int,
        quantity: Int,
    ) {
        val state = getOrCreate(itemId)
        state.priceHistory.add(PriceTick(
            type = type,
            timestamp = System.currentTimeMillis(),
            pricePerUnit = pricePerUnit,
            quantity = quantity,
            playerUuid = playerUuid,
            playerName = playerName,
            priceBefore = priceBefore,
            priceAfter = priceAfter,
        ))
        val cap = CobblemonMarket.config.priceHistorySize
        while (state.priceHistory.size > cap) {
            state.priceHistory.removeAt(0)
        }
        save()
    }
}
