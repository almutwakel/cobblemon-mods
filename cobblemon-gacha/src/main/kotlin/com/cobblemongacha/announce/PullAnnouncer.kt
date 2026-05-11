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
 * Broadcasts a pull to the whole server, plays the appropriate sound at the puller, and spawns
 * a tier-coloured firework at the crate on jackpot pulls.
 */
object PullAnnouncer {

    fun broadcast(
        server: MinecraftServer,
        player: ServerPlayer,
        tier: KeyTier,
        entry: LootEntry,
        crateBlockPos: net.minecraft.core.BlockPos? = null,
    ) {
        val playerName = player.name.string
        val message = when (entry.lootTier) {
            LootTier.Floor, LootTier.Mid -> Component.literal(
                "§7[Gacha] §a$playerName§7 opened a §f${tier.displayName} Box §7and got §f${entry.label}"
            )
            LootTier.High -> Component.literal(
                "§7[Gacha] §a$playerName§7 opened a §f${tier.displayName} Box §7and got §f${entry.label}§6 (HIGH)"
            )
            LootTier.Jackpot -> Component.literal(
                "§e[Gacha] §6★ JACKPOT! §a$playerName§6 got §f${entry.label} §6from a ${tier.displayName} Box ★"
            )
        }
        server.playerList.broadcastSystemMessage(message, false)

        val sound = if (entry.lootTier == LootTier.Jackpot) SoundEvents.PLAYER_LEVELUP else SoundEvents.NOTE_BLOCK_PLING.value()
        player.serverLevel().playSound(
            null, player.x, player.y, player.z, sound, SoundSource.PLAYERS, 1.0f, 1.0f,
        )

        if (entry.lootTier == LootTier.Jackpot && crateBlockPos != null) {
            spawnFirework(server, player, tier, crateBlockPos)
        }
    }

    private fun spawnFirework(
        server: MinecraftServer,
        player: ServerPlayer,
        tier: KeyTier,
        pos: net.minecraft.core.BlockPos,
    ) {
        val color = when (tier) {
            KeyTier.COMMON -> 0xFFFFFF
            KeyTier.RARE -> 0xCC2222
            KeyTier.ULTRA -> 0x8B00FF
        }
        val rocket = ItemStack(Items.FIREWORK_ROCKET)
        // FireworkExplosion in MC 1.21.1 is a record with 5 fields:
        // (Shape, IntList colors, IntList fadeColors, boolean hasTrail, boolean hasTwinkle)
        val explosion = FireworkExplosion(
            FireworkExplosion.Shape.LARGE_BALL,
            IntList.of(color),
            IntList.of(),
            /*hasTrail*/ true,
            /*hasTwinkle*/ true,
        )
        val fireworks = Fireworks(/*flightDuration*/ 1, listOf(explosion))
        rocket.set(DataComponents.FIREWORKS, fireworks)
        val level = player.serverLevel()
        val entity = FireworkRocketEntity(level, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, rocket)
        level.addFreshEntity(entity)
        level.sendParticles(ParticleTypes.FIREWORK, player.x, player.y + 1.0, player.z, 20, 0.4, 0.4, 0.4, 0.0)
    }
}
