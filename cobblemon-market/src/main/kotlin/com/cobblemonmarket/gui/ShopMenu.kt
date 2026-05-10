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
import net.minecraft.world.item.ItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore

class ShopMenu(
    containerId: Int,
    private val playerInventory: Inventory,
) : AbstractContainerMenu(MenuRegistry.SHOP.get(), containerId) {

    private val display = SimpleContainer(SLOT_COUNT)

    init {
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            addSlot(DisplaySlot(display, row * COLS + col, 8 + col * 18, 18 + row * 18))
        }
        repaint()
    }

    private fun repaint() {
        val items = CobblemonMarket.items.entries.toList()
        for (i in 0 until SLOT_COUNT) {
            display.setItem(i, if (i < items.size) buildSlot(items[i].key) else ItemStack.EMPTY)
        }
        broadcastChanges()
    }

    private fun buildSlot(itemId: String): ItemStack {
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val stack = ItemStack(item)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val entry = CobblemonMarket.items[itemId] ?: return stack
        val cfg = CobblemonMarket.config

        val sells = state.transactions.count { it.type == "sell" }
        val buys = state.transactions.count { it.type == "buy" }

        val sellPrice = PricingEngine.sellPrice(
            entry.baseSellPrice, state.priceFactor, sells, buys, cfg.spreadBase, cfg.spreadExtra
        )
        val buyPrice = PricingEngine.buyPrice(entry.baseSellPrice, state.priceFactor, cfg.spreadBase)
        val pct = (state.priceFactor * 100).toInt()
        val lore = listOf(
            Component.literal("§7Buy: §a$$buyPrice"),
            Component.literal("§7Sell: §c$$sellPrice"),
            Component.literal("§7Factor: §f$pct%"),
            Component.literal(""),
            Component.literal("§eClick to trade"),
        )
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        if (slotId !in 0 until SLOT_COUNT) return
        val itemId = CobblemonMarket.items.keys.toList().getOrNull(slotId) ?: return
        val sp = player as? ServerPlayer ?: return
        sp.closeContainer()
        TransactionMenuProvider.open(sp, itemId)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private class DisplaySlot(container: SimpleContainer, slot: Int, x: Int, y: Int) :
        Slot(container, slot, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
        override fun mayPickup(player: Player): Boolean = false
    }

    companion object {
        const val ROWS = 6
        const val COLS = 9
        const val SLOT_COUNT = ROWS * COLS
    }
}

object ShopMenuProvider : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Cobblemon Market")
    override fun createMenu(containerId: Int, inv: Inventory, player: Player): AbstractContainerMenu =
        ShopMenu(containerId, inv)

    fun open(player: ServerPlayer) {
        player.openMenu(this)
    }
}
