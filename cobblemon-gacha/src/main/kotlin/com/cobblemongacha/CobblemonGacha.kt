package com.cobblemongacha

import com.cobblemongacha.commands.GachaCommands
import com.cobblemongacha.config.EggPoolLoader
import com.cobblemongacha.config.GachaConfig
import com.cobblemongacha.config.LootTableLoader
import com.cobblemongacha.data.EggPools
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.PlayerGachaStore
import com.cobblemongacha.gui.RollMenu
import com.cobblemongacha.interaction.CrateInteractionHandler
import com.cobblemongacha.interaction.KeyGrantHooks
import com.cobblemongacha.util.TickScheduler
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonGacha.MOD_ID)
class CobblemonGacha(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Gacha initializing...")

        val configDir = FMLPaths.CONFIGDIR.get()
        config = GachaConfig.load(configDir)
        tables = LootTableLoader.loadAll(configDir)
        eggPools = EggPoolLoader.loadAll(configDir)
        playerStore = PlayerGachaStore(configDir)
        playerStore.load()

        // No custom MenuType registration — RollMenu/OddsMenu use vanilla ChestMenu so the
        // mod stays server-side-only (no client-side install required).

        KeyGrantHooks.registerCobblemonHooks()

        NeoForge.EVENT_BUS.register(CrateInteractionHandler)
        NeoForge.EVENT_BUS.register(KeyGrantHooks)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)
        NeoForge.EVENT_BUS.addListener(::onContainerClose)
        NeoForge.EVENT_BUS.addListener(::onLoggedOut)

        logger.info("Cobblemon Gacha initialized — ${tables.size} tables, ${playerStore.getAll().size} players loaded")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        GachaCommands.register(event.dispatcher)
    }

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        TickScheduler.onServerTickPost(event)
    }

    private fun onContainerClose(event: PlayerContainerEvent.Close) {
        val player = event.entity as? ServerPlayer ?: return
        // We use vanilla ChestMenu, so identify the gacha menu by tracking active rolls
        // by UUID rather than `containerMenu is RollMenu`.
        if (RollMenu.isRolling(player.uuid)) RollMenu.onPlayerClosedContainer(player)
    }

    private fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        RollMenu.onPlayerLoggedOut(player)
        playerStore.save()
    }

    companion object {
        const val MOD_ID = "cobblemon_gacha"
        const val PERSISTENCE_DIR_NAME = "cobblemon-gacha"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: GachaConfig
        lateinit var tables: Map<KeyTier, LootTable>
        lateinit var eggPools: EggPools
        lateinit var playerStore: PlayerGachaStore
    }
}
