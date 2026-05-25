package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click handler for the market shopkeeper NPC. The NPC is spawned by
 * `/function server:market/spawn_npc` with a scoreboard tag of {@code cobblemon_bridge.market_vendor}.
 * Any entity right-click checks for that tag; if present we cancel the default interaction
 * (so the NPC's own dialog system doesn't intercept) and open [MarketMenu].
 *
 * Priority HIGHEST so we run before Cobblemon's NPC interaction handler, which by default
 * would open its own (empty) dialog flow.
 */
object MarketNpcHook {

    private const val VENDOR_TAG = "cobblemon_bridge.market_vendor"

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (VENDOR_TAG !in event.target.tags) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        try {
            MarketMenu.open(player)
        } catch (t: Throwable) {
            CobblemonMarket.logger.error("Failed to open MarketMenu for ${player.gameProfile.name}", t)
        }
    }
}
