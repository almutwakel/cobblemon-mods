package com.cobblemonshowcase.commands

import com.cobblemonshowcase.CobblemonShowcase
import com.cobblemonshowcase.gui.ShowcaseGui
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object ShowcaseCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("showcase")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes { ctx ->
                        val viewer = ctx.source.playerOrException
                        val target = EntityArgument.getPlayer(ctx, "player")
                        ShowcaseGui(viewer, target.uuid, target.name.string, viewer.server).open()
                        1
                    }
                )
        )

        dispatcher.register(
            Commands.literal("team")
                .then(Commands.literal("set")
                    .requires { it.hasPermission(4) }
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .suggests { _, builder ->
                                CobblemonShowcase.config.teams.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes { ctx ->
                                val target = EntityArgument.getPlayer(ctx, "player")
                                val teamName = StringArgumentType.getString(ctx, "name")
                                adminSetTeam(ctx.source, target, teamName)
                                1
                            }
                        )
                    )
                )
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .suggests { _, builder ->
                        CobblemonShowcase.config.teams.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val player = ctx.source.playerOrException
                        val teamName = StringArgumentType.getString(ctx, "name")
                        joinTeam(player, teamName)
                        1
                    }
                )
        )
    }

    private fun joinTeam(player: ServerPlayer, teamName: String) {
        val config = CobblemonShowcase.config
        val store = CobblemonShowcase.playerDataStore

        if (teamName !in config.teams) {
            player.sendSystemMessage(Component.literal(
                "[Showcase] Invalid team. Options: ${config.teams.joinToString(", ")}"))
            return
        }

        if (!store.canSwitchTeam(player.uuid)) {
            val timeLeft = store.getTimeUntilSwitch(player.uuid)
            player.sendSystemMessage(Component.literal(
                "[Showcase] You can switch teams in $timeLeft."))
            return
        }

        store.setTeam(player.uuid, teamName)
        player.sendSystemMessage(Component.literal("[Showcase] You joined Team $teamName!"))
    }

    private fun adminSetTeam(source: CommandSourceStack, target: ServerPlayer, teamName: String) {
        val config = CobblemonShowcase.config

        if (teamName !in config.teams) {
            source.sendSystemMessage(Component.literal(
                "[Showcase] Invalid team. Options: ${config.teams.joinToString(", ")}"))
            return
        }

        CobblemonShowcase.playerDataStore.setTeam(target.uuid, teamName)
        source.sendSystemMessage(Component.literal(
            "[Showcase] Set ${target.name.string}'s team to $teamName."))
    }
}
