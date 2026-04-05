package com.cobblemonranked.elo

import kotlin.test.Test
import kotlin.test.assertEquals

class EloCalculatorTest {

    // -------------------------------------------------------------------------
    // calculate()
    // -------------------------------------------------------------------------

    /**
     * Equal ratings: each side has exactly 0.5 expected score.
     * With K=32 the winner gains 16 and the loser loses 16.
     */
    @Test
    fun `equal ratings - winner gains 16 loser loses 16`() {
        val (newWinner, newLoser) = EloCalculator.calculate(
            winnerElo = 1200,
            loserElo = 1200,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1216, newWinner, "Winner should gain exactly 16 points")
        assertEquals(1184, newLoser, "Loser should lose exactly 16 points")
    }

    /**
     * Higher-rated player wins: expected score is high, so the gain is small (~5).
     * 1500 beats 1200 with K=32.
     *   expected(1500 vs 1200) = 1 / (1 + 10^((1200-1500)/400)) ≈ 0.8489
     *   gain = round(32 * (1 - 0.8489)) = round(4.835) = 5
     */
    @Test
    fun `higher rated wins - small gain`() {
        val (newWinner, _) = EloCalculator.calculate(
            winnerElo = 1500,
            loserElo = 1200,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1505, newWinner, "Higher-rated winner should gain ~5 points")
    }

    /**
     * Lower-rated player wins: expected score is low, so the gain is large (~27).
     * 1200 beats 1500 with K=32.
     *   expected(1200 vs 1500) = 1 / (1 + 10^((1500-1200)/400)) ≈ 0.1511
     *   gain = round(32 * (1 - 0.1511)) = round(27.165) = 27
     */
    @Test
    fun `lower rated wins - big gain`() {
        val (newWinner, _) = EloCalculator.calculate(
            winnerElo = 1200,
            loserElo = 1500,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1227, newWinner, "Lower-rated winner should gain ~27 points")
    }

    /**
     * ELO floor is respected: a loser already at the floor cannot drop further.
     * Loser at 1000 beats floor=1000. Raw result would be ~992; clamped to 1000.
     */
    @Test
    fun `elo floor respected - loser cannot go below minimum`() {
        val (_, newLoser) = EloCalculator.calculate(
            winnerElo = 1200,
            loserElo = 1000,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1000, newLoser, "Loser ELO should be clamped to the minimum floor")
    }

    // -------------------------------------------------------------------------
    // decayElo()
    // -------------------------------------------------------------------------

    /**
     * High-rated player (1500) decays against the standard 1200 reference.
     * This is equivalent to losing to a 1200 player.
     *   expected(1500 vs 1200) ≈ 0.8489
     *   decay = round(32 * (0 - 0.8489)) = round(-27.165) = -27
     *   result = 1500 - 27 = 1473
     */
    @Test
    fun `decay for high rated player - loses ~27`() {
        val decayed = EloCalculator.decayElo(
            currentElo = 1500,
            decayOpponentElo = 1200,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1473, decayed, "1500-rated player should decay to 1473 against 1200 reference")
    }

    /**
     * Player at 1200 decays against 1200 reference — equivalent to equal-rated loss.
     *   expected(1200 vs 1200) = 0.5
     *   decay = round(32 * (0 - 0.5)) = -16
     *   result = 1200 - 16 = 1184
     */
    @Test
    fun `decay at 1200 loses 16`() {
        val decayed = EloCalculator.decayElo(
            currentElo = 1200,
            decayOpponentElo = 1200,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1184, decayed, "1200-rated player should decay to 1184 against 1200 reference")
    }

    /**
     * Decay respects the minimum ELO floor.
     * A player just above the floor should not drop below it.
     * 1008 decaying against 1200 with K=32:
     *   expected(1008 vs 1200) = 1 / (1 + 10^((1200-1008)/400)) = 1 / (1 + 10^0.48) ≈ 0.2617
     *   decay = round(32 * (0 - 0.2617)) = round(-8.374) = -8
     *   raw result = 1008 - 8 = 1000 (exactly at floor, no clamping needed here)
     * Use 1005 to ensure raw < 1000 after decay:
     *   expected(1005 vs 1200) = 1 / (1 + 10^((1200-1005)/400)) = 1 / (1 + 10^0.4875) ≈ 0.2549
     *   decay = round(32 * 0.2549) = round(8.156) = 8
     *   raw = 1005 - 8 = 997 → clamped to 1000
     */
    @Test
    fun `decay respects elo floor`() {
        val decayed = EloCalculator.decayElo(
            currentElo = 1005,
            decayOpponentElo = 1200,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1000, decayed, "Decayed ELO should be clamped to the minimum floor")
    }

    /**
     * A player already at the floor should not decay at all — the floor clamp
     * prevents any downward movement.
     * 1000 against 1200 raw would yield ~992; clamped back to 1000.
     */
    @Test
    fun `decay at floor does nothing`() {
        val decayed = EloCalculator.decayElo(
            currentElo = 1000,
            decayOpponentElo = 1200,
            kFactor = 32,
            minimumElo = 1000
        )
        assertEquals(1000, decayed, "Player at ELO floor should not decay further")
    }
}
