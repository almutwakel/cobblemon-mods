package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Elite Four gauntlet — gyms 20-23 must be beaten consecutively in one session. Losing,
 * fleeing, or disconnecting resets progress; the player has to restart from E4-1 (gym 20).
 *
 * State:
 *   - [unlocked] — the next E4 gym the player is allowed to challenge. Set to N+1 after winning
 *     gym N (for N in 20..22). Cleared on loss / flee / disconnect / completion (after gym 23).
 *   - [active] — the gym the player is currently fighting. Stashed at [EntityInteract] for the
 *     gym leader and consumed on the battle's end event so we know which gym just ended.
 *
 * Gating contract (used by [GymPrereqHook] for gyms 20-23):
 *   - gym 20: always allowed if base prereq met (beat_gym_10 done). [GymPrereqHook] checks that;
 *     this hook accepts every gym 20 attempt without further check.
 *   - gym 21-23: allowed only if [unlocked][uuid] == gymId. Otherwise the interact is cancelled
 *     with a chat message.
 *
 * Auto-chain (currently disabled):
 *   - On winning gym N (20..22), the player is told to challenge gym N+1 manually. The
 *     teleport-and-auto-start chain that used to fire here is parked until we revisit the
 *     gauntlet design.
 *   - On winning gym 23: clear gauntlet state and chat congrats (Champion at gym 24 is gated
 *     elsewhere on beat_gym_23, so the player walks to that one normally).
 */
object E4GauntletHook {

    private const val E4_FIRST: Int = 20
    private const val E4_LAST: Int = 23

    /** Next allowed gym in the gauntlet. Null = not in gauntlet (only gym 20 is fightable). */
    private val unlocked: MutableMap<UUID, Int> = ConcurrentHashMap()
    /** Gym currently being fought — set on [EntityInteract], cleared on battle end. */
    private val active: MutableMap<UUID, Int> = ConcurrentHashMap()

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event -> onVictory(event) }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event -> onFled(event) }
    }

    /** Called by [GymPrereqHook] for gyms 20-23. Returns true if the player may challenge. */
    fun canChallenge(player: ServerPlayer, gymId: Int): Boolean {
        if (gymId !in E4_FIRST..E4_LAST) return true
        if (gymId == E4_FIRST) return true  // entry into the gauntlet
        return unlocked[player.uuid] == gymId
    }

    /** Reason text shown to the player when [canChallenge] returns false. */
    fun lockedReason(gymId: Int): String = when {
        gymId == E4_FIRST -> ""  // gym 20 is never locked by us
        else -> "§c[Elite Four] §fYou must beat E4 ${gymId - E4_FIRST}§f first — start at §eE4 1 (Gym ${E4_FIRST})§f."
    }

    /** External entry — used by [GymBattleGate] to mark a player as currently fighting [gymId]
     *  when the battle starts via the force-battle path (which skips EntityInteract). */
    fun stashActive(uuid: java.util.UUID, gymId: Int) {
        if (gymId !in E4_FIRST..E4_LAST) return
        active[uuid] = gymId
        if (gymId == E4_FIRST) unlocked[uuid] = E4_FIRST
    }

    // ─── Stash on interact ─────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide || event.isCanceled) return
        val player = event.entity as? ServerPlayer ?: return
        val gymId = BridgeTags.findGymId(event.target.tags) ?: return
        if (gymId !in E4_FIRST..E4_LAST) return
        active[player.uuid] = gymId
        // Stamp into the gauntlet on the first E4 interact, so subsequent E4 fights can be gated.
        if (gymId == E4_FIRST) {
            unlocked[player.uuid] = E4_FIRST
        }
    }

    // ─── Battle outcomes ───────────────────────────────────────────────────
    private fun onVictory(event: BattleVictoryEvent) {
        // Winners — player wins an E4 fight
        for (actor in event.winners) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            val gym = active.remove(player.uuid) ?: continue
            if (gym !in E4_FIRST..E4_LAST) continue
            if (gym < E4_LAST) {
                val next = gym + 1
                unlocked[player.uuid] = next
                // Auto-teleport + auto-battle disabled. Player walks to the next trainer manually
                // and right-clicks them to continue; the gating ([canChallenge]) still enforces
                // order, and a loss/flee still resets the gauntlet.
                player.sendSystemMessage(Component.literal(
                    "§6[Elite Four] §fNext: §eE4 ${next - E4_FIRST + 1} (Gym $next)§f — challenge them next."
                ))
            } else {
                // Won gym 23 — gauntlet complete.
                unlocked.remove(player.uuid)
                player.sendSystemMessage(Component.literal(
                    "§6§l[Elite Four] §fGauntlet complete! §7The Champion (Gym 24) is now open."
                ))
            }
        }
        // Losers — player loses an E4 fight
        for (actor in event.losers) {
            val player = (actor as? PlayerBattleActor)?.entity as? ServerPlayer ?: continue
            val gym = active.remove(player.uuid) ?: continue
            if (gym !in E4_FIRST..E4_LAST) continue
            failGauntlet(player, "lost")
        }
    }

    private fun onFled(event: BattleFledEvent) {
        val player = event.player.entity as? ServerPlayer ?: return
        val gym = active.remove(player.uuid) ?: return
        if (gym !in E4_FIRST..E4_LAST) return
        failGauntlet(player, "fled")
    }

    private fun failGauntlet(player: ServerPlayer, reason: String) {
        if (unlocked.remove(player.uuid) != null) {
            player.sendSystemMessage(Component.literal(
                "§c[Elite Four] §fGauntlet failed ($reason). Restart from §eE4 1 (Gym ${E4_FIRST})§f."
            ))
            CobblemonBridge.logger.info(
                "E4 gauntlet ${reason} for {} — state cleared", player.gameProfile.name,
            )
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val uuid = event.entity.uuid
        val hadUnlocked = unlocked.remove(uuid) != null
        active.remove(uuid)
        if (hadUnlocked) {
            CobblemonBridge.logger.info(
                "E4 gauntlet cleared for {} on disconnect", event.entity.gameProfile.name,
            )
        }
    }

}
