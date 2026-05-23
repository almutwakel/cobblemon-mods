package com.cobblemoncarrots.healer

import kotlin.math.ceil

/**
 * Pure heal-cost math. Decoupled from Cobblemon types so it's unit-testable.
 *
 * Two cost paths the healer charges:
 *   1. `carrotsForRevives = faintedCount * healerReviveCarrotCost`. Each revive includes a
 *      free `hpPerCarrot`-sized heal — the fainted mon comes back at that HP, with the rest
 *      of the max-HP gap rolled into the pooled heal portion below.
 *   2. `carrotsForHealing = ceil(totalDeficit / hpPerCarrot)`, where `totalDeficit` is the
 *      sum of (max_hp - current_hp) for non-fainted mons + (max_hp - hpPerCarrot) for
 *      fainted mons (the post-revive remaining gap, clamped to 0 for tiny mons).
 *
 * "Optimal pooling" claim: manual feeding pays `ceil(deficit_i / hpPerCarrot)` per mon —
 * overflow on the last carrot per mon is wasted. The healer pools the deficit and pays
 * `ceil(Σ deficit_i / hpPerCarrot)`, which is strictly ≤ the manual sum.
 */
data class HealCost(
    val carrotsForHealing: Int,
    val carrotsForRevives: Int,
    val totalCarrots: Int,
    val carrotsInInventory: Int,
    val carrotsShort: Int,
    val moneyCost: Int,
)

object HealCalculator {

    /**
     * @param hpDeficits for non-fainted Pokémon (maxHP - currentHP).
     * @param faintedMaxHps for fainted Pokémon — each entry is that mon's max HP, used to
     *   compute the post-revive gap above the free `hpPerCarrot` first chunk.
     */
    fun compute(
        hpDeficits: List<Int>,
        faintedMaxHps: List<Int>,
        hpPerCarrot: Int,
        healerReviveCarrotCost: Int,
        carrotsInInventory: Int,
        carrotPrice: Int,
        minCarrots: Int = 0,
    ): HealCost {
        require(hpPerCarrot > 0) { "hpPerCarrot must be positive" }
        val deficitFromLiving = hpDeficits.sumOf { it.coerceAtLeast(0) }
        val deficitFromRevived = faintedMaxHps.sumOf { (it - hpPerCarrot).coerceAtLeast(0) }
        val totalDeficit = deficitFromLiving + deficitFromRevived
        val carrotsHealing = ceil(totalDeficit.toDouble() / hpPerCarrot).toInt()
        val carrotsRevives = faintedMaxHps.size * healerReviveCarrotCost
        // Enforce a minimum service fee — even a 1-HP heal costs at least minCarrots.
        // The fee gets allocated to the heal side (not revives) since revives are flat.
        val totalRaw = carrotsHealing + carrotsRevives
        val total = totalRaw.coerceAtLeast(minCarrots)
        val healingAfterMin = (total - carrotsRevives).coerceAtLeast(0)
        val short = (total - carrotsInInventory).coerceAtLeast(0)
        val money = short * carrotPrice
        return HealCost(
            carrotsForHealing = healingAfterMin,
            carrotsForRevives = carrotsRevives,
            totalCarrots = total,
            carrotsInInventory = carrotsInInventory,
            carrotsShort = short,
            moneyCost = money,
        )
    }
}
