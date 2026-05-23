package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.LevelCap
import net.minecraft.server.level.ServerPlayer

/**
 * One-sided level cap on the player's team in wild battles. Mutates each player Pokémon's
 * `effectedPokemon` level down to [LevelCap.forPlayer] before the battle commits. Doesn't
 * touch the original storage Pokémon — only the in-battle clone — so the level reverts when
 * the battle ends.
 *
 * Wild battle detection mirrors [WildBattleRewardHook]: losers must be `PokemonBattleActor`
 * only (no trainers, no other players).
 *
 * The wild Pokémon side stays at its spawn level (capped by [WildSpawnLevelCapHook] already).
 * Net effect: a level-50 Pikachu owner fighting a level-15 Caterpie at the pre-gym cap of 15
 * gets a fair level-15-vs-level-15 fight, with no "one-shot the wild mon" exploit.
 */
object WildBattleAdjustHook {

    fun registerEvents() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { event ->
            applyToBattle(event)
        }
    }

    private fun applyToBattle(event: BattleStartedEvent.Pre) {
        val battle = event.battle
        val actors = battle.actors.toList()
        // Wild battle: at least one player side, and the non-player side(s) are pure wild
        // PokemonBattleActor (no trainers, no other players).
        val playerActors = actors.filterIsInstance<PlayerBattleActor>()
        val opposingActors = actors.filterNot { it is PlayerBattleActor }
        if (playerActors.isEmpty()) return
        if (opposingActors.isEmpty()) return
        if (opposingActors.any { it is TrainerBattleActor }) return
        if (opposingActors.any { it is PlayerBattleActor }) return  // shouldn't happen but defensive
        if (opposingActors.none { it is PokemonBattleActor }) return

        // If the wild opposing mon is legendary or mythical, don't cap the player side — they
        // should be allowed to bring their full strength to a legendary encounter.
        val opposingMon = opposingActors.filterIsInstance<PokemonBattleActor>()
            .firstOrNull()
            ?.pokemon
            ?.effectedPokemon
        if (opposingMon != null && (opposingMon.isLegendary() || opposingMon.isMythical())) {
            return
        }

        for (actor in playerActors) {
            val player = actor.entity as? ServerPlayer ?: continue
            val cap = LevelCap.forPlayer(player)
            if (LevelCap.isUncapped(cap)) continue
            for (bp in actor.pokemonList) {
                val mon = bp.effectedPokemon
                if (mon.level > cap) {
                    val originalLevel = mon.level
                    mon.level = cap
                    CobblemonBridge.logger.debug(
                        "Wild battle level-cap: {}'s {} L{} → L{}",
                        player.gameProfile.name, mon.species.name, originalLevel, cap,
                    )
                }
            }
        }
    }
}
