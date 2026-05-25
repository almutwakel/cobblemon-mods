package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
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

    private data class Pending(val gymId: Int, val isChallenge: Boolean, val capturedAtMs: Long)

    /** playerUuid → pending stash from EntityInteract. */
    private val pendingByPlayer: MutableMap<UUID, Pending> = ConcurrentHashMap()

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
        val isChallenge = BridgeTags.isGymChallenge(event.target.tags)
        pendingByPlayer[player.uuid] = Pending(gymId, isChallenge, System.currentTimeMillis())
        CobblemonBridge.logger.debug(
            "cobblemon-bridge: stashed gym_id={}{} for player {}",
            gymId, if (isChallenge) " (challenge)" else "", player.uuid,
        )
    }

    private fun applyToVictory(event: BattleVictoryEvent) {
        val now = System.currentTimeMillis()
        val losersIncludeTrainer = event.losers.any { it is TrainerBattleActor }
        for (winner in event.winners) {
            val playerActor = winner as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue

            // Branch 1: gym defeat via stashed gym_id tag (more specific, takes precedence).
            val pending = pendingByPlayer.remove(player.uuid)
            if (pending != null && now - pending.capturedAtMs <= STASH_TTL_MS) {
                val advancementId = if (pending.isChallenge) {
                    "server:beat_gym_${pending.gymId}_challenge"
                } else {
                    "server:beat_gym_${pending.gymId}"
                }
                val awarded = QuestAdvancements.award(player, advancementId, criterion = "done")
                if (awarded) {
                    CobblemonBridge.logger.info(
                        "cobblemon-bridge: awarded {} to {}", advancementId, player.gameProfile.name,
                    )
                }
                continue
            }

            // Branch 2: any other RCT/Cobblemon trainer NPC defeat fires the wild-trainer quest.
            // The advancement system makes this a one-time grant per player.
            if (losersIncludeTrainer) {
                val awarded = QuestAdvancements.award(player, "server:beat_wild_trainer", criterion = "done")
                if (awarded) {
                    CobblemonBridge.logger.info(
                        "cobblemon-bridge: awarded server:beat_wild_trainer to {}", player.gameProfile.name,
                    )
                }
            }
        }
    }

    /** Test seam. */
    internal fun clearStashForTests() = pendingByPlayer.clear()
    internal fun stashedGymIdFor(uuid: UUID): Int? = pendingByPlayer[uuid]?.gymId
}
