package com.cobblemongacha.gui

import com.cobblemongacha.CobblemonGacha
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object GachaMenuRegistry {

    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CobblemonGacha.MOD_ID)

    val ROLL: DeferredHolder<MenuType<*>, MenuType<RollMenu>> =
        MENUS.register("roll") { ->
            IMenuTypeExtension.create<RollMenu> { id, inv, _ -> RollMenu.clientStub(id, inv) }
        }

    val ODDS: DeferredHolder<MenuType<*>, MenuType<OddsMenu>> =
        MENUS.register("odds") { ->
            IMenuTypeExtension.create<OddsMenu> { id, inv, _ -> OddsMenu.clientStub(id, inv) }
        }
}
