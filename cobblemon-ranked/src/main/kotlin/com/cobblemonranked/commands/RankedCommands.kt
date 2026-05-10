package com.cobblemonranked.commands

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.decay.DecayManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths

object RankedCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ranked")
                .then(Commands.literal("challenge")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { ctx ->
                            val source = ctx.source.playerOrException
                            val target = EntityArgument.getPlayer(ctx, "player")
                            handleChallenge(source, target)
                            1
                        }
                    )
                )
                .then(Commands.literal("accept")
                    .executes { ctx ->
                        handleAccept(ctx.source.playerOrException)
                        1
                    }
                )
                .then(Commands.literal("decline")
                    .executes { ctx ->
                        handleDecline(ctx.source.playerOrException)
                        1
                    }
                )
                .then(Commands.literal("stats")
                    .executes { ctx ->
                        showStats(ctx.source.playerOrException, ctx.source.playerOrException)
                        1
                    }
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { ctx ->
                            showStats(ctx.source.playerOrException, EntityArgument.getPlayer(ctx, "player"))
                            1
                        }
                    )
                )
                .then(Commands.literal("leaderboard")
                    .executes { ctx ->
                        showLeaderboard(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("setelo")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes { ctx ->
                                    val target = EntityArgument.getPlayer(ctx, "player")
                                    val value = IntegerArgumentType.getInteger(ctx, "value")
                                    adminSetElo(ctx.source, target, value)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("decay")
                        .executes { ctx ->
                            DecayManager.forceDecay(ctx.source.server)
                            ctx.source.sendSystemMessage(Component.literal("[Ranked] Decay manually triggered."))
                            1
                        }
                    )
                    .then(Commands.literal("force")
                        .then(Commands.argument("player1", EntityArgument.player())
                            .then(Commands.argument("player2", EntityArgument.player())
                                .executes { ctx ->
                                    val p1 = EntityArgument.getPlayer(ctx, "player1")
                                    val p2 = EntityArgument.getPlayer(ctx, "player2")
                                    adminForce(ctx.source, p1, p2)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx ->
                            CobblemonRanked.config = RankedConfig.load(FMLPaths.CONFIGDIR.get())
                            val arena = if (CobblemonRanked.config.isArenaConfigured()) "configured" else "disabled"
                            ctx.source.sendSystemMessage(Component.literal("[Ranked] Config reloaded. Arena: $arena."))
                            1
                        }
                    )
                )
        )
    }

    private fun handleChallenge(challenger: ServerPlayer, target: ServerPlayer) {
        val challengeManager = CobblemonRanked.challengeManager
        val error = challengeManager.challenge(challenger, target)
        if (error != null) {
            challenger.sendSystemMessage(Component.literal("[Ranked] $error"))
            return
        }

        val forced = challengeManager.getPendingForced(target.uuid)
        if (forced != null) {
            RankedBattleManager.startTeamSelection(challenger, target)
        }
    }

    private fun handleAccept(player: ServerPlayer) {
        val challenge = CobblemonRanked.challengeManager.accept(player)
        if (challenge == null) {
            player.sendSystemMessage(Component.literal("[Ranked] No pending challenge to accept."))
            return
        }
        val challenger = player.server.playerList.getPlayer(challenge.challengerUuid)
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("[Ranked] Challenger is no longer online."))
            return
        }
        RankedBattleManager.startTeamSelection(challenger, player)
    }

    private fun handleDecline(player: ServerPlayer) {
        if (!CobblemonRanked.challengeManager.decline(player)) {
            player.sendSystemMessage(Component.literal("[Ranked] No pending challenge to decline."))
        } else {
            player.sendSystemMessage(Component.literal("[Ranked] Challenge declined."))
        }
    }

    private fun showStats(viewer: ServerPlayer, target: ServerPlayer) {
        val data = CobblemonRanked.eloStore.getOrCreate(target.uuid, target.name.string)
        viewer.sendSystemMessage(Component.literal(
            "[Ranked] ${target.name.string}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"}"
        ))
    }

    private fun showLeaderboard(source: CommandSourceStack) {
        val config = CobblemonRanked.config
        val leaderboard = CobblemonRanked.eloStore.getLeaderboard()
        source.sendSystemMessage(Component.literal("[Ranked] === ELO Leaderboard ==="))
        if (leaderboard.isEmpty()) {
            source.sendSystemMessage(Component.literal("  No players ranked yet."))
            return
        }
        val topN = leaderboard.take(config.leaderboardSize)
        topN.forEachIndexed { i, (_, data) ->
            source.sendSystemMessage(Component.literal(
                "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)"
            ))
        }

        // Show caller's rank if not in top N
        val player = source.player ?: return
        val playerUuid = player.uuid.toString()
        val playerIndex = leaderboard.indexOfFirst { it.first == playerUuid }
        if (playerIndex >= config.leaderboardSize) {
            val (_, playerData) = leaderboard[playerIndex]
            source.sendSystemMessage(Component.literal("  ---"))
            source.sendSystemMessage(Component.literal(
                "  ${playerIndex + 1}. ${playerData.name}: ${playerData.elo} (${playerData.wins}W/${playerData.losses}L)"
            ))
        }
    }

    private fun adminSetElo(source: CommandSourceStack, target: ServerPlayer, value: Int) {
        CobblemonRanked.eloStore.setElo(target.uuid, value)
        source.sendSystemMessage(Component.literal(
            "[Ranked] Set ${target.name.string}'s ELO to ${value.coerceAtLeast(CobblemonRanked.config.minimumElo)}"
        ))
    }

    private fun adminForce(source: CommandSourceStack, p1: ServerPlayer, p2: ServerPlayer) {
        if (p1.uuid == p2.uuid) {
            source.sendSystemMessage(Component.literal("[Ranked] Can't force a player to fight themselves."))
            return
        }
        source.sendSystemMessage(Component.literal(
            "[Ranked] Forcing match: ${p1.name.string} vs ${p2.name.string}"
        ))
        RankedBattleManager.startTeamSelection(p1, p2)
    }
}
