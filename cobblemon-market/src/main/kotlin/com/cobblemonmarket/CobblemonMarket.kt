package com.cobblemonmarket

import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.data.MarketStore
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonMarket : ModInitializer {
    const val MOD_ID = "cobblemon-market"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: MarketConfig
    lateinit var items: Map<String, ItemEntry>
    lateinit var marketStore: MarketStore

    override fun onInitialize() {
        logger.info("Cobblemon Market initializing...")
        val configDir = FabricLoader.getInstance().configDir
        config = MarketConfig.load(configDir)
        items = ItemConfig.load(configDir)
        marketStore = MarketStore(configDir)
        marketStore.load()
        logger.info("Market config loaded: ${items.size} items configured")
    }
}
