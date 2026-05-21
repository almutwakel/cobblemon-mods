package com.cobblemonbridge.battle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges entity-tag intent → Cobblemon `BattleFormat.adjustLevel` at battle start.
 *
 * The pattern: at entity-interact time we know *which* entity the player clicked, but the battle
 * hasn't started yet. By the time `BATTLE_STARTED_PRE` fires, the entity is no longer accessible
 * via the battle actors (built-in `TrainerBattleActor` doesn't expose an entity ref). So we stash
 * the desired adjustLevel keyed by the interacting player's UUID at interact time, then consume
 * the stash on battle start.
 *
 * Stash entries auto-expire after [STASH_TTL_MS] in case the interact didn't actually trigger a
 * battle (player walked away, NPC not battle-capable, etc.) — prevents stale entries from
 * applying to an unrelated battle the player starts later.
 */
object AdjustLevelHook {

    private const val STASH_TTL_MS: Long = 5_000L

    /** playerUuid → (desiredLevel, capturedAtMs). */
    private val pendingByPlayer: MutableMap<UUID, Pair<Int, Long>> = ConcurrentHashMap()

    /** Wire both the NeoForge event listener (interact) and the Cobblemon event subscriber. */
    fun registerEvents() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { event ->
            applyToBattle(event.battle)
        }
    }

    /** Public for testing; called by `BATTLE_STARTED_PRE`. */
    internal fun applyToBattle(battle: com.cobblemon.mod.common.api.battles.model.PokemonBattle) {
        // Find any player actor on either side whose UUID has a fresh stash.
        val now = System.currentTimeMillis()
        val playerUuids = battle.sides
            .flatMap { it.actors.toList() }
            .flatMap { it.uuid.let { uuid -> listOf(uuid) } } // actor.uuid is the actor's, not necessarily player's
        // The actor's UUID equals the player's UUID for PlayerBattleActor (Cobblemon convention).
        for (uuid in playerUuids) {
            val pending = pendingByPlayer.remove(uuid) ?: continue
            val (level, capturedAt) = pending
            if (now - capturedAt > STASH_TTL_MS) {
                CobblemonBridge.logger.debug(
                    "Stash for {} expired ({}ms old); skipping adjustLevel apply", uuid, now - capturedAt,
                )
                continue
            }
            battle.format.adjustLevel = level
            CobblemonBridge.logger.info(
                "cobblemon-bridge: applied adjustLevel={} to battle {} (triggered by player {})",
                level, battle.battleId, uuid,
            )
            return // first hit wins; one tag per battle
        }
    }

    /** NeoForge listener — captures the target entity's adjust-level tag for the next battle. */
    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? Player ?: return
        val target: Entity = event.target
        val level = BridgeTags.findAdjustLevel(target.tags) ?: return
        pendingByPlayer[player.uuid] = level to System.currentTimeMillis()
        CobblemonBridge.logger.debug(
            "cobblemon-bridge: stashed adjustLevel={} for player {} (target entity {})",
            level, player.uuid, target.uuid,
        )
    }

    /** Test seam: clear stash between cases. */
    internal fun clearStashForTests() = pendingByPlayer.clear()

    /** Test seam: read stash without removing. */
    internal fun stashedLevelFor(uuid: UUID): Int? = pendingByPlayer[uuid]?.first
}
