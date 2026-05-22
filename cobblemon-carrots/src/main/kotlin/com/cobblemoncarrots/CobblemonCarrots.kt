package com.cobblemoncarrots

import com.cobblemoncarrots.healer.HealerHandler
import com.cobblemoncarrots.interact.CarrotHealHandler
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonCarrots.MOD_ID)
class CobblemonCarrots(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Carrots initializing...")
        config = CarrotsConfig.load(FMLPaths.CONFIGDIR.get())
        logger.info(
            "Config: ${config.hpPerCarrot} HP/carrot, " +
                "${config.inventoryReviveCarrotCost} carrots/revive (inv), " +
                "${config.healerReviveCarrotCost} carrots/revive (healer), " +
                "\$${config.carrotPrice}/carrot"
        )

        NeoForge.EVENT_BUS.register(CarrotHealHandler)
        NeoForge.EVENT_BUS.register(HealerHandler)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)

        logger.info("Cobblemon Carrots initialized")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        register(event.dispatcher)
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // Internal commands targeted by the chat-clickable [CONFIRM] / [CANCEL] in the healer
        // prompt. Players never type these directly — they click the chat element.
        dispatcher.register(
            Commands.literal("cobblemoncarrots")
                .then(Commands.literal("heal")
                    .then(Commands.literal("confirm").executes { ctx ->
                        ctx.source.player?.let { HealerHandler.executeConfirm(it) }
                        1
                    })
                    .then(Commands.literal("cancel").executes { ctx ->
                        ctx.source.player?.let { HealerHandler.executeCancel(it) }
                        1
                    }))
        )
    }

    companion object {
        const val MOD_ID = "cobblemon_carrots"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
        lateinit var config: CarrotsConfig
    }
}
