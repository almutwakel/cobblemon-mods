package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.data.ItemState
import com.cobblemonmarket.pricing.PricingEngine
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.math.BigDecimal

class TransactionGui(
    private val player: ServerPlayer,
    private val itemId: String,
    private val itemEntry: ItemEntry,
    private val state: ItemState
) {
    private var quantity = 1
    private var isBuying = true

    fun open() {
        rebuild()
    }

    private fun rebuild() {
        val gui = SimpleGui(MenuType.GENERIC_9x4, player, false)
        val config = CobblemonMarket.config
        val itemName = ShopGui.formatItemName(itemId)

        gui.title = Component.literal(if (isBuying) "Buy $itemName" else "Sell $itemName")

        // Row 1: Mode toggle + item display
        gui.setSlot(0, GuiElementBuilder(if (isBuying) Items.EMERALD else Items.GOLD_INGOT)
            .setName(Component.literal(if (isBuying) "Mode: BUYING" else "Mode: SELLING"))
            .setLore(listOf(Component.literal("Click to switch")))
            .setCallback { _, _, _ ->
                isBuying = !isBuying
                quantity = 1
                rebuild()
            }.build())

        val mcItem = resolveItem()
        gui.setSlot(4, GuiElementBuilder(mcItem)
            .setName(Component.literal(itemName))
            .setCount(quantity.coerceIn(1, 64))
            .build())

        for (i in listOf(1, 2, 3, 5, 6, 7, 8)) {
            gui.setSlot(i, filler())
        }

        // Row 2: Quantity buttons
        val quantities = listOf(1, 5, 10, 25, 50)
        for ((i, q) in quantities.withIndex()) {
            val selected = q == quantity
            gui.setSlot(9 + i + 2, GuiElementBuilder(
                if (selected) Items.LIME_STAINED_GLASS_PANE else Items.WHITE_STAINED_GLASS_PANE
            )
                .setName(Component.literal("Qty: $q"))
                .setCount(q.coerceIn(1, 64))
                .setCallback { _, _, _ ->
                    quantity = q
                    rebuild()
                }.build())
        }
        for (i in listOf(9, 10)) {
            gui.setSlot(i, filler())
        }
        gui.setSlot(17, filler())

        // Row 3: Price preview
        val sellCount = state.transactions.count { it.type == "sell" }
        val buyCount = state.transactions.count { it.type == "buy" }
        val lore: List<Component>

        if (isBuying) {
            val result = PricingEngine.simulateBatchBuy(
                baseSellPrice = itemEntry.baseSellPrice,
                startFactor = state.priceFactor,
                quantity = quantity,
                buyGrowth = config.buyGrowth,
                factorCeiling = config.factorCeiling,
                sells = sellCount, buys = buyCount,
                spreadBase = config.spreadBase, spreadExtra = config.spreadExtra
            )
            lore = buildList {
                add(Component.literal("Buy $quantity x $itemName:"))
                result.perUnitPrices.forEachIndexed { idx, price ->
                    add(Component.literal("  #${idx + 1}: $price PokeDollars"))
                }
                add(Component.literal(""))
                add(Component.literal("Total: ${result.totalPrice} PokeDollars"))
            }
        } else {
            val result = PricingEngine.simulateBatchSell(
                baseSellPrice = itemEntry.baseSellPrice,
                startFactor = state.priceFactor,
                quantity = quantity,
                sellDecay = config.sellDecay,
                factorFloor = config.factorFloor
            )
            lore = buildList {
                add(Component.literal("Sell $quantity x $itemName:"))
                result.perUnitPrices.forEachIndexed { idx, price ->
                    add(Component.literal("  #${idx + 1}: $price PokeDollars"))
                }
                add(Component.literal(""))
                add(Component.literal("Total: ${result.totalPrice} PokeDollars"))
            }
        }

        gui.setSlot(22, GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("Price Breakdown"))
            .setLore(lore)
            .build())

        for (i in listOf(18, 19, 20, 21, 23, 24, 25, 26)) {
            gui.setSlot(i, filler())
        }

        // Row 4: Back + Confirm + Cancel
        gui.setSlot(27, GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("Back to Shop"))
            .setCallback { _, _, _ -> ShopGui(player).open() }
            .build())

        for (i in 28..33) {
            gui.setSlot(i, filler())
        }

        gui.setSlot(34, GuiElementBuilder(Items.LIME_CONCRETE)
            .setName(Component.literal("Confirm"))
            .setCallback { _, _, _ -> executeTransaction() }
            .build())

        gui.setSlot(35, GuiElementBuilder(Items.RED_CONCRETE)
            .setName(Component.literal("Cancel"))
            .setCallback { _, _, _ -> ShopGui(player).open() }
            .build())

        gui.open()
    }

    private fun executeTransaction() {
        val config = CobblemonMarket.config
        val store = CobblemonMarket.marketStore
        val mcItem = resolveItem()

        if (isBuying) {
            val sellCount = state.transactions.count { it.type == "sell" }
            val buyCount = state.transactions.count { it.type == "buy" }

            val result = PricingEngine.simulateBatchBuy(
                baseSellPrice = itemEntry.baseSellPrice,
                startFactor = state.priceFactor,
                quantity = quantity,
                buyGrowth = config.buyGrowth,
                factorCeiling = config.factorCeiling,
                sells = sellCount, buys = buyCount,
                spreadBase = config.spreadBase, spreadExtra = config.spreadExtra
            )

            val balance = getPlayerBalance()
            if (balance < result.totalPrice) {
                player.sendSystemMessage(Component.literal(
                    "[Market] Not enough PokeDollars! Need ${result.totalPrice}, have $balance."))
                return
            }
            if (!hasInventorySpace(quantity)) {
                player.sendSystemMessage(Component.literal("[Market] Not enough inventory space!"))
                return
            }

            subtractBalance(result.totalPrice)
            player.inventory.add(ItemStack(mcItem, quantity))
            state.priceFactor = result.finalFactor
            repeat(quantity) { store.addTransaction(itemId, "buy") }
            store.save()

            player.sendSystemMessage(Component.literal(
                "[Market] Bought $quantity x ${ShopGui.formatItemName(itemId)} for ${result.totalPrice} PokeDollars."))
        } else {
            val result = PricingEngine.simulateBatchSell(
                baseSellPrice = itemEntry.baseSellPrice,
                startFactor = state.priceFactor,
                quantity = quantity,
                sellDecay = config.sellDecay,
                factorFloor = config.factorFloor
            )

            val itemCount = countItems(mcItem)
            if (itemCount < quantity) {
                player.sendSystemMessage(Component.literal(
                    "[Market] You only have $itemCount x ${ShopGui.formatItemName(itemId)}."))
                return
            }

            removeItems(mcItem, quantity)
            addBalance(result.totalPrice)
            state.priceFactor = result.finalFactor
            repeat(quantity) { store.addTransaction(itemId, "sell") }
            store.save()

            player.sendSystemMessage(Component.literal(
                "[Market] Sold $quantity x ${ShopGui.formatItemName(itemId)} for ${result.totalPrice} PokeDollars."))
        }

        ShopGui(player).open()
    }

    // --- Economy bridge ---
    // Uses Cobblemon Economy API via reflection for soft dependency.
    // Replace with direct imports if cobblemon-economy is a compile dependency.

    private fun getPlayerBalance(): Int {
        return try {
            val economyClass = Class.forName("com.ryvexam.cobblemoneconomy.CobblemonEconomy")
            val instance = economyClass.getField("INSTANCE").get(null)
            val getBalance = economyClass.getMethod("getBalance", java.util.UUID::class.java)
            val balance = getBalance.invoke(instance, player.uuid) as BigDecimal
            balance.toInt()
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to get player balance via CobblemonEconomy", e)
            0
        }
    }

    private fun subtractBalance(amount: Int) {
        try {
            val economyClass = Class.forName("com.ryvexam.cobblemoneconomy.CobblemonEconomy")
            val instance = economyClass.getField("INSTANCE").get(null)
            val method = economyClass.getMethod("removeBalance",
                java.util.UUID::class.java, BigDecimal::class.java)
            method.invoke(instance, player.uuid, BigDecimal(amount))
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to subtract balance", e)
        }
    }

    private fun addBalance(amount: Int) {
        try {
            val economyClass = Class.forName("com.ryvexam.cobblemoneconomy.CobblemonEconomy")
            val instance = economyClass.getField("INSTANCE").get(null)
            val method = economyClass.getMethod("addBalance",
                java.util.UUID::class.java, BigDecimal::class.java)
            method.invoke(instance, player.uuid, BigDecimal(amount))
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to add balance", e)
        }
    }

    // --- Inventory helpers ---

    private fun countItems(item: Item): Int {
        return player.inventory.items.sumOf { stack ->
            if (stack.item == item) stack.count else 0
        }
    }

    private fun removeItems(item: Item, count: Int) {
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

    private fun hasInventorySpace(count: Int): Boolean {
        var space = 0
        for (stack in player.inventory.items) {
            if (stack.isEmpty) space += 64
        }
        return space >= count
    }

    private fun resolveItem(): Item {
        return try {
            BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        } catch (e: Exception) {
            Items.PAPER
        }
    }

    private fun filler(): ItemStack {
        return GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
            .setName(Component.literal(" "))
            .build()
    }
}
