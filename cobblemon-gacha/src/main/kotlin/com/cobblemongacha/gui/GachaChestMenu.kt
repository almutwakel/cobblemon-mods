package com.cobblemongacha.gui

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType

/**
 * Thin subclass of vanilla `ChestMenu` that exposes the container-accepting constructor (which is
 * protected on the vanilla class). Importantly we pass a VANILLA `MenuType` (GENERIC_9x1 etc.) so
 * the client doesn't need any mod-side registry sync — every Minecraft client already knows these
 * menu types.
 *
 * No `DeferredRegister<MenuType<*>>` is involved. Server uses this subclass to build the menu;
 * client uses the vanilla ChestScreen because the menu type id is vanilla.
 */
class GachaChestMenu(
    rows: Int,
    syncId: Int,
    inv: Inventory,
    container: Container,
) : ChestMenu(menuTypeForRows(rows), syncId, inv, container, rows) {

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
