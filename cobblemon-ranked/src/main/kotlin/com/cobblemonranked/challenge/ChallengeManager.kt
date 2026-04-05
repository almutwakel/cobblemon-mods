package com.cobblemonranked.challenge

import com.cobblemon.mod.common.Cobblemon
import com.cobblemonranked.CobblemonRanked
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.util.UUID

data class PendingChallenge(
    val challengerUuid: UUID,
    val targetUuid: UUID,
    val isForced: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChallengeManager {
    private val pendingChallenges: MutableMap<UUID, PendingChallenge> = mutableMapOf()
    private val CHALLENGE_TIMEOUT_MS = 60_000L

    /**
     * Attempt to challenge a player. Returns null on success, or an error message.
     */
    fun challenge(challenger: ServerPlayer, target: ServerPlayer): String? {
        if (challenger.uuid == target.uuid) {
            return "You can't challenge yourself."
        }

        if (isPlayerInBattle(target)) {
            return "${target.name.string} is already in a battle."
        }

        if (pendingChallenges.containsKey(challenger.uuid)) {
            return "You already have a pending challenge. Wait for it to expire or be answered."
        }

        val store = CobblemonRanked.eloStore
        val challengerData = store.getOrCreate(challenger.uuid, challenger.name.string)
        val targetData = store.getOrCreate(target.uuid, target.name.string)

        val challengerIsLower = challengerData.elo < targetData.elo
        val today = LocalDate.now().toString()

        val isForced = if (challengerIsLower) {
            val lastForce = challengerData.forceLog[target.uuid.toString()]
            if (lastForce == today) {
                false // Already forced today for this pair
            } else {
                true
            }
        } else {
            false // Higher ELO can't force
        }

        val challenge = PendingChallenge(
            challengerUuid = challenger.uuid,
            targetUuid = target.uuid,
            isForced = isForced
        )
        pendingChallenges[target.uuid] = challenge

        if (isForced) {
            challengerData.forceLog[target.uuid.toString()] = today
            CobblemonRanked.eloStore.save()

            target.sendSystemMessage(
                Component.literal("[Ranked] ${challenger.name.string} (${challengerData.elo}) has forced you into a ranked match! Preparing team selection...")
            )
            challenger.sendSystemMessage(
                Component.literal("[Ranked] Force challenge sent to ${target.name.string} (${targetData.elo}). Preparing team selection...")
            )
            return null
        } else {
            target.sendSystemMessage(
                Component.literal("[Ranked] ${challenger.name.string} (${challengerData.elo}) challenges you to a ranked match! Type /ranked accept or /ranked decline. (60s timeout)")
            )
            challenger.sendSystemMessage(
                Component.literal("[Ranked] Challenge sent to ${target.name.string} (${targetData.elo}). Waiting for response...")
            )
            return null
        }
    }

    fun accept(player: ServerPlayer): PendingChallenge? {
        val challenge = pendingChallenges.remove(player.uuid) ?: return null
        if (System.currentTimeMillis() - challenge.timestamp > CHALLENGE_TIMEOUT_MS) {
            return null
        }
        return challenge
    }

    fun decline(player: ServerPlayer): Boolean {
        val challenge = pendingChallenges.remove(player.uuid) ?: return false
        val server = player.server
        val challenger = server.playerList.getPlayer(challenge.challengerUuid)
        challenger?.sendSystemMessage(
            Component.literal("[Ranked] ${player.name.string} declined your challenge.")
        )
        return true
    }

    fun getPendingForced(playerUuid: UUID): PendingChallenge? {
        val challenge = pendingChallenges[playerUuid]
        if (challenge != null && challenge.isForced) {
            pendingChallenges.remove(playerUuid)
            return challenge
        }
        return null
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = pendingChallenges.filter { now - it.value.timestamp > CHALLENGE_TIMEOUT_MS }
        expired.forEach { (uuid, _) -> pendingChallenges.remove(uuid) }
    }

    private fun isPlayerInBattle(player: ServerPlayer): Boolean {
        return try {
            Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null
        } catch (e: Exception) {
            false
        }
    }
}
