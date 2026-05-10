package com.cobblemonranked.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.decay.DecayManager
import com.cobblemonranked.elo.EloCalculator
import com.cobblemonranked.gui.TeamSelectionGui
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActiveRankedMatch(
    val player1Uuid: UUID,
    val player2Uuid: UUID,
    val battleId: UUID? = null
)

object RankedBattleManager {
    private val rankedBattles: ConcurrentHashMap<UUID, ActiveRankedMatch> = ConcurrentHashMap()
    private val pendingTeams: ConcurrentHashMap<UUID, List<Pokemon>> = ConcurrentHashMap()
    private val pendingMatches: ConcurrentHashMap<UUID, UUID> = ConcurrentHashMap()

    fun startTeamSelection(player1: ServerPlayer, player2: ServerPlayer) {
        val config = CobblemonRanked.config
        pendingMatches[player1.uuid] = player2.uuid
        pendingMatches[player2.uuid] = player1.uuid

        openSelectionGui(player1, config.maxLegendaries)
        openSelectionGui(player2, config.maxLegendaries)
    }

    private fun openSelectionGui(player: ServerPlayer, maxLegendaries: Int) {
        TeamSelectionGui(
            player = player,
            maxLegendaries = maxLegendaries,
            onConfirm = { team ->
                pendingTeams[player.uuid] = team.map { it.clone() }
                player.sendSystemMessage(Component.literal("[Ranked] Team locked in! Waiting for opponent..."))
                checkBothReady(player)
            },
            onCancel = {
                cancelMatch(player)
            }
        ).open()
    }

    private fun checkBothReady(player: ServerPlayer) {
        val opponentUuid = pendingMatches[player.uuid] ?: return
        val myTeam = pendingTeams[player.uuid] ?: return
        val opponentTeam = pendingTeams[opponentUuid] ?: return

        val server = player.server
        val opponent = server.playerList.getPlayer(opponentUuid) ?: run {
            player.sendSystemMessage(Component.literal("[Ranked] Opponent disconnected. Match cancelled."))
            cleanup(player.uuid, opponentUuid)
            return
        }

        startBattle(player, myTeam, opponent, opponentTeam)
    }

    private fun startBattle(
        player1: ServerPlayer, team1: List<Pokemon>,
        player2: ServerPlayer, team2: List<Pokemon>
    ) {
        val config = CobblemonRanked.config

        // Legality check
        val p1Legendaries = team1.count { it.isLegendary() }
        val p2Legendaries = team2.count { it.isLegendary() }

        if (p1Legendaries > config.maxLegendaries && p2Legendaries > config.maxLegendaries) {
            broadcast(player1.server,
                "[Ranked] Both players had illegal teams (too many legendaries). Match voided.")
            cleanup(player1.uuid, player2.uuid)
            return
        }
        if (p1Legendaries > config.maxLegendaries) {
            player1.sendSystemMessage(Component.literal(
                "[Ranked] Your team has $p1Legendaries legendaries (max ${config.maxLegendaries}). You auto-lose."))
            resolveMatch(player2, player1)
            cleanup(player1.uuid, player2.uuid)
            return
        }
        if (p2Legendaries > config.maxLegendaries) {
            player2.sendSystemMessage(Component.literal(
                "[Ranked] Your team has $p2Legendaries legendaries (max ${config.maxLegendaries}). You auto-lose."))
            resolveMatch(player1, player2)
            cleanup(player1.uuid, player2.uuid)
            return
        }

        // Save teams for showcase
        CobblemonRanked.teamStore.saveTeam(player1.uuid, team1)
        CobblemonRanked.teamStore.saveTeam(player2.uuid, team2)

        // Build temporary party stores with selected teams
        val tempParty1 = buildTempParty(player1.uuid, team1)
        val tempParty2 = buildTempParty(player2.uuid, team2)
        val teamMap = mapOf(player1.uuid to tempParty1, player2.uuid to tempParty2)

        val format = BattleFormat.GEN_9_SINGLES.copy(adjustLevel = config.levelCap)

        val result = BattleBuilder.pvp1v1(
            player1 = player1,
            player2 = player2,
            battleFormat = format,
            healFirst = true,
            cloneParties = true,
            partyAccessor = { teamMap[it.uuid] ?: Cobblemon.storage.getParty(it) }
        )

        result.ifSuccessful { battle ->
            val match = ActiveRankedMatch(player1.uuid, player2.uuid, battle.battleId)
            rankedBattles[battle.battleId] = match
            broadcast(player1.server,
                "[Ranked] Battle started: ${player1.name.string} vs ${player2.name.string}!")
        }

        result.ifErrored {
            player1.sendSystemMessage(Component.literal("[Ranked] Failed to start battle."))
            player2.sendSystemMessage(Component.literal("[Ranked] Failed to start battle."))
        }

        cleanup(player1.uuid, player2.uuid)
    }

