package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Deposits a configurable bounty ($1 by default) to the winning player when they win a wild
 * PvE battle. Wild = the losing side is a [PokemonBattleActor] (no [TrainerBattleActor], no
 * other [PlayerBattleActor]). Trainer wins are filtered out so the quest-track flow there
 * stays clean.
 */
object WildBattleRewardHook {

    private const val BOUNTY: Int = 1

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            applyBounty(event)
        }
    }

    private fun applyBounty(event: BattleVictoryEvent) {
        // Wild battle: losers are PokemonBattleActor only (no trainers, no players).
        val isWildBattle = event.losers.isNotEmpty() &&
            event.losers.all { it is PokemonBattleActor } &&
            event.losers.none { it is TrainerBattleActor }
        if (!isWildBattle) return

        for (winner in event.winners) {
            val playerActor = winner as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue
            EconomyBridge.deposit(player.uuid, BOUNTY)
            player.sendSystemMessage(Component.literal("§7+ §6$$BOUNTY §7for the wild defeat"))
            CobblemonBridge.logger.debug(
                "Wild defeat bounty: ${'$'}{} to {}", BOUNTY, player.gameProfile.name,
            )
        }
    }
}
