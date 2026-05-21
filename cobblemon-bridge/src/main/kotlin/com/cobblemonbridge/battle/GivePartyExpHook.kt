package com.cobblemonbridge.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click any entity tagged `cobblemon_bridge.give_party_exp.<N>` → distribute N Cobblemon EXP
 * equally across the player's party Pokémon, despawn the entity, suppress the vanilla interact.
 *
 * "Equally across the party" means: pull the player's `PartyStore`, iterate over the populated
 * slots, give `N / slotCount` to each (any remainder lands on the first slot). Empty party slots
 * are skipped. Players with an empty party get nothing and the entity stays (caller can re-tag
 * to retry later, or just lose the grant — we don't reserve).
 *
 * Cancelling the `PlayerInteractEvent.EntityInteract` event prevents the entity's vanilla
 * `interact()` from firing. For Cobbleloots loot balls, that's the path that grants the
 * built-in (vanilla Minecraft) XP and opens the loot UI — both of which we want suppressed
 * since we're replacing the reward semantics entirely.
 */
object GivePartyExpHook {

    private val SOURCE = SidemodExperienceSource("cobblemon_bridge")

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val target = event.target
        val amount = BridgeTags.findGivePartyExp(target.tags) ?: return

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
                "§a+${amount} EXP §7split across §f${members.size}§7 Pokémon (§f${per}§7 each)",
            ),
        )

        target.discard()
        event.isCanceled = true

        CobblemonBridge.logger.info(
            "cobblemon-bridge: give_party_exp granted {} EXP to {} ({} mons, {} each)",
            amount, player.gameProfile.name, members.size, per,
        )
    }
}
