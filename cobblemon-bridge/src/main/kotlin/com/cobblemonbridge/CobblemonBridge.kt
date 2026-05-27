package com.cobblemonbridge

import com.cobblemonbridge.adapters.CobbleloootsAdapter
import com.cobblemonbridge.battle.AdjustLevelHook
import com.cobblemonbridge.battle.E4GauntletHook
import com.cobblemonbridge.battle.GivePartyExpHook
import com.cobblemonbridge.battle.GymBattleAdjustHook
import com.cobblemonbridge.battle.GymDefeatHook
import com.cobblemonbridge.battle.GymPrereqHook
import com.cobblemonbridge.commands.CommandAliases
import com.cobblemonbridge.commands.HomeAliases
import com.cobblemonbridge.commands.QuestCommand
import com.cobblemonbridge.commands.WildCommand
import com.cobblemonbridge.eggs.EggDefeatHook
import com.cobblemonbridge.quests.HealQuestHook
import com.cobblemonbridge.quests.PartyLevelHook
import com.cobblemonbridge.quests.SetHomeHook
import com.cobblemonbridge.wild.TradeCapHook
import com.cobblemonbridge.wild.WildBattleAdjustHook
import com.cobblemonbridge.wild.WildBattleRewardHook
import com.cobblemonbridge.wild.WildSpawnLevelCapHook
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Tag-driven hooks that bridge Cobblemon's systems. Each hook lives in its own object and is
 * activated by a `cobblemon_bridge:<hook>/<arg>` tag on an entity, a Cobblemon event, or a
 * NeoForge event. Adding a new hook means: add the file, register it here.
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
        NeoForge.EVENT_BUS.register(GymPrereqHook)
        GymBattleAdjustHook.registerEvents()
        NeoForge.EVENT_BUS.register(GymBattleAdjustHook)
        E4GauntletHook.registerEvents()
        NeoForge.EVENT_BUS.register(E4GauntletHook)
        NeoForge.EVENT_BUS.register(SetHomeHook)
        NeoForge.EVENT_BUS.register(HealQuestHook)
        PartyLevelHook.registerEvents()
        WildBattleRewardHook.registerEvents()
        WildSpawnLevelCapHook.registerEvents()
        // WildBattleAdjustHook intentionally NOT registered: wild battles don't downlevel the
        // player's team. Gym battles DO downlevel — via GymBattleAdjustHook (above), not via
        // RCT's adjustPlayerLevels (which turned out to be dead config — its BattleRules field
        // is parsed from JSON but never consumed).
        TradeCapHook.registerEvents()
        // EggDefeatHook is timer-based now; only the server-tick subscriber is needed.
        NeoForge.EVENT_BUS.register(EggDefeatHook)

        val cobbleloots = CobbleloootsAdapter.isPresent()
        if (cobbleloots) {
            NeoForge.EVENT_BUS.register(CobbleloootsAdapter)
        }

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)

        logger.info(
            "Cobblemon Bridge initialized — adjust_level + give_party_exp + party_level + " +
                "set_home + home aliases active (cobbleloots adapter: {})",
            if (cobbleloots) "on" else "off",
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        QuestCommand.register(event.dispatcher)
        HomeAliases.register(event.dispatcher)
        CommandAliases.register(event.dispatcher)
        WildCommand.register(event.dispatcher)
    }

    companion object {
        const val MOD_ID = "cobblemon_bridge"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
