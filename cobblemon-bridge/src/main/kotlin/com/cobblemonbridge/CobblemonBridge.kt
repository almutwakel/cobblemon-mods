package com.cobblemonbridge

import com.cobblemonbridge.adapters.CobbleloootsAdapter
import com.cobblemonbridge.battle.AdjustLevelHook
import com.cobblemonbridge.battle.GivePartyExpHook
import com.cobblemonbridge.battle.GymDefeatHook
import com.cobblemonbridge.commands.QuestCommand
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Tag-driven hooks that bridge Cobblemon's systems. Each hook lives in its own object and is
 * activated by a `cobblemon_bridge:<hook>/<arg>` tag on an entity. Adding a new hook means:
 *
 *   1. Adding a new tag prefix constant in `tags/BridgeTags`.
 *   2. Adding a new hook object that listens to the relevant Cobblemon or NeoForge event.
 *   3. Wiring it here in `init`.
 *
 * Hooks intentionally don't talk to each other — each one reads its own tag, applies its own
 * effect. The mod stays a thin layer of one-line bridges.
 */
@Mod(CobblemonBridge.MOD_ID)
class CobblemonBridge(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Bridge initializing...")

        AdjustLevelHook.registerEvents()
        NeoForge.EVENT_BUS.register(AdjustLevelHook)
        NeoForge.EVENT_BUS.register(GivePartyExpHook)
        GymDefeatHook.registerEvents()
        NeoForge.EVENT_BUS.register(GymDefeatHook)

        val cobbleloots = CobbleloootsAdapter.isPresent()
        if (cobbleloots) {
            NeoForge.EVENT_BUS.register(CobbleloootsAdapter)
        }

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)

        logger.info(
            "Cobblemon Bridge initialized — adjust_level + give_party_exp hooks active (cobbleloots adapter: {})",
            if (cobbleloots) "on" else "off",
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        QuestCommand.register(event.dispatcher)
    }

    companion object {
        const val MOD_ID = "cobblemon_bridge"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
