package com.cobblemongacha

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonGacha.MOD_ID)
class CobblemonGacha(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Gacha initializing (scaffold)…")
    }

    companion object {
        const val MOD_ID = "cobblemon_gacha"
        const val PERSISTENCE_DIR_NAME = "cobblemon-gacha"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
