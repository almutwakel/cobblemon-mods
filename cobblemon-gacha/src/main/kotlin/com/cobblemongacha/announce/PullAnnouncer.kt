package com.cobblemongacha.announce

import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTier
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.core.component.DataComponents

/**
 * Broadcasts a pull to the whole server, plays a tier-appropriate settle sound, and always spawns
 * a tier-coloured firework at the crate (or the player's feet if no crate is bound). The firework
 * scales with the loot tier: small ball for Floor, medium for Mid/High, multi-coloured large with
 * trail+twinkle for Jackpot.
 */
object PullAnnouncer {

    fun broadcast(
        server: MinecraftServer,
        player: ServerPlayer,
        tier: KeyTier,
        entry: LootEntry,
        crateBlockPos: net.minecraft.core.BlockPos? = null,
        labelOverride: String? = null,
    ) {
        val playerName = player.name.string
        // `labelOverride` lets eggs surface the rolled species + HA tag in the announce instead
        // of the generic CSV label ("Shiny Egg" → "Shiny Pikachu Egg §d(Hidden Ability)").
        val label = labelOverride ?: entry.label
        val message = when (entry.lootTier) {
            LootTier.Floor, LootTier.Mid -> Component.literal(
                "§7[Gacha] §a$playerName§7 opened a §f${tier.displayName} Box §7and got §f$label"
            )
            LootTier.High -> Component.literal(
                "§7[Gacha] §a$playerName§7 opened a §f${tier.displayName} Box §7and got §f$label§6 (HIGH)"
            )
            LootTier.Jackpot -> Component.literal(
                "§e[Gacha] §6★ JACKPOT! §a$playerName§6 got §f$label §6from a ${tier.displayName} Box ★"
            )
        }
        server.playerList.broadcastSystemMessage(message, false)

        val sound = if (entry.lootTier == LootTier.Jackpot) SoundEvents.PLAYER_LEVELUP else SoundEvents.NOTE_BLOCK_PLING.value()
        player.serverLevel().playSound(
            null, player.x, player.y, player.z, sound, SoundSource.PLAYERS, 1.0f, 1.0f,
        )

        // Always fire a celebration firework — falls back to the player's feet if no crate is bound.
        val firePos = crateBlockPos
            ?: net.minecraft.core.BlockPos(player.blockX, player.blockY, player.blockZ)
        spawnFirework(player, tier, entry.lootTier, firePos)
    }

    /**
     * Spawns a vanilla `FireworkRocketEntity`. Visuals scale with [lootTier]:
     *   Floor   → small ball, single tier-coloured, no trail/twinkle
     *   Mid     → small ball, tier-coloured, twinkle
     *   High    → large ball, tier-coloured, trail
     *   Jackpot → large ball, tier-coloured + gold fade, trail + twinkle
     */
    private fun spawnFirework(
        player: ServerPlayer,
        tier: KeyTier,
        lootTier: LootTier,
        pos: net.minecraft.core.BlockPos,
    ) {
        val baseColor = when (tier) {
            KeyTier.COMMON -> 0xFFFFFF   // white
            KeyTier.RARE -> 0xCC2222     // red
            KeyTier.ULTRA -> 0x8B00FF    // purple
        }
        val (shape, hasTrail, hasTwinkle, fadeColors) = when (lootTier) {
            LootTier.Floor -> Quad(FireworkExplosion.Shape.SMALL_BALL, false, false, IntList.of())
            LootTier.Mid -> Quad(FireworkExplosion.Shape.SMALL_BALL, false, true, IntList.of())
            LootTier.High -> Quad(FireworkExplosion.Shape.LARGE_BALL, true, false, IntList.of())
            LootTier.Jackpot -> Quad(FireworkExplosion.Shape.LARGE_BALL, true, true, IntList.of(0xFFD700))
        }
        val rocket = ItemStack(Items.FIREWORK_ROCKET)
        val explosion = FireworkExplosion(
            shape, IntList.of(baseColor), fadeColors, hasTrail, hasTwinkle,
        )
        val fireworks = Fireworks(/*flightDuration*/ 1, listOf(explosion))
        rocket.set(DataComponents.FIREWORKS, fireworks)
        val level = player.serverLevel()
        val entity = FireworkRocketEntity(level, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, rocket)
        level.addFreshEntity(entity)
        level.sendParticles(ParticleTypes.FIREWORK, player.x, player.y + 1.0, player.z, 20, 0.4, 0.4, 0.4, 0.0)
    }

    private data class Quad(
        val shape: FireworkExplosion.Shape,
        val hasTrail: Boolean,
        val hasTwinkle: Boolean,
        val fade: IntList,
    )
}
