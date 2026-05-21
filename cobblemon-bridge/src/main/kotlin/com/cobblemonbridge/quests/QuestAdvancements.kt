package com.cobblemonbridge.quests

import com.cobblemonbridge.CobblemonBridge
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Tiny shared utility for awarding `server:*` advancements (the quest datapack at
 * `world/datapacks/server-quests/`) from in-house mod event handlers. The pattern is the same
 * in every caller — look up the advancement by ResourceLocation, no-op if it doesn't exist
 * (datapack not loaded yet, or advancement not yet defined), call `award(...)` with the
 * named criterion if found.
 *
 * Callers pass advancement ids as `"server:reach_elo_1100"` etc. The criterion name must match
 * the key in the advancement's `criteria` block (typically `"done"` for impossible-trigger
 * quests, or `"crossed"` for threshold-style ones).
 */
object QuestAdvancements {

    /**
     * Award [criterion] of [advancementId] to [player]. Returns true if the criterion was newly
     * completed (i.e. the player didn't already have it). Logs a debug line on miss so we can
     * see if a hook fires before the datapack loads.
     */
    fun award(player: ServerPlayer, advancementId: String, criterion: String = "done"): Boolean {
        val rl = ResourceLocation.parse(advancementId)
        val holder = player.server.advancements.get(rl)
        if (holder == null) {
            CobblemonBridge.logger.debug("Advancement {} not found; skipping award for {}", rl, player.gameProfile.name)
            return false
        }
        val progress = player.advancements.getOrStartProgress(holder)
        if (progress.isDone) return false
        return player.advancements.award(holder, criterion)
    }
}
