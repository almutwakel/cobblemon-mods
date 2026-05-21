package com.cobblemongacha.data

import kotlin.random.Random

/**
 * One species entry in an egg pool. `id` is the lowercase Cobblemon species id passed to
 * `givepokemonegg` (e.g. "pikachu", "jangmo-o"). `hasHiddenAbility` is the flag from the
 * pool CSV — drives filtering when a loot entry requires Hidden Ability.
 *
 * `notes` carries any annotation from the CSV (e.g. "Own Tempo", "Yes (both forms)") so the
 * data isn't lost; the code doesn't act on it for v1.
 */
data class EggSpecies(
    val id: String,
    val hasHiddenAbility: Boolean,
    val notes: String = "",
)

/**
 * Four-tier pool of Pokémon species available for gacha eggs. Keys are normalised lowercase
 * with underscores: "common", "uncommon", "rare", "ultra_rare".
 */
data class EggPools(val byTier: Map<String, List<EggSpecies>>) {

    /**
     * Pick a species id for the given tier, optionally requiring it has a Hidden Ability slot.
     * Returns null if the pool is unknown or filtering leaves no candidates.
     */
    fun pick(tier: String, requireHiddenAbility: Boolean = false, random: Random = Random.Default): String? {
        val key = normaliseTier(tier)
        val pool = byTier[key] ?: return null
        val candidates = if (requireHiddenAbility) pool.filter { it.hasHiddenAbility } else pool
        if (candidates.isEmpty()) return null
        return candidates.random(random).id
    }

    companion object {
        /** Lower-snake-case normaliser: "Ultra Rare" → "ultra_rare", "common" → "common". */
        fun normaliseTier(s: String): String =
            s.trim().lowercase().replace(' ', '_').replace('-', '_')
    }
}
