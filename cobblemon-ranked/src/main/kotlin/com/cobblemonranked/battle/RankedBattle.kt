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
import com.cobblemonranked.config.ArenaPos
import com.cobblemonranked.decay.DecayManager
import com.cobblemonranked.elo.EloCalculator
import com.cobblemonranked.gui.TeamSelectionGui
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of a player's location captured before arena teleport, so we can put them back.
 */
data class OriginalLocation(
    val worldId: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class ActiveRankedMatch(
    val player1Uuid: UUID,
    val player2Uuid: UUID,
    val battleId: UUID? = null,
    /** Original locations keyed by player UUID; empty when arena teleport is disabled. */
    val originalLocations: Map<UUID, OriginalLocation> = emptyMap(),
)

/** Result of [RankedBattleManager.applyMatchResult]. */
data class MatchOutcome(
    val winnerName: String, val loserName: String,
    val oldWinnerElo: Int, val newWinnerElo: Int,
    val oldLoserElo: Int, val newLoserElo: Int,
)

/** Result of [RankedBattleManager.simulateMatch]; carries the dice roll for transparency. */
data class SimulateOutcome(
    val name1: String, val name2: String,
    val elo1: Int, val elo2: Int,
    /** Probability that player1 wins given the ELO gap (in 0.0..1.0). */
    val expected1: Double,
    /** The random number drawn (in 0.0..1.0). player1 wins when roll < expected1. */
    val roll: Double,
    val player1Wins: Boolean,
    val applied: MatchOutcome,
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

        // Capture original locations and teleport to arena if configured.
        val originals: Map<UUID, OriginalLocation> = if (config.isArenaConfigured()) {
            val map = mapOf(
                player1.uuid to captureLocation(player1),
                player2.uuid to captureLocation(player2),
            )
            teleport(player1, config.arenaPos1!!)
            teleport(player2, config.arenaPos2!!)
            map
        } else {
            emptyMap()
        }

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
            val match = ActiveRankedMatch(player1.uuid, player2.uuid, battle.battleId, originals)
            rankedBattles[battle.battleId] = match
            broadcast(player1.server,
                "[Ranked] Battle started: ${player1.name.string} vs ${player2.name.string}!")
        }

        result.ifErrored {
            player1.sendSystemMessage(Component.literal("[Ranked] Failed to start battle."))
            player2.sendSystemMessage(Component.literal("[Ranked] Failed to start battle."))
            // Battle never started — teleport back immediately if we already moved them.
            originals[player1.uuid]?.let { restore(player1, it) }
            originals[player2.uuid]?.let { restore(player2, it) }
        }

        cleanup(player1.uuid, player2.uuid)
    }

    private fun captureLocation(player: ServerPlayer): OriginalLocation {
        val level = player.serverLevel()
        return OriginalLocation(
            worldId = level.dimension().location().toString(),
            x = player.x, y = player.y, z = player.z,
            yaw = player.yRot, pitch = player.xRot,
        )
    }

    private fun teleport(player: ServerPlayer, pos: ArenaPos) {
        val level = resolveLevel(player.server, pos.world) ?: run {
            CobblemonRanked.logger.warn("Unknown arena dimension '{}' — skipping teleport for {}",
                pos.world, player.name.string)
            return
        }
        player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
    }

    private fun restore(player: ServerPlayer, loc: OriginalLocation) {
        val level = resolveLevel(player.server, loc.worldId) ?: run {
            CobblemonRanked.logger.warn("Cannot restore {} — original dimension '{}' not loaded",
                player.name.string, loc.worldId)
            return
        }
        player.teleportTo(level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
    }

    private fun resolveLevel(server: MinecraftServer, worldId: String): ServerLevel? {
        val rl = ResourceLocation.tryParse(worldId) ?: return null
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, rl))
    }

    /**
     * Teleports the two players in [match] back to their captured starting locations.
     * No-op when the match has no captured locations (arena was disabled at start).
     * Players who logged out are skipped — they'll wake up at the arena, a known limitation.
     */
    private fun teleportBack(server: MinecraftServer, match: ActiveRankedMatch) {
        if (match.originalLocations.isEmpty()) return
        for ((uuid, loc) in match.originalLocations) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            restore(player, loc)
        }
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
                    teleportBack(winnerPlayer.server, match)
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
            teleportBack(server, match)
        }
    }

    private fun resolveMatch(winner: ServerPlayer, loser: ServerPlayer) {
        applyMatchResult(
            server = winner.server,
            winnerUuid = winner.uuid, winnerName = winner.name.string,
            loserUuid = loser.uuid, loserName = loser.name.string,
        )
    }

    /**
     * Applies an ELO match outcome: updates ELO/wins/losses, persists, marks the day
     * for decay tracking, and broadcasts the result + leaderboard to anyone online.
     *
     * Shared by the real battle path ([resolveMatch]) and the simulate-from-console path
     * ([simulateMatch]) so both paths exercise the exact same ELO/persistence/leaderboard
     * code.
     */
    fun applyMatchResult(
        server: MinecraftServer,
        winnerUuid: UUID, winnerName: String,
        loserUuid: UUID, loserName: String,
    ): MatchOutcome {
        val store = CobblemonRanked.eloStore
        val config = CobblemonRanked.config
        val winnerData = store.getOrCreate(winnerUuid, winnerName)
        val loserData = store.getOrCreate(loserUuid, loserName)

        val oldWinnerElo = winnerData.elo
        val oldLoserElo = loserData.elo

        val (newWinnerElo, newLoserElo) = EloCalculator.calculate(
            winnerElo = oldWinnerElo,
            loserElo = oldLoserElo,
            kFactor = config.kFactor,
            minimumElo = config.minimumElo,
        )

        winnerData.elo = newWinnerElo
        winnerData.wins++
        winnerData.lastBattleDate = LocalDate.now().toString()

        loserData.elo = newLoserElo
        loserData.losses++
        loserData.lastBattleDate = LocalDate.now().toString()

        store.save()
        DecayManager.recordBattle()

        // Quest advancements for the winner: first_pvp_win + any newly-crossed ELO thresholds.
        // Skips silently when the player is offline (e.g., during simulateMatch from console)
        // or when the datapack hasn't loaded the advancement.
        server.playerList.getPlayer(winnerUuid)?.let { winnerPlayer ->
            awardQuest(winnerPlayer, "server:first_pvp_win")
            for (threshold in ELO_THRESHOLDS) {
                if (newWinnerElo >= threshold && oldWinnerElo < threshold) {
                    awardQuest(winnerPlayer, "server:reach_elo_$threshold")
                }
            }
        }

        val winnerDelta = newWinnerElo - oldWinnerElo
        val loserDelta = newLoserElo - oldLoserElo
        broadcast(server, "[Ranked] $winnerName defeated $loserName!")
        broadcast(server,
            "[Ranked] $winnerName: $oldWinnerElo -> $newWinnerElo (+$winnerDelta) | " +
            "$loserName: $oldLoserElo -> $newLoserElo ($loserDelta)")

        val leaderboard = store.getLeaderboard()
        val topN = leaderboard.take(config.leaderboardSize)
        broadcast(server, "[Ranked] Leaderboard:")
        topN.forEachIndexed { i, (_, data) ->
            broadcast(server, "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)")
        }

        return MatchOutcome(
            winnerName = winnerName, loserName = loserName,
            oldWinnerElo = oldWinnerElo, newWinnerElo = newWinnerElo,
            oldLoserElo = oldLoserElo, newLoserElo = newLoserElo,
        )
    }

    /**
     * Simulates a ranked match between two players (online or not) using offline-mode
     * UUIDs derived from names. Winner is chosen randomly weighted by ELO win probability:
     * `expected1 = 1 / (1 + 10^((elo2 - elo1) / 400))`. Then [applyMatchResult] runs the
     * usual ELO/persistence/leaderboard path.
     */
    fun simulateMatch(server: MinecraftServer, name1: String, name2: String): SimulateOutcome {
        val store = CobblemonRanked.eloStore
        val uuid1 = offlineUuid(name1)
        val uuid2 = offlineUuid(name2)
        val data1 = store.getOrCreate(uuid1, name1)
        val data2 = store.getOrCreate(uuid2, name2)

        // Snapshot pre-match ELOs *before* applyMatchResult mutates the records, so the
        // returned SimulateOutcome reflects the state used to compute the win probability.
        val preElo1 = data1.elo
        val preElo2 = data2.elo
        val expected1 = 1.0 / (1.0 + Math.pow(10.0, (preElo2 - preElo1) / 400.0))
        val roll = Math.random()
        val player1Wins = roll < expected1

        val winnerName: String; val loserName: String
        val winnerUuid: UUID; val loserUuid: UUID
        if (player1Wins) {
            winnerName = name1; winnerUuid = uuid1
            loserName = name2; loserUuid = uuid2
        } else {
            winnerName = name2; winnerUuid = uuid2
            loserName = name1; loserUuid = uuid1
        }
        val outcome = applyMatchResult(server, winnerUuid, winnerName, loserUuid, loserName)
        return SimulateOutcome(
            name1 = name1, name2 = name2,
            elo1 = preElo1, elo2 = preElo2,
            expected1 = expected1, roll = roll, player1Wins = player1Wins,
            applied = outcome,
        )
    }

    private fun offlineUuid(name: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

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

    /**
     * ELO thresholds that grant `server:reach_elo_<N>` advancements. Set deliberately as
     * milestones; the current "active quest goal" is 1100 (see HUD cascade in the server-quests
     * datapack). The remaining thresholds award silently with `goal`-framed advancements until
     * promoted.
     */
    private val ELO_THRESHOLDS = listOf(1100, 1200, 1300, 1500, 2000)

    /**
     * Award `advancementId`'s default criterion to [player] if the advancement exists and the
     * player hasn't completed it. Used to grant the quest-datapack advancements from event
     * handlers. No-op on missing datapack (server starts up without quest pack loaded).
     */
    private fun awardQuest(player: ServerPlayer, advancementId: String, criterion: String = "done") {
        val rl = ResourceLocation.parse(advancementId)
        val holder = player.server.advancements.get(rl) ?: return
        val progress = player.advancements.getOrStartProgress(holder)
        if (progress.isDone) return
        player.advancements.award(holder, criterion)
    }
}
