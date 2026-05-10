package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.pricing.PricingEngine
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore

class TransactionMenu(
    containerId: Int,
    private val playerInventory: Inventory,
    private val itemId: String,
) : AbstractContainerMenu(MenuRegistry.TRANSACTION.get(), containerId) {

    private val display = SimpleContainer(27)
    private var quantity: Int = 1

    init {
        for (row in 0 until 3) for (col in 0 until 9) {
            addSlot(DisplaySlot(display, row * 9 + col, 8 + col * 18, 18 + row * 18))
        }
        repaint()
    }

    private fun repaint() {
        for (i in 0 until display.containerSize) display.setItem(i, ItemStack.EMPTY)

        display.setItem(SLOT_PREVIEW, buildPreview())

        display.setItem(SLOT_MINUS_10, button(Items.RED_STAINED_GLASS_PANE, "§c-10"))
        display.setItem(SLOT_MINUS_5,  button(Items.RED_STAINED_GLASS_PANE, "§c-5"))
        display.setItem(SLOT_MINUS_1,  button(Items.RED_STAINED_GLASS_PANE, "§c-1"))
        display.setItem(SLOT_PLUS_1,   button(Items.LIME_STAINED_GLASS_PANE, "§a+1"))
        display.setItem(SLOT_PLUS_5,   button(Items.LIME_STAINED_GLASS_PANE, "§a+5"))
        display.setItem(SLOT_PLUS_10,  button(Items.LIME_STAINED_GLASS_PANE, "§a+10"))

        display.setItem(SLOT_BUY,  button(Items.EMERALD, "§aBUY $quantity"))
        display.setItem(SLOT_SELL, button(Items.GOLD_INGOT, "§eSELL $quantity"))
        display.setItem(SLOT_BACK, button(Items.BARRIER, "§7Back"))

        broadcastChanges()
    }

    private fun buildPreview(): ItemStack {
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val stack = ItemStack(item, quantity.coerceAtMost(64))
        val entry = CobblemonMarket.items[itemId] ?: return stack
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val cfg = CobblemonMarket.config

        val sells = state.transactions.count { it.type == "sell" }
        val buys = state.transactions.count { it.type == "buy" }

        val buyResult = PricingEngine.simulateBatchBuy(
            entry.baseSellPrice, state.priceFactor, quantity,
            cfg.buyGrowth, cfg.factorCeiling, cfg.spreadBase
        )
        val sellResult = PricingEngine.simulateBatchSell(
            entry.baseSellPrice, state.priceFactor, quantity,
            cfg.sellDecay, cfg.factorFloor, sells, buys, cfg.spreadBase, cfg.spreadExtra
        )

        val lore = mutableListOf<Component>(
            Component.literal("§7Quantity: §f$quantity"),
            Component.literal("§aBuy total: §f$${buyResult.totalPrice}"),
            Component.literal("§eSell total: §f$${sellResult.totalPrice}"),
            Component.literal(""),
        )
        if (buyResult.perUnitPrices.isNotEmpty()) {
            val preview = buyResult.perUnitPrices.take(5).joinToString(", ") { "$$it" } +
                if (buyResult.perUnitPrices.size > 5) " ..." else ""
            lore += Component.literal("§7Buy per-unit: §f$preview")
        }
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    private fun button(item: Item, name: String): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        return stack
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val sp = player as? ServerPlayer ?: return
        when (slotId) {
            SLOT_MINUS_10 -> { quantity = (quantity - 10).coerceAtLeast(1); repaint() }
            SLOT_MINUS_5  -> { quantity = (quantity - 5).coerceAtLeast(1);  repaint() }
            SLOT_MINUS_1  -> { quantity = (quantity - 1).coerceAtLeast(1);  repaint() }
            SLOT_PLUS_1   -> { quantity = (quantity + 1).coerceAtMost(MAX_QTY); repaint() }
            SLOT_PLUS_5   -> { quantity = (quantity + 5).coerceAtMost(MAX_QTY); repaint() }
            SLOT_PLUS_10  -> { quantity = (quantity + 10).coerceAtMost(MAX_QTY); repaint() }
            SLOT_BUY  -> performBuy(sp)
            SLOT_SELL -> performSell(sp)
            SLOT_BACK -> { sp.closeContainer(); ShopMenuProvider.open(sp) }
        }
    }

    private fun performBuy(player: ServerPlayer) {
        when (val r = com.cobblemonmarket.economy.TradeOps.buy(player, itemId, quantity)) {
            is com.cobblemonmarket.economy.TradeResult.Success ->
                player.sendSystemMessage(Component.literal("§aBought $quantity for $${r.totalPrice}"))
            is com.cobblemonmarket.economy.TradeResult.InsufficientBalance ->
                player.sendSystemMessage(Component.literal("§cInsufficient balance: have $${r.have}, need $${r.need}"))
            com.cobblemonmarket.economy.TradeResult.NoInventorySpace ->
                player.sendSystemMessage(Component.literal("§cNot enough inventory space"))
            com.cobblemonmarket.economy.TradeResult.EconomyFailed ->
                player.sendSystemMessage(Component.literal("§cTransaction failed (economy unavailable)"))
            is com.cobblemonmarket.economy.TradeResult.UnknownItem ->
                player.sendSystemMessage(Component.literal("§cUnknown item: ${r.itemId}"))
            is com.cobblemonmarket.economy.TradeResult.InsufficientItems -> {} // unreachable for buy
        }
        repaint()
    }

    private fun performSell(player: ServerPlayer) {
        when (val r = com.cobblemonmarket.economy.TradeOps.sell(player, itemId, quantity)) {
            is com.cobblemonmarket.economy.TradeResult.Success ->
                player.sendSystemMessage(Component.literal("§aSold $quantity for $${r.totalPrice}"))
            is com.cobblemonmarket.economy.TradeResult.InsufficientItems ->
                player.sendSystemMessage(Component.literal("§cYou only have ${r.have} ${itemId.substringAfterLast(':')}"))
            is com.cobblemonmarket.economy.TradeResult.UnknownItem ->
                player.sendSystemMessage(Component.literal("§cUnknown item: ${r.itemId}"))
            is com.cobblemonmarket.economy.TradeResult.InsufficientBalance,
            com.cobblemonmarket.economy.TradeResult.NoInventorySpace,
            com.cobblemonmarket.economy.TradeResult.EconomyFailed -> {} // unreachable for sell
        }
        repaint()
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private class DisplaySlot(c: SimpleContainer, slot: Int, x: Int, y: Int) : Slot(c, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = false
        override fun mayPickup(player: Player) = false
    }

    companion object {
        const val MAX_QTY = 256
        const val SLOT_PREVIEW = 4
        const val SLOT_MINUS_10 = 9
        const val SLOT_MINUS_5  = 10
        const val SLOT_MINUS_1  = 11
        const val SLOT_PLUS_1   = 12
        const val SLOT_PLUS_5   = 13
        const val SLOT_PLUS_10  = 14
        const val SLOT_BUY  = 18
        const val SLOT_SELL = 22
        const val SLOT_BACK = 26
    }
}

class TransactionMenuFactory(private val itemId: String) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Trade")
    override fun createMenu(containerId: Int, inv: Inventory, player: Player): AbstractContainerMenu =
        TransactionMenu(containerId, inv, itemId)
}

object TransactionMenuProvider {
    fun open(player: ServerPlayer, itemId: String) {
        player.openMenu(TransactionMenuFactory(itemId)) { buf -> buf.writeUtf(itemId) }
    }
}
