package com.cobblemonranked

import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.challenge.ChallengeManager
import com.cobblemonranked.commands.RankedCommands
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.data.EloStore
import com.cobblemonranked.data.TeamStore
import com.cobblemonranked.decay.DecayManager
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CobblemonRanked.MOD_ID)
class CobblemonRanked(modBus: IEventBus, container: ModContainer) {

    init {
        logger.info("Cobblemon Ranked initializing...")

        val configDir = FMLPaths.CONFIGDIR.get()
        config = RankedConfig.load(configDir)
        eloStore = EloStore(configDir)
        eloStore.load()
        challengeManager = ChallengeManager()
        teamStore = TeamStore(configDir)

        RankedBattleManager.registerEvents()

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerTickPost)

        logger.info("Cobblemon Ranked initialized! ${eloStore.getAll().size} players loaded.")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        RankedCommands.register(event.dispatcher)
    }

    private var tickCounter: Int = 0

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        tickCounter++
        if (tickCounter % 100 == 0) {
            challengeManager.cleanupExpired()
        }
        if (tickCounter % 1200 == 0) {
            DecayManager.tryDailyDecay(event.server)
        }
    }

    companion object {
        const val MOD_ID = "cobblemon_ranked"
        const val PERSISTENCE_DIR_NAME = "cobblemon-ranked"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var config: RankedConfig
        lateinit var eloStore: EloStore
        lateinit var challengeManager: ChallengeManager
        lateinit var teamStore: TeamStore
    }
}
