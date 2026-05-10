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
            CobblemonMarket.logger.info("[gui-debug] Building MenuType<ShopMenu>")
            IMenuTypeExtension.create<ShopMenu> { containerId, inv, _ ->
                CobblemonMarket.logger.info("[gui-debug] MenuType<ShopMenu> factory invoked (containerId={})", containerId)
                ShopMenu(containerId, inv)
            }
        }

    val TRANSACTION: DeferredHolder<MenuType<*>, MenuType<TransactionMenu>> =
        MENUS.register("transaction") { ->
            CobblemonMarket.logger.info("[gui-debug] Building MenuType<TransactionMenu>")
            IMenuTypeExtension.create<TransactionMenu> { containerId, inv, data ->
                val itemId = data.readUtf()
                CobblemonMarket.logger.info("[gui-debug] MenuType<TransactionMenu> factory invoked (containerId={}, itemId={})", containerId, itemId)
                TransactionMenu(containerId, inv, itemId)
            }
        }
}
