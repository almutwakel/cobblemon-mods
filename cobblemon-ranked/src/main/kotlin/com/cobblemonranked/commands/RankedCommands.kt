package com.cobblemonranked.commands

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.decay.DecayManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
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
                .executes { ctx ->
                    showHelp(ctx.source, ctx.source.hasPermission(4))
                    1
                }
                .then(Commands.literal("help")
                    .executes { ctx ->
                        showHelp(ctx.source, ctx.source.hasPermission(4))
                        1
                    }
                )
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
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes { ctx ->
                            // Accept either an online player name or any name with an EloStore entry,
                            // so the console can inspect offline players' stats too.
                            showStatsByName(ctx.source, StringArgumentType.getString(ctx, "player"))
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
                    .then(Commands.literal("simulate")
                        .then(Commands.argument("name1", StringArgumentType.string())
                            .then(Commands.argument("name2", StringArgumentType.string())
                                .executes { ctx ->
                                    val n1 = StringArgumentType.getString(ctx, "name1")
                                    val n2 = StringArgumentType.getString(ctx, "name2")
                                    if (n1.equals(n2, ignoreCase = true)) {
                                        ctx.source.sendSystemMessage(Component.literal(
                                            "§c[Ranked] Both names are the same — pick two different players."))
                                        return@executes 0
                                    }
                                    adminSimulate(ctx.source, n1, n2)
                                    1
                                }
                            )
                        )
                    )
                )
        )
    }

    private fun showHelp(source: CommandSourceStack, includeAdmin: Boolean) {
        val lines = mutableListOf(
            "§e[Ranked] §fCommands:",
            "§7  /ranked challenge <player> §f— challenge a player to a ranked match",
            "§7  /ranked accept §f— accept a pending challenge",
            "§7  /ranked decline §f— decline a pending challenge",
            "§7  /ranked stats [player] §f— view ELO, wins, losses",
            "§7  /ranked leaderboard §f— top players by ELO",
        )
        if (includeAdmin) {
            lines += listOf(
                "§e[Ranked] §fAdmin (op level 4):",
                "§7  /ranked admin setelo <player> <value> §f— override a player's ELO",
                "§7  /ranked admin decay §f— manually trigger daily decay",
                "§7  /ranked admin force <player1> <player2> §f— force a match (bypasses daily limit)",
                "§7  /ranked admin reload §f— reload config.json from disk",
                "§7  /ranked admin simulate <name1> <name2> §f— simulate a match (winner picked by ELO odds; offline-friendly)",
            )
        }
        lines.forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    /**
     * Drives RankedBattleManager.simulateMatch and prints a transparent breakdown to the
     * source: pre-match ELOs, win probability, the random roll, and the applied delta.
     */
    private fun adminSimulate(source: CommandSourceStack, name1: String, name2: String) {
        val outcome = RankedBattleManager.simulateMatch(source.server, name1, name2)
        val pct1 = (outcome.expected1 * 100).toInt()
        val pct2 = 100 - pct1
        val winner = if (outcome.player1Wins) name1 else name2
        source.sendSystemMessage(Component.literal(
            "§e[Ranked] §fSimulating §a$name1 §7(${outcome.elo1})§f vs §a$name2 §7(${outcome.elo2})"))
        source.sendSystemMessage(Component.literal(
            "§7  Win probability: §f$name1 $pct1%§7 / §f$name2 $pct2%"))
        source.sendSystemMessage(Component.literal(
            "§7  Roll: §f${"%.3f".format(outcome.roll)}§7 → §a$winner wins"))
        // applyMatchResult already broadcasts the ELO update lines, so no need to repeat here.
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

    /**
     * Stats lookup that works from any source (player or console). Resolves the target by:
     *   1) An online player matching the name (case-insensitive), preferring their server UUID.
     *   2) An existing EloStore entry whose stored name matches (case-insensitive).
     *   3) Returns "no stats" if no record found and the target isn't online.
     */
    private fun showStatsByName(source: CommandSourceStack, name: String) {
        val online = source.server.playerList.players.firstOrNull { it.name.string.equals(name, ignoreCase = true) }
        if (online != null) {
            val data = CobblemonRanked.eloStore.getOrCreate(online.uuid, online.name.string)
            source.sendSystemMessage(Component.literal(
                "[Ranked] ${online.name.string}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"}"
            ))
            return
        }
        val existing = CobblemonRanked.eloStore.getAll().entries.firstOrNull {
            it.value.name.equals(name, ignoreCase = true)
        }
        if (existing != null) {
            val data = existing.value
            source.sendSystemMessage(Component.literal(
                "[Ranked] ${data.name}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"} (offline)"
            ))
            return
        }
        source.sendSystemMessage(Component.literal("§c[Ranked] No record for '$name' (player has not battled and is not online)"))
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
        CobblemonRanked.eloStore.setElo(target.uuid, target.name.string, value)
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
