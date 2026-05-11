package com.cobblemongacha.interaction

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.gui.OddsMenu
import com.cobblemongacha.gui.RollMenu
import com.cobblemongacha.item.KeyItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Listens to right-click-on-block. If the targeted block is a configured crate coord, cancels
 * the underlying interaction (so chests don't open) and routes to RollMenu or OddsMenu based
 * on whether the player holds a matching key.
 */
object CrateInteractionHandler {

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        val pos = event.pos
        val dimId = player.serverLevel().dimension().location().toString()
        val tier = matchedCrateTier(pos, dimId) ?: return

        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS

        val held = player.mainHandItem
        val heldTier = KeyItems.tierOf(held)
        val table = CobblemonGacha.tables[tier] ?: return

        if (heldTier == tier) {
            held.shrink(1)
            RollMenu.openFor(player, tier, table, pos)
        } else {
            OddsMenu.openFor(player, tier, table)
        }
    }

    private fun matchedCrateTier(pos: BlockPos, dimId: String): KeyTier? {
        for (tier in KeyTier.entries) {
            val crate = CobblemonGacha.config.crateOf(tier) ?: continue
            if (crate.x == pos.x && crate.y == pos.y && crate.z == pos.z && crate.dim == dimId) {
                return tier
            }
        }
        return null
    }
}
