package com.cobblemonranked.gui

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import java.util.function.Consumer

/**
 * Public entry point for the team-selection flow. Preserves the original Fabric-era
 * constructor signature for callers (RankedBattleManager); internally delegates to the
 * vanilla-MenuType-based [TeamSelectionMenuProvider].
 */
class TeamSelectionGui(
    private val player: ServerPlayer,
    private val maxLegendaries: Int,
    private val onConfirm: Consumer<List<Pokemon>>,
    private val onCancel: Runnable,
) {
    fun open() {
        val provider = TeamSelectionMenuProvider(
            player = player,
            maxLegendaries = maxLegendaries,
            onConfirm = { team -> onConfirm.accept(team) },
            onCancel = { onCancel.run() },
        )
        player.openMenu(provider)
    }
}
