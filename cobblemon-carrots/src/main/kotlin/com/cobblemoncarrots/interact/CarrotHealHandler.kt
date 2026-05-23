package com.cobblemoncarrots.interact

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemoncarrots.CobblemonCarrots
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click + carrot interactions:
 *
 *   - Plain right-click on a living, non-full-HP Pokémon → heal [hpPerCarrot] HP, consume 1 carrot.
 *   - Plain right-click on a fainted Pokémon → hint to shift-right-click (and bail).
 *   - Plain right-click on a full-HP Pokémon → pass through (no carrot consumed).
 *   - SHIFT-right-click on a fainted Pokémon → revive to [hpPerCarrot] HP, consume
 *     [inventoryReviveCarrotCost] carrots. One revive per click — no batching.
 *   - SHIFT-right-click on a living Pokémon → pass through (no behaviour, lets vanilla handle).
 */
object CarrotHealHandler {

    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        val player = event.entity as? ServerPlayer ?: return
        val target = event.target as? PokemonEntity ?: return
        val stack = player.mainHandItem
        if (stack.item != Items.CARROT) return

        val pokemon = target.pokemon
        val cfg = CobblemonCarrots.config

        // ── Shift-right-click revive branch (gated by config) ──────────────
        if (player.isShiftKeyDown && cfg.allowInventoryRevive) {
            if (!pokemon.isFainted()) return  // shift on living = pass-through
            val cost = cfg.inventoryReviveCarrotCost
            val carrotsHeld = countCarrots(player)
            if (carrotsHeld < cost) {
                player.sendSystemMessage(Component.literal(
                    "§c[Carrots] Need §f$cost carrots§c to revive (you have §f$carrotsHeld§c)."
                ))
                event.cancellationResult = InteractionResult.SUCCESS
                event.isCanceled = true
                return
            }
            pokemon.currentHealth = minOf(cfg.hpPerCarrot, pokemon.maxHealth)
            if (!player.abilities.instabuild) consumeCarrots(player, cost)
            player.serverLevel().playSound(
                null, target.x, target.y, target.z,
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f,
            )
            player.serverLevel().sendParticles(
                ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 1.0, target.z,
                8, 0.4, 0.4, 0.4, 0.0,
            )
            player.sendSystemMessage(Component.literal(
                "§a${pokemon.species.translatedName.string}§7 revived! §8(${pokemon.currentHealth}/${pokemon.maxHealth} HP, §c-$cost carrots§8)"
            ))
            event.cancellationResult = InteractionResult.SUCCESS
            event.isCanceled = true
            return
        }

        // ── Plain right-click heal branch ──────────────────────────────────
        if (pokemon.isFainted()) {
            player.sendSystemMessage(Component.literal(
                "§7${pokemon.species.translatedName.string} has fainted — use a §fPoké Healer §7to revive."
            ))
            event.cancellationResult = InteractionResult.SUCCESS
            event.isCanceled = true
            return
        }
        if (pokemon.isFullHealth()) {
            event.cancellationResult = InteractionResult.PASS
            return
        }
        val newHp = minOf(pokemon.currentHealth + cfg.hpPerCarrot, pokemon.maxHealth)
        val gained = newHp - pokemon.currentHealth
        pokemon.currentHealth = newHp
        if (!player.abilities.instabuild) stack.shrink(1)
        player.serverLevel().playSound(
            null, target.x, target.y, target.z,
            SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.2f,
        )
        player.serverLevel().sendParticles(
            ParticleTypes.HEART, target.x, target.y + 1.0, target.z,
            3, 0.3, 0.3, 0.3, 0.0,
        )
        player.sendSystemMessage(Component.literal(
            "§a${pokemon.species.translatedName.string} §7+$gained HP §8(${pokemon.currentHealth}/${pokemon.maxHealth})"
        ))
        event.cancellationResult = InteractionResult.SUCCESS
        event.isCanceled = true
    }

    private fun countCarrots(player: ServerPlayer): Int =
        player.inventory.items.filter { it.item == Items.CARROT }.sumOf { it.count }

    private fun consumeCarrots(player: ServerPlayer, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val s = player.inventory.getItem(i)
            if (s.item != Items.CARROT) continue
            val take = minOf(s.count, remaining)
            s.shrink(take)
            remaining -= take
        }
    }
}
