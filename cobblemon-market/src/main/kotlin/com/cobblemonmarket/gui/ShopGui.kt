package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.pricing.PricingEngine
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items

class ShopGui(private val player: ServerPlayer) {

    fun open() {
        val items = CobblemonMarket.items
        val config = CobblemonMarket.config
        val store = CobblemonMarket.marketStore

        val rows = ((items.size + 8) / 9).coerceIn(1, 6)
        val menuType = when (rows) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }

        val gui = SimpleGui(menuType, player, false)
        gui.title = Component.literal("Market Shop")

        var slot = 0
        for ((itemId, itemEntry) in items) {
            if (slot >= rows * 9) break

            val state = store.getOrCreate(itemId)
            val sellCount = state.transactions.count { it.type == "sell" }
            val buyCount = state.transactions.count { it.type == "buy" }

            val currentBuyPrice = PricingEngine.buyPrice(itemEntry.baseSellPrice, state.priceFactor, config.spreadBase)
            val currentSellPrice = PricingEngine.sellPrice(
                itemEntry.baseSellPrice, state.priceFactor,
                sellCount, buyCount, config.spreadBase, config.spreadExtra
            )

            val mcItem = try {
                BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
            } catch (e: Exception) {
                Items.PAPER
            }

            val factorPercent = (state.priceFactor * 100).toInt()

            gui.setSlot(slot, GuiElementBuilder(mcItem)
                .setName(Component.literal(formatItemName(itemId)))
                .setLore(listOf(
                    Component.literal("Sell to shop: $currentSellPrice PokeDollars"),
                    Component.literal("Buy from shop: $currentBuyPrice PokeDollars"),
                    Component.literal("Demand: $factorPercent%"),
                    Component.literal(""),
                    Component.literal("Click to buy or sell")
                ))
                .setCallback { _, _, _ ->
                    TransactionGui(player, itemId, itemEntry, state).open()
                }
                .build())
            slot++
        }

        gui.open()
    }

    companion object {
        fun formatItemName(itemId: String): String {
            return itemId.substringAfter(":")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }
}
