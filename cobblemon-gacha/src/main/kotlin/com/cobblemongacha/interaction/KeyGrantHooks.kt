package com.cobblemongacha.interaction

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.util.TickScheduler
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.time.LocalDate

/**
 * Wires the two daily key grants:
 *   - Login: PlayerEvent.PlayerLoggedInEvent (NeoForge bus)
 *   - First PvP ranked win of the day: CobblemonEvents.BATTLE_VICTORY (Cobblemon bus)
 *
 * Each grant is gated on `lastLoginGrantDate` / `lastRankedGrantDate` matching today.
 */
object KeyGrantHooks {

    fun registerCobblemonHooks() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            // Mirror the pattern from RankedBattle.kt:
            // Detect PvP: at least 2 distinct PlayerBattleActor instances in the battle.
            val allPlayerActors = event.battle.actors.filterIsInstance<PlayerBattleActor>()
            if (allPlayerActors.size < 2) return@subscribe

            // Iterate winning player actors exactly as RankedBattle.kt does.
            val winners = event.winners.filterIsInstance<PlayerBattleActor>()
            for (actor in winners) {
                val player = actor.entity ?: continue
                tryGrantRanked(player)
            }
        }
    }

    @SubscribeEvent
    fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        // 10-second delay (200 ticks) so the daily key doesn't race the starter-kit grant.
        // Starter-kit fires on first-time players and rewrites slots 0–7 a few ticks after
        // login — without this delay the key lands in slot 0, then the kit overwrites it.
        TickScheduler.later(200) { tryGrantLogin(player) }
    }

    private fun tryGrantLogin(player: ServerPlayer) {
        if (!player.isAlive) return  // player disconnected before the delay finished
        val today = LocalDate.now().toString()
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.lastLoginGrantDate == today) return
        data.lastLoginGrantDate = today
        CobblemonGacha.playerStore.save()
        val stack = KeyItems.build(KeyTier.COMMON)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
        player.sendSystemMessage(Component.literal("§e[Gacha] Daily login bonus: §6+1 Common Key"))
        CobblemonGacha.logger.info("Granted login key to {}", player.name.string)
    }

    private fun tryGrantRanked(player: ServerPlayer) {
        val today = LocalDate.now().toString()
        val data = CobblemonGacha.playerStore.getOrCreate(player.uuid, player.name.string)
        if (data.lastRankedGrantDate == today) return
        data.lastRankedGrantDate = today
        CobblemonGacha.playerStore.save()
        val stack = KeyItems.build(KeyTier.COMMON)
        if (!player.inventory.add(stack)) player.drop(stack, false)
        player.sendSystemMessage(Component.literal("§e[Gacha] First ranked win today: §6+1 Common Key"))
        CobblemonGacha.logger.info("Granted ranked key to {}", player.name.string)
    }
}
