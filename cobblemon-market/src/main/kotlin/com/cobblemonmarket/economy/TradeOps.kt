package com.cobblemonmarket.economy

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.pricing.PricingEngine
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Outcome of a trade attempt. Carries enough info for the caller (GUI menu or chat command)
 * to render an appropriate message without re-running any pricing math.
 */
sealed class TradeResult {
    data class Success(val totalPrice: Int, val newFactor: Double) : TradeResult()
    data class InsufficientBalance(val have: Int, val need: Int) : TradeResult()
    data class InsufficientItems(val itemId: String, val have: Int, val need: Int) : TradeResult()
    object NoInventorySpace : TradeResult()
    data class UnknownItem(val itemId: String) : TradeResult()
    object EconomyFailed : TradeResult()
}

/**
 * Server-side market transaction logic. Single source of truth shared between the
 * `TransactionMenu` GUI and the `/market buy|sell` and `/market admin trade` commands.
 */
object TradeOps {

    fun buy(player: ServerPlayer, itemId: String, qty: Int): TradeResult {
        if (qty <= 0) return TradeResult.UnknownItem(itemId) // treat zero as bad input
        val entry = CobblemonMarket.items[itemId] ?: return TradeResult.UnknownItem(itemId)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val cfg = CobblemonMarket.config

        val result = PricingEngine.simulateBatchBuy(
            entry.baseSellPrice, state.priceFactor, qty,
            cfg.buyGrowth, cfg.factorCeiling, cfg.spreadBase
        )
        val totalCost = result.totalPrice

        val balance = EconomyBridge.getBalance(player.uuid)
        if (balance < totalCost) return TradeResult.InsufficientBalance(balance, totalCost)
        if (!hasInventorySpace(player, qty)) return TradeResult.NoInventorySpace

        if (!EconomyBridge.withdraw(player.uuid, totalCost)) {
            return TradeResult.EconomyFailed
        }

        repeat(qty) {
            state.priceFactor = PricingEngine.updateFactorOnBuy(state.priceFactor, cfg.buyGrowth, cfg.factorCeiling)
            CobblemonMarket.marketStore.addTransaction(itemId, "buy")
        }
        val item: Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        player.inventory.add(ItemStack(item, qty))
        CobblemonMarket.marketStore.save()

        return TradeResult.Success(totalCost, state.priceFactor)
    }

    fun sell(player: ServerPlayer, itemId: String, qty: Int): TradeResult {
        if (qty <= 0) return TradeResult.UnknownItem(itemId)
        val entry = CobblemonMarket.items[itemId] ?: return TradeResult.UnknownItem(itemId)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val cfg = CobblemonMarket.config
        val sells = state.transactions.count { it.type == "sell" }
        val buys = state.transactions.count { it.type == "buy" }

        val itemRef: Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val have = countItems(player, itemRef)
        if (have < qty) return TradeResult.InsufficientItems(itemId, have, qty)

        val result = PricingEngine.simulateBatchSell(
            entry.baseSellPrice, state.priceFactor, qty,
            cfg.sellDecay, cfg.factorFloor, sells, buys, cfg.spreadBase, cfg.spreadExtra
        )
        val totalProceeds = result.totalPrice

        removeItems(player, itemRef, qty)
        repeat(qty) {
            state.priceFactor = PricingEngine.updateFactorOnSell(state.priceFactor, cfg.sellDecay, cfg.factorFloor)
            CobblemonMarket.marketStore.addTransaction(itemId, "sell")
        }
        EconomyBridge.deposit(player.uuid, totalProceeds)
        CobblemonMarket.marketStore.save()

        return TradeResult.Success(totalProceeds, state.priceFactor)
    }

    fun countItems(player: Player, item: Item): Int =
        player.inventory.items.sumOf { if (it.item == item) it.count else 0 }

    fun removeItems(player: Player, item: Item, count: Int) {
        var remaining = count
        for (stack in player.inventory.items) {
            if (remaining <= 0) break
            if (stack.item == item) {
                val take = minOf(remaining, stack.count)
                stack.shrink(take)
                remaining -= take
            }
        }
    }

    fun hasInventorySpace(player: Player, count: Int): Boolean {
        var space = 0
        for (s in player.inventory.items) if (s.isEmpty) space += 64
        return space >= count
    }
}
