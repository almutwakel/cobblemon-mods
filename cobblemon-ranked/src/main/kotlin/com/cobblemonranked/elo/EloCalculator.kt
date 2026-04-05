package com.cobblemonranked.elo

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure ELO calculation engine with no Minecraft dependencies.
 *
 * Standard ELO formula:
 *   expected = 1 / (1 + 10^((opponentElo - myElo) / 400))
 *   newElo   = oldElo + K * (actual - expected)
 *
 * Results are always clamped to [minimumElo, ∞).
 */
object EloCalculator {

    /**
     * Computes the expected score for [myElo] against [opponentElo].
     * Returns a value in (0.0, 1.0).
     */
    private fun expectedScore(myElo: Int, opponentElo: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentElo - myElo) / 400.0))

    /**
     * Calculates new ELO ratings after a battle.
     *
     * @param winnerElo   The winner's current ELO.
     * @param loserElo    The loser's current ELO.
     * @param kFactor     K-factor controlling the maximum points exchanged per match.
     * @param minimumElo  Floor below which no rating may fall.
     * @return A [Pair] of (newWinnerElo, newLoserElo), both >= [minimumElo].
     */
    fun calculate(
        winnerElo: Int,
        loserElo: Int,
        kFactor: Int,
        minimumElo: Int
    ): Pair<Int, Int> {
        val winnerExpected = expectedScore(winnerElo, loserElo)
        val loserExpected  = expectedScore(loserElo, winnerElo)

        val newWinner = winnerElo + (kFactor * (1.0 - winnerExpected)).roundToInt()
        val newLoser  = loserElo  + (kFactor * (0.0 - loserExpected)).roundToInt()

        return Pair(
            maxOf(newWinner, minimumElo),
            maxOf(newLoser,  minimumElo)
        )
    }

    /**
     * Simulates an inactivity decay by computing the ELO loss from a virtual loss
     * against [decayOpponentElo] (typically 1200).
     *
     * @param currentElo        The player's current ELO.
     * @param decayOpponentElo  The reference opponent ELO used for decay (usually 1200).
     * @param kFactor           K-factor controlling the maximum points exchanged.
     * @param minimumElo        Floor below which the rating cannot fall.
     * @return The decayed ELO, clamped to [minimumElo].
     */
    fun decayElo(
        currentElo: Int,
        decayOpponentElo: Int,
        kFactor: Int,
        minimumElo: Int
    ): Int {
        val myExpected = expectedScore(currentElo, decayOpponentElo)
        val decayed = currentElo + (kFactor * (0.0 - myExpected)).roundToInt()
        return maxOf(decayed, minimumElo)
    }
}
