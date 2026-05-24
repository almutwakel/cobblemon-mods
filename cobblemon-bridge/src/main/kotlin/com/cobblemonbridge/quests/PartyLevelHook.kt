package com.cobblemonbridge.quests

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemonbridge.CobblemonBridge

/**
 * Awards `server:reach_party_level_20` the first time the player has any party member at
 * level 20 or above (matches the starting level cap — finishing this quest signals the player
 * has hit the natural ceiling and is ready for Gym 1). Fires on two events:
 *   - `LEVEL_UP_EVENT` — a party Pokémon levels up to ≥ 20 from training.
 *   - `POKEMON_CAPTURED` — a Pokémon caught at level ≥ 20.
 *
 * The advancement system makes the award idempotent; whichever event fires first wins, the
 * other becomes a no-op.
 */
object PartyLevelHook {

    private const val TARGET_LEVEL: Int = 20
    private const val ADVANCEMENT_ID: String = "server:reach_party_level_20"

    fun registerEvents() {
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL) { event ->
            if (event.newLevel < TARGET_LEVEL) return@subscribe
            val player = event.pokemon.getOwnerPlayer() ?: return@subscribe
            award(player, "level_up", event.pokemon.species.translatedName.string, event.newLevel)
        }
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event ->
            if (event.pokemon.level < TARGET_LEVEL) return@subscribe
            award(event.player, "catch", event.pokemon.species.translatedName.string, event.pokemon.level)
        }
    }

    private fun award(
        player: net.minecraft.server.level.ServerPlayer,
        via: String,
        species: String,
        level: Int,
    ) {
        val awarded = QuestAdvancements.award(player, ADVANCEMENT_ID, criterion = "done")
        if (awarded) {
            CobblemonBridge.logger.info(
                "cobblemon-bridge: awarded {} to {} ({} {} at level {})",
                ADVANCEMENT_ID, player.gameProfile.name, via, species, level,
            )
        }
    }
}
