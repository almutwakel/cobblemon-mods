package com.cobblemonmarket.economy

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Income-threshold quest awards. Called from the sell-trade path with the player's balance
 * before and after a deposit. For every threshold the balance newly crossed (strictly less
 * than before, ≥ after), award the corresponding `server:reach_income_<N>` advancement.
 *
 * The current "active quest goal" is the 1000 entry — its advancement is framed as `task` in
 * the datapack with a real reward bundle. The others are silent milestones (`goal` frame) until
 * promoted. The mod doesn't care about the difference — it just emits the award; the datapack
 * decides what fanfare each tier gets.
 *
 * Threshold list is centralized here so admins editing income tiers only have one place to
 * touch. Keep it in sync with the datapack's `data/server/advancement/reach_income_*.json`.
 */
object QuestRewards {

    // Aligned with the datapack's actual advancement files
    // (reach_income_250 / _1000 / _10000 / _100000). Any threshold here without a matching
    // advancement file silently no-ops in awardQuest, and any advancement without a threshold
    // here is unreachable — keep both lists in sync.
    private val INCOME_THRESHOLDS = listOf(250, 1000, 10000, 100000)

    fun checkIncomeThresholds(player: ServerPlayer, balanceBefore: Int, balanceAfter: Int) {
        if (balanceAfter <= balanceBefore) return
        for (threshold in INCOME_THRESHOLDS) {
            if (balanceBefore < threshold && balanceAfter >= threshold) {
                awardQuest(player, "server:reach_income_$threshold")
            }
        }
    }

    private fun awardQuest(player: ServerPlayer, advancementId: String, criterion: String = "done") {
        val rl = ResourceLocation.parse(advancementId)
        val holder = player.server.advancements.get(rl) ?: return
        val progress = player.advancements.getOrStartProgress(holder)
        if (progress.isDone) return
        player.advancements.award(holder, criterion)
    }
}
