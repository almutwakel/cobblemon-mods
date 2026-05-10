package com.cobblemonmarket

import com.cobblemonmarket.commands.MarketCommands
import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.data.MarketStore
import com.cobblemonmarket.data.PlayerSpendStore
import com.cobblemonmarket.pricing.PricingEngine
import com.cobblemonmarket.shop.ShopkeeperManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonMarket : ModInitializer {
    const val MOD_ID = "cobblemon-market"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: MarketConfig
    var items: Map<String, ItemEntry> = emptyMap()
    lateinit var marketStore: MarketStore
    lateinit var playerSpendStore: PlayerSpendStore

    override fun onInitialize() {
        logger.info("Cobblemon Market initializing...")

        val configDir = FabricLoader.getInstance().configDir
        config = MarketConfig.load(configDir)
        items = ItemConfig.load(configDir)
        marketStore = MarketStore(configDir)
        marketStore.load()
        playerSpendStore = PlayerSpendStore(configDir)
        playerSpendStore.load()

        ShopkeeperManager.init(configDir)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            MarketCommands.register(dispatcher)
        }

        // Hourly price recovery
        var recoveryTickCounter = 0
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            recoveryTickCounter++
            if (recoveryTickCounter % 72000 == 0) { // Every hour at 20 tps
                applyRecoveryToAll()
            }
        }

        logger.info("Cobblemon Market initialized! ${items.size} items, market state loaded.")
    }

    private fun applyRecoveryToAll() {
        var updated = false
        for ((itemId, _) in items) {
            val state = marketStore.getOrCreate(itemId)
            val oldFactor = state.priceFactor
            state.priceFactor = PricingEngine.applyRecovery(
                oldFactor, config.recoveryRatePerHour, config.factorCeiling
            )
            if (state.priceFactor != oldFactor) updated = true
        }
        if (updated) {
            marketStore.save()
            logger.info("Hourly price recovery applied")
        }
    }
}
