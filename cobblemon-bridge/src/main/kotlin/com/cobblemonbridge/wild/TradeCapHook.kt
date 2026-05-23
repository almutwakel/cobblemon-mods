package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.TradeEvent
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.LevelCap
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Cancels trades where either participant would receive a Pokémon above their gym-progression
 * level cap. Prevents level-laundering — an end-game player can't hand a level-80 Mewtwo to a
 * pre-gym-1 player.
 *
 * Cancellation is bidirectional: if either side would receive an over-cap mon, the whole
 * trade is rejected, both players get a chat explanation.
 */
object TradeCapHook {

    fun registerEvents() {
        CobblemonEvents.TRADE_EVENT_PRE.subscribe(Priority.NORMAL) { event ->
            val server = guessServer(event) ?: return@subscribe
            val p1 = resolvePlayer(server, event.tradeParticipant1.uuid)
            val p2 = resolvePlayer(server, event.tradeParticipant2.uuid)
            val incoming1 = event.tradeParticipant2Pokemon  // p1 receives p2's offered mon
            val incoming2 = event.tradeParticipant1Pokemon  // p2 receives p1's offered mon

            val violations = mutableListOf<String>()
            if (p1 != null && !isExempt(incoming1)) {
                val cap1 = LevelCap.forPlayer(p1)
                if (!LevelCap.isUncapped(cap1) && incoming1.level > cap1) {
                    violations += "${p1.gameProfile.name} (cap ${cap1}) would receive ${incoming1.species.name} L${incoming1.level}"
                }
            }
            if (p2 != null && !isExempt(incoming2)) {
                val cap2 = LevelCap.forPlayer(p2)
                if (!LevelCap.isUncapped(cap2) && incoming2.level > cap2) {
                    violations += "${p2.gameProfile.name} (cap ${cap2}) would receive ${incoming2.species.name} L${incoming2.level}"
                }
            }
            if (violations.isEmpty()) return@subscribe

            event.cancel()
            val msg = Component.literal(
                "§c[Trade Blocked] Above your level cap. Beat more gyms first.\n§7" + violations.joinToString("; "),
            )
            p1?.sendSystemMessage(msg)
            p2?.sendSystemMessage(msg)
            CobblemonBridge.logger.info("Trade cancelled — {}", violations.joinToString("; "))
        }
    }

    private fun resolvePlayer(server: MinecraftServer, uuid: UUID): ServerPlayer? =
        server.playerList.getPlayer(uuid)

    /** Legendaries and mythicals can be traded across cap boundaries — they're rare and the
     *  whole point of trading is helping a friend get one. The recipient still can't use it in
     *  wild battles above their cap thanks to [WildBattleAdjustHook]'s legendary exemption
     *  routing differently, but they can own it. */
    private fun isExempt(pokemon: com.cobblemon.mod.common.pokemon.Pokemon): Boolean =
        pokemon.isLegendary() || pokemon.isMythical()

    /**
     * `TradeEvent` doesn't carry a [MinecraftServer] handle directly; we need one to resolve
     * the UUIDs. Pull it from the first participant whose [TradeParticipant.getParty] resolves
     * to a real [com.cobblemon.mod.common.api.storage.party.PlayerPartyStore], which knows
     * the world's server via its observers. Falls back to null if both parties are offline /
     * detached, which would be a weird state but we tolerate it.
     */
    private fun guessServer(event: TradeEvent.Pre): MinecraftServer? {
        for (participant in listOf(event.tradeParticipant1, event.tradeParticipant2)) {
            val uuid = participant.uuid
            // Try one of the loaded servers via reflection-free path:
            val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
            if (server != null) return server
            if (server == null) continue
        }
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
    }
}
