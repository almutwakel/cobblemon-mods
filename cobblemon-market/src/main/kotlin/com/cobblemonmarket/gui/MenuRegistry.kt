package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object MenuRegistry {

    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, CobblemonMarket.MOD_ID)

    val SHOP: DeferredHolder<MenuType<*>, MenuType<ShopMenu>> =
        MENUS.register("shop") { ->
            IMenuTypeExtension.create<ShopMenu> { containerId, inv, _ ->
                ShopMenu(containerId, inv)
            }
        }

    val TRANSACTION: DeferredHolder<MenuType<*>, MenuType<TransactionMenu>> =
        MENUS.register("transaction") { ->
            IMenuTypeExtension.create<TransactionMenu> { containerId, inv, data ->
                val itemId = data.readUtf()
                TransactionMenu(containerId, inv, itemId)
            }
        }
}
