package com.cobblemonbridge.battle

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Gates gym battles on progression. Cancels the right-click `EntityInteract` on the gym leader
 * before RCTmod's battle-start handler runs, so the trainer stays idle and the player gets a
 * chat error.
 *
 * Progression rules:
 *   - Gym 1: no prerequisite (entry point).
 *   - Gyms 2–10 (mainline): strict linear — gym N requires beat_gym_(N-1).
 *   - Gyms 11–23 (rotating + Oak + E4): all unlock once beat_gym_10 is done. Free-for-all
 *     order within this block — matches the user's "beating gym 10 unlocks all rotating gyms
 *     + the Elite Four" intent.
 *   - Gym 24 (Champion): requires beat_gym_23 (the final E4 fight).
 *
 * Hooks at [EventPriority.HIGHEST] so we run before any other interaction handler. Because
 * cobblemon-battles use `TrainerBattleActor` which doesn't expose the trainer entity, we can't
 * inspect tags after the battle has begun — gating must happen at the interact stage where the
 * entity is still concretely available.
 */
object GymPrereqHook {

    /** Returns the prereq gym number for [gymId], or null if [gymId] has no prereq. */
    private fun prereqGymFor(gymId: Int): Int? = when (gymId) {
        in 1..1 -> null
        in 2..10 -> gymId - 1       // 2→1, 3→2, ..., 10→9
        in 11..23 -> 10             // rotating + Oak + E4 — gym 10 unlocks the block
        24 -> 23                    // Champion — needs the final E4 (Lance) done
        else -> gymId - 1           // future-proof fallback for ids > 24
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val gymId = BridgeTags.findGymId(event.target.tags) ?: return
        val isChallenge = BridgeTags.isGymChallenge(event.target.tags)

        // Challenge variant: must have beaten the mainline gym first. Skips the rest of the
        // gauntlet/linear logic — challenges are a per-gym side path.
        if (isChallenge) {
            val mainId = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_$gymId")
            val main = player.server.advancements.get(mainId)
            if (main != null && !player.advancements.getOrStartProgress(main).isDone) {
                event.isCanceled = true
                event.cancellationResult = InteractionResult.FAIL
                player.sendSystemMessage(Component.literal(
                    "§c[Challenge Gym ${gymId}] §fLocked. Beat the regular §eGym ${gymId}§f first."
                ))
                CobblemonBridge.logger.debug(
                    "Blocked {} from challenge gym {} — missing {}",
                    player.gameProfile.name, gymId, mainId,
                )
            }
            return
        }

        // E4 gauntlet (gyms 21-23): handed off to E4GauntletHook, which enforces the
        // win-without-resetting requirement. Gym 20 still goes through the linear prereq below.
        if (gymId in 21..23) {
            if (!E4GauntletHook.canChallenge(player, gymId)) {
                event.isCanceled = true
                event.cancellationResult = InteractionResult.FAIL
                player.sendSystemMessage(Component.literal(E4GauntletHook.lockedReason(gymId)))
                CobblemonBridge.logger.debug(
                    "Blocked {} from gym {} — E4 gauntlet check failed",
                    player.gameProfile.name, gymId,
                )
                return
            }
            // Past the gauntlet check; no further linear prereq needed.
            return
        }

        val prereqGym = prereqGymFor(gymId) ?: return  // no prereq → allow

        val prereqId = ResourceLocation.fromNamespaceAndPath("server", "beat_gym_$prereqGym")
        val mgr = player.server.advancements
        val prereq = mgr.get(prereqId)
        if (prereq == null) {
            CobblemonBridge.logger.warn(
                "Gym {} prereq advancement {} not found — letting interact proceed",
                gymId, prereqId,
            )
            return
        }
        if (player.advancements.getOrStartProgress(prereq).isDone) return

        event.isCanceled = true
        event.cancellationResult = InteractionResult.FAIL
        player.sendSystemMessage(Component.literal(
            "§c[Gym ${gymId}] §fLocked. Beat §eGym ${prereqGym}§f first."
        ))
        CobblemonBridge.logger.debug(
            "Blocked {} from gym {} interact — missing {}",
            player.gameProfile.name, gymId, prereqId,
        )
    }
}
