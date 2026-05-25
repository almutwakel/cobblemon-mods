package com.cobblemonmarket

import com.cobblemonmarket.commands.MarketCommands
import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.gui.MarketNpcHook
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.data.MarketStore
import com.cobblemonmarket.data.PlayerSpendStore
import com.cobblemonmarket.pricing.PricingEngine
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonMarket.MOD_ID)
class CobblemonMarket(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Market initializing...")

        val configDir = FMLPaths.CONFIGDIR.get()
        config = MarketConfig.load(configDir)
        items = ItemConfig.load(configDir)
        marketStore = MarketStore(configDir)
        marketStore.load()
        // Stock-based pricing needs a non-zero starting stock for any newly-configured
        // item or fresh server, otherwise prices spike to baseStock^elasticity on first read.
        marketStore.ensureInitialized(items)
        playerSpendStore = PlayerSpendStore(configDir)
        playerSpendStore.load()

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)
        NeoForge.EVENT_BUS.register(MarketNpcHook)

        logger.info("Cobblemon Market initialized! ${items.size} items, market state loaded.")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MarketCommands.register(event.dispatcher)
    }

    private var recoveryTickCounter: Int = 0

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        recoveryTickCounter++
        // 72000 ticks ≈ 1 in-game hour at 20 TPS. Restock pulls each item's stock toward
        // its baseStock — partial refill of depleted items, partial bleed-off of saturated.
        if (recoveryTickCounter % 72000 == 0) {
            applyRestockToAll()
        }
    }

    private fun applyRestockToAll() {
        var updated = false
        for ((itemId, entry) in items) {
            val state = marketStore.getOrCreate(itemId)
            val oldStock = state.stock
            state.stock = PricingEngine.applyRestock(
                oldStock, entry.baseStock, config.restockRatePerHour
            )
            if (state.stock != oldStock) updated = true
        }
        if (updated) {
            marketStore.save()
            logger.info("Hourly restock applied")
        }
    }

    companion object {
        const val MOD_ID = "cobblemon_market"
        const val PERSISTENCE_DIR_NAME = "cobblemon-market"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: MarketConfig
        var items: Map<String, ItemEntry> = emptyMap()
        lateinit var marketStore: MarketStore
        lateinit var playerSpendStore: PlayerSpendStore
    }
}
