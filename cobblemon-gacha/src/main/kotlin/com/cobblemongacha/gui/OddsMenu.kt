package com.cobblemongacha.gui

import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.reward.RewardGranter
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

/**
 * Read-only preview of a loot table's possible rewards. One slot per entry (up to 27); each
 * slot is the entry's representative ItemStack with appended lore showing tier and percentage.
 */
class OddsMenu(
    syncId: Int,
    private val playerInventory: Inventory,
    display: SimpleContainer,
) : AbstractContainerMenu(GachaMenuRegistry.ODDS.get(), syncId) {

    init {
        val rows = display.containerSize / 9
        for (row in 0 until rows) for (col in 0 until 9) {
            addSlot(object : Slot(display, col + row * 9, 8 + col * 18, 18 + row * 18) {
                override fun mayPickup(player: Player) = false
                override fun mayPlace(stack: ItemStack) = false
            })
        }
        val topY = 18 + rows * 18 + 14
        for (i in 0 until 3) for (j in 0 until 9) {
            addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, topY + i * 18))
        }
        for (i in 0 until 9) addSlot(Slot(playerInventory, i, 8 + i * 18, topY + 58))
    }

    override fun quickMoveStack(player: Player, slot: Int): ItemStack = ItemStack.EMPTY
    override fun stillValid(player: Player) = true

    companion object {
        fun clientStub(syncId: Int, inv: Inventory): OddsMenu = OddsMenu(syncId, inv, SimpleContainer(9))

        fun openFor(player: ServerPlayer, tier: KeyTier, table: LootTable) {
            val nonZero = table.entries.filter { it.weightPct > 0.0 }
            val rows = ((nonZero.size + 8) / 9).coerceAtMost(3).coerceAtLeast(1)
            val cap = rows * 9
            val display = SimpleContainer(cap)
            nonZero.take(cap).forEachIndexed { i, entry ->
                val stack = RewardGranter.representative(entry).copy()
                if (stack.isEmpty) return@forEachIndexed
                val newName = "${tierColor(entry.lootTier.name)}${entry.label}"
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(newName))
                val lore = mutableListOf<Component>(
                    Component.literal("§7Tier: §f${entry.lootTier.name}"),
                    Component.literal("§7Chance: §a${"%.1f".format(entry.weightPct)}%"),
                )
                if (entry.notes.isNotBlank()) lore += Component.literal("§8${entry.notes}")
                stack.set(DataComponents.LORE, ItemLore(lore))
                display.setItem(i, stack)
            }
            val provider = object : MenuProvider {
                override fun getDisplayName(): Component =
                    Component.literal("§e[${tier.displayName} Box] §7Possible Rewards")
                override fun createMenu(syncId: Int, inv: Inventory, p: Player): AbstractContainerMenu =
                    OddsMenu(syncId, inv, display)
            }
            player.openMenu(provider)
        }

        private fun tierColor(name: String): String = when (name) {
            "Floor" -> "§7"
            "Mid" -> "§b"
            "High" -> "§6"
            "Jackpot" -> "§d"
            else -> "§f"
        }
    }
}
