package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.QuestAdvancements
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge a `cobblemon_bridge.gym_id.<N>` tag on an NPC to `server:beat_gym_<N>` advancement
 * award on the player who beats them in battle.
 *
 * Same stash-on-interact / apply-later pattern as [AdjustLevelHook], with a longer TTL because
 * a gym battle can run several minutes. The stash is populated on `EntityInteract` and consumed
 * on `BATTLE_VICTORY` for the winning player. Lost or fled battles never reach `BATTLE_VICTORY`
 * for the player as winner, so the stash entry naturally expires.
 */
object GymDefeatHook {

    private const val STASH_TTL_MS: Long = 5 * 60 * 1000L  // 5 minutes — gym fights can drag

    /** playerUuid → (gymId, capturedAtMs). */
    private val pendingByPlayer: MutableMap<UUID, Pair<Int, Long>> = ConcurrentHashMap()

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            applyToVictory(event)
        }
    }

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val gymId = BridgeTags.findGymId(event.target.tags) ?: return
        pendingByPlayer[player.uuid] = gymId to System.currentTimeMillis()
        CobblemonBridge.logger.debug(
            "cobblemon-bridge: stashed gym_id={} for player {}", gymId, player.uuid,
        )
    }

    private fun applyToVictory(event: BattleVictoryEvent) {
        val now = System.currentTimeMillis()
        for (winner in event.winners) {
            val playerActor = winner as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue
            val pending = pendingByPlayer.remove(player.uuid) ?: continue
            val (gymId, capturedAt) = pending
            if (now - capturedAt > STASH_TTL_MS) {
                CobblemonBridge.logger.debug("gym_id stash for {} expired; skipping", player.uuid)
                continue
            }
            val awarded = QuestAdvancements.award(player, "server:beat_gym_$gymId", criterion = "done")
            if (awarded) {
                CobblemonBridge.logger.info(
                    "cobblemon-bridge: awarded server:beat_gym_{} to {}", gymId, player.gameProfile.name,
                )
            }
        }
    }

    /** Test seam. */
    internal fun clearStashForTests() = pendingByPlayer.clear()
    internal fun stashedGymIdFor(uuid: UUID): Int? = pendingByPlayer[uuid]?.first
}
