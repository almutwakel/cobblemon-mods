package com.cobblemonranked

import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.data.EloStore
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonRanked : ModInitializer {
    const val MOD_ID = "cobblemon-ranked"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: RankedConfig
    lateinit var eloStore: EloStore

    override fun onInitialize() {
        logger.info("Cobblemon Ranked initializing...")
        val configDir = FabricLoader.getInstance().configDir
        config = RankedConfig.load(configDir)
        eloStore = EloStore(configDir)
        eloStore.load()
        logger.info("Config loaded: startingElo=${config.startingElo}, kFactor=${config.kFactor}")
        logger.info("ELO data loaded: ${eloStore.getAll().size} players")
    }
}
