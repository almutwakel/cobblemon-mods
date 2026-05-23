package com.cobblemonbridge.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.adapters.CobbleloootsAdapter
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click any entity that yields a party-EXP amount → distribute the EXP equally across the
 * player's party Pokémon, despawn the entity, suppress the vanilla interact.
 *
 * The amount comes from one of two sources, in this priority:
 *   1. `cobblemon_bridge.give_party_exp.<N>` entity tag (set via `/tag … add` or stamped at
 *      spawn time by an adapter).
 *   2. Direct lookup for known mod entities — currently just Cobbleloots loot balls, where the
 *      tier is read via reflection at interact time. This catches loot balls that spawned
 *      before the bridge was installed / before the spawn-time tagger ran.
 *
 * Cancelling the `PlayerInteractEvent.EntityInteract` event prevents the entity's vanilla
 * `interact()` from firing. For Cobbleloots loot balls, that's the path that opens the loot
 * UI (showing "This ball has no loot!" against our empty fallback table) and grants vanilla
 * XP — both replaced by the party EXP grant.
 */
object GivePartyExpHook {

    private val SOURCE = SidemodExperienceSource("cobblemon_bridge")
    private val COBBLELOOTS_BALL_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("cobbleloots", "loot_ball")

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val target = event.target
        val amount = resolveAmount(target) ?: return

        val party = Cobblemon.storage.getParty(player)
        val members = party.iterator().asSequence().toList()
        if (members.isEmpty()) {
            player.sendSystemMessage(
                Component.literal("§cYou need at least one Pokémon in your party to claim this."),
            )
            event.isCanceled = true
            return
        }

        val per = amount / members.size
        val remainder = amount - per * members.size
        members.forEachIndexed { idx, pokemon ->
            val share = per + if (idx == 0) remainder else 0
            if (share > 0) pokemon.addExperienceWithPlayer(player, SOURCE, share)
        }

        player.serverLevel().playSound(
            null, player.x, player.y, player.z,
            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f,
        )
        player.sendSystemMessage(
            Component.literal(
                "§a+${amount} EXP §7to your party §8(§f${per}§8 each across §f${members.size}§8 mons)",
            ),
        )

        target.discard()
        event.isCanceled = true

        CobblemonBridge.logger.info(
            "cobblemon-bridge: give_party_exp granted {} EXP to {} ({} mons, {} each)",
            amount, player.gameProfile.name, members.size, per,
        )
    }

    /**
     * Resolve the EXP amount for [target]. First check the bridge tag; if not present, fall
     * back to checking if the entity is a Cobbleloots loot ball and reading its tier on the
     * spot. Returns null if neither matches (don't intercept the event).
     */
    private fun resolveAmount(target: Entity): Int? {
        BridgeTags.findGivePartyExp(target.tags)?.let { return it }
        if (BuiltInRegistries.ENTITY_TYPE.getKey(target.type) == COBBLELOOTS_BALL_ID) {
            return CobbleloootsAdapter.expFor(target)
        }
        return null
    }
}