    private fun buildTempParty(ownerUuid: UUID, team: List<Pokemon>): PlayerPartyStore {
        val store = PlayerPartyStore(ownerUuid)
        team.forEach { pokemon ->
            val clone = pokemon.clone()
            clone.heal()
            store.add(clone)
        }
        return store
    }

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            val match = rankedBattles.remove(event.battle.battleId) ?: return@subscribe
            val winners = event.winners.filterIsInstance<PlayerBattleActor>()
            val losers = event.losers.filterIsInstance<PlayerBattleActor>()

            if (winners.isNotEmpty() && losers.isNotEmpty()) {
                val winnerPlayer = winners.first().entity
                val loserPlayer = losers.first().entity
                if (winnerPlayer != null && loserPlayer != null) {
                    resolveMatch(winnerPlayer, loserPlayer)
                }
            }
        }

        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            val match = rankedBattles.remove(event.battle.battleId) ?: return@subscribe
            // Determine who fled — the player who is no longer in the battle
            val actors = event.battle.actors.filterIsInstance<PlayerBattleActor>()
            val server = actors.firstOrNull()?.entity?.server ?: return@subscribe

            val p1 = server.playerList.getPlayer(match.player1Uuid)
            val p2 = server.playerList.getPlayer(match.player2Uuid)

            // The player who fled loses
            if (p1 != null && p2 != null) {
                // Check who is still in battle vs who fled
                val p1InBattle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p1) != null
                val p2InBattle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p2) != null

                when {
                    !p1InBattle && p2InBattle -> resolveMatch(p2, p1)
                    !p2InBattle && p1InBattle -> resolveMatch(p1, p2)
                    else -> {
                        // Both left — void the match
                        broadcast(server, "[Ranked] Both players fled. Match voided.")
                    }
                }
            }
        }
    }

    private fun resolveMatch(winner: ServerPlayer, loser: ServerPlayer) {
        val store = CobblemonRanked.eloStore
        val config = CobblemonRanked.config
        val winnerData = store.getOrCreate(winner.uuid, winner.name.string)
        val loserData = store.getOrCreate(loser.uuid, loser.name.string)

        val oldWinnerElo = winnerData.elo
        val oldLoserElo = loserData.elo

        val (newWinnerElo, newLoserElo) = EloCalculator.calculate(
            winnerElo = oldWinnerElo,
            loserElo = oldLoserElo,
            kFactor = config.kFactor,
            minimumElo = config.minimumElo
        )

        winnerData.elo = newWinnerElo
        winnerData.wins++
        winnerData.lastBattleDate = LocalDate.now().toString()

        loserData.elo = newLoserElo
        loserData.losses++
        loserData.lastBattleDate = LocalDate.now().toString()

        store.save()
        DecayManager.recordBattle()

        val winnerDelta = newWinnerElo - oldWinnerElo
        val loserDelta = newLoserElo - oldLoserElo
        broadcast(winner.server,
            "[Ranked] ${winner.name.string} defeated ${loser.name.string}!")
        broadcast(winner.server,
            "[Ranked] ${winner.name.string}: $oldWinnerElo -> $newWinnerElo (+$winnerDelta) | " +
            "${loser.name.string}: $oldLoserElo -> $newLoserElo ($loserDelta)")

        val leaderboard = store.getLeaderboard()
        val topN = leaderboard.take(config.leaderboardSize)
        broadcast(winner.server, "[Ranked] Leaderboard:")
        topN.forEachIndexed { i, (_, data) ->
            broadcast(winner.server, "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)")
        }
    }

    private fun cancelMatch(player: ServerPlayer) {
        val opponentUuid = pendingMatches[player.uuid] ?: return
        val opponent = player.server.playerList.getPlayer(opponentUuid)
        opponent?.sendSystemMessage(Component.literal("[Ranked] ${player.name.string} cancelled the match."))
        player.sendSystemMessage(Component.literal("[Ranked] Match cancelled."))
        cleanup(player.uuid, opponentUuid)
    }

    private fun cleanup(uuid1: UUID, uuid2: UUID) {
        pendingTeams.remove(uuid1)
        pendingTeams.remove(uuid2)
        pendingMatches.remove(uuid1)
        pendingMatches.remove(uuid2)
    }

    private fun broadcast(server: MinecraftServer, message: String) {
        server.playerList.players.forEach {
            it.sendSystemMessage(Component.literal(message))
        }
    }
}
