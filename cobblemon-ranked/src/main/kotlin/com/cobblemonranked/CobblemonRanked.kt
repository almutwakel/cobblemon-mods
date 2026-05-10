package com.cobblemonranked

import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.challenge.ChallengeManager
import com.cobblemonranked.commands.RankedCommands
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.data.EloStore
import com.cobblemonranked.data.TeamStore
import com.cobblemonranked.decay.DecayManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object CobblemonRanked : ModInitializer {
    const val MOD_ID = "cobblemon-ranked"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: RankedConfig
    lateinit var eloStore: EloStore
    lateinit var challengeManager: ChallengeManager
    lateinit var teamStore: TeamStore

    override fun onInitialize() {
        logger.info("Cobblemon Ranked initializing...")

        val configDir = FabricLoader.getInstance().configDir
        config = RankedConfig.load(configDir)
        eloStore = EloStore(configDir)
        eloStore.load()
        challengeManager = ChallengeManager()
        teamStore = TeamStore(configDir)

        RankedBattleManager.registerEvents()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RankedCommands.register(dispatcher)
        }

        var tickCounter = 0
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++
            if (tickCounter % 100 == 0) { // Every 5 seconds
                challengeManager.cleanupExpired()
            }
            if (tickCounter % 1200 == 0) { // Every 60 seconds
                DecayManager.tryDailyDecay(server)
            }
        }

        logger.info("Cobblemon Ranked initialized! ${eloStore.getAll().size} players loaded.")
    }
}
