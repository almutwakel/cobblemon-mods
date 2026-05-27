package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.QuestAdvancements
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * `/wild` — teleport to a random surface location in the overworld (wilderness).
 *
 * Picks a random X/Z within [radius] blocks of ([centerX], [centerZ]), finds the
 * surface height, and teleports the player there. Retries up to 5 times to avoid
 * landing in water/lava. Per-player cooldown resets on server restart.
 *
 * Admin variant: `/wild <player>` (permission level 2+) can target any online
 * player. Same per-player cooldown applies. Useful for command blocks / portals.
 */
object WildCommand {

    /** Center X of the random teleport area. */
    var centerX: Int = 350
    /** Center Z of the random teleport area. */
    var centerZ: Int = -700
    /** Half-width of the teleport area (500×500 → radius 250). */
    var radius: Int = 250
    /** Cooldown between uses in seconds. */
    var cooldownSeconds: Int = 60

    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("wild")
                .executes { ctx ->
                    val player = ctx.source.player ?: return@executes 0
                    handleWild(player, enforceCooldown = true)
                    1
                }
                .then(Commands.argument("player", EntityArgument.player())
                    .requires { it.hasPermission(2) }
                    .executes { ctx ->
                        val target = EntityArgument.getPlayer(ctx, "player")
                        handleWild(target, enforceCooldown = true)
                        ctx.source.sendSystemMessage(
                            Component.literal("§a[Wild] §fSent ${target.gameProfile.name} to the wilderness.")
                        )
                        1
                    }
                )
        )
    }

    private fun handleWild(player: ServerPlayer, enforceCooldown: Boolean) {
        // ── Cooldown check (skipped for admin usage) ────────────────────────
        if (enforceCooldown) {
            val now = System.currentTimeMillis()
            val lastUsed = cooldowns[player.uuid]
            if (lastUsed != null) {
                val remaining = cooldownSeconds - ((now - lastUsed) / 1000).toInt()
                if (remaining > 0) {
                    player.sendSystemMessage(Component.literal("§c[Wild] Cooldown: ${remaining}s remaining."))
                    return
                }
            }
        }

        val overworld = player.server.getLevel(Level.OVERWORLD) ?: run {
            player.sendSystemMessage(Component.literal("§c[Wild] Wilderness world not available."))
            return
        }

        // ── Find a safe landing spot (up to 5 attempts) ────────────────────
        // Force-load the chunk before reading the heightmap so ungenerated chunks
        // don't return Y=0 (which buries the player underground).
        var bestX = 0; var bestZ = 0; var bestY = 0; var safe = false
        for (attempt in 1..5) {
            val x = centerX + Random.nextInt(-radius, radius + 1)
            val z = centerZ + Random.nextInt(-radius, radius + 1)
            overworld.getChunk(x shr 4, z shr 4)
            val y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            bestX = x; bestZ = z; bestY = y

            @Suppress("DEPRECATION")
            val below = overworld.getBlockState(BlockPos(x, y - 1, z))
            if (!below.liquid()) {
                safe = true
                break
            }
        }

        // ── Teleport ────────────────────────────────────────────────────────
        player.teleportTo(overworld, bestX + 0.5, bestY.toDouble(), bestZ + 0.5, player.yRot, player.xRot)
        cooldowns[player.uuid] = System.currentTimeMillis()

        val warn = if (!safe) " §7(landed in water)" else ""
        player.sendSystemMessage(
            Component.literal("§a[Wild] §fTeleported to the wilderness! §7($bestX, $bestY, $bestZ)$warn")
        )
        QuestAdvancements.award(player, "server:use_wild")
        CobblemonBridge.logger.info("/wild: {} → ({}, {}, {})", player.gameProfile.name, bestX, bestY, bestZ)
    }
}
