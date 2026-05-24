package com.cobblemongacha.gui

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

/**
 * Read-only chest menu used by `OddsMenu`. Subclasses vanilla `ChestMenu` purely so we can use a
 * vanilla `MenuType` (no client-side registry sync required) while making the chest slots
 * **non-interactive** — every click on a slot belonging to the container is dropped server-side.
 * Without this guard, players could shift-click or grab items from the odds-preview chest and
 * keep them after closing the menu (since the items live in the player's inventory once moved,
 * not the throw-away `SimpleContainer`).
 *
 * Inventory slots (the bottom 36 slots) remain interactive so players can still rearrange their
 * own hotbar while the preview is open.
 */
class GachaChestMenu(
    rows: Int,
    syncId: Int,
    inv: Inventory,
    container: Container,
) : ChestMenu(menuTypeForRows(rows), syncId, inv, container, rows) {

    private val chestSlotCount: Int = rows * 9

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        // Slots 0..chestSlotCount-1 are the preview slots. Block all interactions on them.
        // QUICK_MOVE (shift-click) and THROW (drop with Q over slot) also originate at a slot
        // and are caught by this check. SWAP (number-key hotbar swap) targets a chest slot too.
        if (slotId in 0 until chestSlotCount) return
        // QUICK_CRAFT (drag) can paint across multiple slots; only allow if none are chest slots.
        if (clickType == ClickType.QUICK_CRAFT) {
            // Vanilla tracks drag state across multiple clicked() calls; safest is to drop
            // any drag interaction that started on a chest slot — also drop if currently dragging.
            return
        }
        super.clicked(slotId, button, clickType, player)
    }

    override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
        // Shift-clicking a chest slot would pull the item into the player's inventory. Refuse.
        if (slotIndex in 0 until chestSlotCount) return ItemStack.EMPTY
        return super.quickMoveStack(player, slotIndex)
    }

    companion object {
        private fun menuTypeForRows(rows: Int): MenuType<ChestMenu> = when (rows) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }
    }
}
