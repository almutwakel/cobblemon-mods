package com.cobblemonranked.gui

import com.cobblemonranked.CobblemonRanked
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object MenuRegistry {

    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CobblemonRanked.MOD_ID)

    val TEAM_SELECTION: DeferredHolder<MenuType<*>, MenuType<TeamSelectionMenu>> =
        MENUS.register("team_selection") { ->
            // Client-side factory — server openMenu uses MenuProvider with real state
            IMenuTypeExtension.create<TeamSelectionMenu> { containerId, inv, _ ->
                TeamSelectionMenu.clientStub(containerId, inv)
            }
        }
}
