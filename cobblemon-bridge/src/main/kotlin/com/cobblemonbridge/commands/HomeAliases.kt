package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.QuestAdvancements
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Adds no-arg variants of `/sethome` and `/home` so players can use a single default home
 * without typing a name. NeoEssentials's `/sethome <name>` and `/home <name>` paths still work
 * — Brigadier merges literal branches, and the parser picks the `.executes()` path when no
 * argument is provided.
 *
 * The no-arg variants alias to a fixed name ("home"), then call into NeoEssentials's existing
 * command via `performPrefixedCommand` so we don't have to replicate home storage / location
 * resolution / cooldown logic.
 */
object HomeAliases {

    private const val DEFAULT_HOME = "home"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("sethome")
                .executes { ctx ->
                    val player = ctx.source.player ?: return@executes 0
                    val src = ctx.source.withPermission(4).withSuppressedOutput()
                    player.server.commands.performPrefixedCommand(src, "sethome $DEFAULT_HOME")
                    QuestAdvancements.award(player, "server:set_home", criterion = "done")
                    player.sendSystemMessage(Component.literal("§a✓ Home set. §7Return any time with §f/home"))
                    CobblemonBridge.logger.info(
                        "cobblemon-bridge: /sethome alias → 'sethome {}' for {}",
                        DEFAULT_HOME, player.gameProfile.name,
                    )
                    1
                }
        )
        dispatcher.register(
            Commands.literal("home")
                .executes { ctx ->
                    val player = ctx.source.player ?: return@executes 0
                    val src = ctx.source.withPermission(4).withSuppressedOutput()
                    player.server.commands.performPrefixedCommand(src, "home $DEFAULT_HOME")
                    1
                }
        )
    }
}
