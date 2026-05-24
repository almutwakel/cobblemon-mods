package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.LevelCap

/**
 * Clamps wild Pokémon spawn levels (and evolution stage) when a too-strong species rolls in a
 * low-cap player's area. DOES NOT cancel the spawn — that would nerf encounter rates for
 * low-cap players. Instead we:
 *   1. Pick a target level uniformly at random in `[cap - TOLERANCE_DOWN, cap + TOLERANCE_UP]`.
 *   2. Walk back the species' pre-evolution chain through any [LevelUpEvolution] whose
 *      [LevelRequirement.minLevel] exceeds the target. Avoids the "level 18 Blastoise" surprise
 *      — a clamped Blastoise becomes a Wartortle (or Squirtle if the target's low enough).
 *      Non-level evolutions (stone, trade, friendship) halt the walk; we don't try to undo a
 *      wild Raichu into Pikachu.
 *   3. Mutate `pokemon.species` and `pokemon.level`, then re-init the moveset so the new form
 *      learns level-appropriate moves rather than carrying Blastoise's Hydro Pump on a
 *      freshly-down-evolved Squirtle.
 *
 * Trigger: re-roll when `level > cap`. Anything at or under cap rides through unchanged.
 *
 * Legendaries and mythicals are intentionally exempt — they're rare, their high level is the
 * whole point of the encounter, and a low-level player ought to be able to *find* one (catching
 * is a separate problem).
 *
 * "Nearby" = within [SCAN_RADIUS] blocks of the spawn. Multi-player: take the max cap (so
 * high-progression players in the area still raise the ceiling for everyone).
 */
object WildSpawnLevelCapHook {

    /** Clamp range for over-cap spawns: `[cap - TOLERANCE_DOWN, cap]` (inclusive). */
    private const val TOLERANCE_DOWN: Int = 5
    private const val SCAN_RADIUS: Double = 64.0

    fun registerEvents() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.NORMAL) { event ->
            val entity = event.entity
            val pokemon = entity.pokemon
            // Legendaries and mythicals spawn unmodified — chase the fantasy, not the formula.
            if (pokemon.isLegendary() || pokemon.isMythical()) return@subscribe
            val originalSpecies = pokemon.species
            val originalLevel = pokemon.level

            val server = entity.server ?: return@subscribe
            val candidates = server.playerList.players.filter { p ->
                p.level() === entity.level() &&
                    p.distanceToSqr(entity) <= SCAN_RADIUS * SCAN_RADIUS
            }
            val cap = if (candidates.isEmpty()) LevelCap.BASE else LevelCap.highestInRange(candidates)
            if (LevelCap.isUncapped(cap)) return@subscribe  // E4-complete player nearby → no clamp

            if (originalLevel <= cap) return@subscribe  // already at or under cap, leave alone

            val targetLevel = (cap - TOLERANCE_DOWN..cap).random().coerceAtLeast(1)
            val targetSpecies = walkBackForLevel(originalSpecies, targetLevel)
            if (targetSpecies !== originalSpecies) {
                pokemon.species = targetSpecies
                pokemon.initializeMoveset(false)
            }
            pokemon.level = targetLevel
            CobblemonBridge.logger.debug(
                "Wild spawn clamped: {} L{} → {} L{} (cap {}, nearby players: {})",
                originalSpecies.name, originalLevel, targetSpecies.name, targetLevel, cap, candidates.size,
            )
        }
    }

    /**
     * Walks back through [start]'s pre-evolution chain while the level threshold of the
     * incoming [LevelUpEvolution] exceeds [targetLevel]. Stops at the first stage whose
     * evolution-in threshold is ≤ target, or at a non-level evolution (no [LevelRequirement]
     * present), or at the base form (no [Species.preEvolution]).
     *
     * Examples (target=14):
     *   - Blastoise: Wartortle evolves to Blastoise at L36 → step back. Squirtle evolves to
     *     Wartortle at L16 → step back. Squirtle has no preEvolution → stop. Returns Squirtle.
     *   - Charizard (target=15): same chain step-back to Charmander.
     *   - Raichu (target=14): preEvolution Pikachu, but Pikachu→Raichu is a
     *     `ItemInteractionEvolution` (Thunder Stone), no [LevelRequirement] → stop. Returns
     *     Raichu (level-only clamp).
     */
    private fun walkBackForLevel(start: Species, targetLevel: Int): Species {
        var current = start
        var guard = 0
        while (guard++ < 8) {  // sanity cap — no realistic chain is longer than 3 (Caterpie line ≤ 3)
            val pre = current.preEvolution ?: return current
            val prior = pre.species ?: return current
            val evo = prior.evolutions
                .filterIsInstance<LevelUpEvolution>()
                .firstOrNull { it.result.species?.equals(current.name, ignoreCase = true) == true }
                ?: return current  // non-level evolution → don't de-evolve further
            val threshold = evo.requirements
                .filterIsInstance<LevelRequirement>()
                .firstOrNull()?.minLevel
                ?: return current
            if (threshold <= targetLevel) return current
            current = prior
        }
        return current
    }
}
