package com.cobblemonshowcase

import com.cobblemonshowcase.commands.ShowcaseCommands
import com.cobblemonshowcase.config.ShowcaseConfig
import com.cobblemonshowcase.data.PlayerDataStore
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonShowcase : ModInitializer {
    const val MOD_ID = "cobblemon-showcase"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: ShowcaseConfig
    lateinit var playerDataStore: PlayerDataStore

    override fun onInitialize() {
        logger.info("Cobblemon Showcase initializing...")

        val configDir = FabricLoader.getInstance().configDir
        config = ShowcaseConfig.load(configDir)
        playerDataStore = PlayerDataStore(configDir)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ShowcaseCommands.register(dispatcher)
        }

        logger.info("Cobblemon Showcase initialized!")
    }
}
