package com.cobblemonranked.decay

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.elo.EloCalculator
import net.minecraft.server.MinecraftServer
import java.time.LocalDate

object DecayManager {
    private var lastDecayDate: String? = null
    var anyBattlesToday: Boolean = false
        private set

    fun recordBattle() {
        anyBattlesToday = true
    }

    /**
     * Called periodically from server tick. Checks if a new day has started.
     * If the previous day had battles, applies decay to all players who didn't battle.
     */
    fun tryDailyDecay(server: MinecraftServer) {
        val today = LocalDate.now().toString()
        if (today == lastDecayDate) return

        // New day — apply decay for yesterday's inactivity if any battles happened
        if (anyBattlesToday) {
            applyDecay(server)
        }

        lastDecayDate = today
        anyBattlesToday = false
    }

    fun forceDecay(server: MinecraftServer) {
        applyDecay(server)
    }

    private fun applyDecay(server: MinecraftServer) {
        val config = CobblemonRanked.config
        if (!config.decayEnabled) return

        val store = CobblemonRanked.eloStore
        val today = LocalDate.now().toString()
        var decayCount = 0

        for ((uuid, data) in store.getAll()) {
            if (data.lastBattleDate == today) continue
            val oldElo = data.elo
            val newElo = EloCalculator.decayElo(
                currentElo = oldElo,
                decayOpponentElo = config.startingElo,
                kFactor = config.kFactor,
                minimumElo = config.minimumElo
            )
            if (newElo != oldElo) {
                data.elo = newElo
                decayCount++
            }
        }

        if (decayCount > 0) {
            store.save()
            CobblemonRanked.logger.info("Daily decay applied to $decayCount players")
        }
    }
}
