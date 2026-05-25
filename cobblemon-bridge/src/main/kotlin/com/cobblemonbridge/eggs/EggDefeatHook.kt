package com.cobblemonbridge.eggs

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.util.AfkBridge
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * Timer-based egg hatching, gated on non-AFK playtime. Replaces the earlier defeat-counter
 * implementation (which was flaky around what counted as a "wild defeat" in concurrent battles).
 *
 * Per-tier duration of active play required:
 *   common = 1h, uncommon = 2h, rare = 4h, ultra / ultra_rare = 8h.
 *
 * Mechanism:
 *   1. **Initialization** (per-second scan of online players' inventories) — when a gacha-tagged
 *      egg appears that we haven't seen before, stamp `cobblemongacha_seconds_remaining` with
 *      the tier's full duration, pin Cobreeding's `TIMER` component to a near-infinite value so
 *      Cobreeding's own playtime tick never finishes the hatch on its own, and DM the player
 *      the expected play time.
 *   2. **Tick** (per second, only for non-AFK online players) — decrement
 *      `seconds_remaining` by 1 for every gacha-tagged egg in the player's inventory. When a
 *      counter reaches 0, flip Cobreeding's `TIMER` to 1 so its next inventory tick hatches
 *      the egg, and chat the player that it's ready.
 *
 * The class name is preserved (legacy) to minimize churn in [com.cobblemonbridge.CobblemonBridge]
 * — the BATTLE_VICTORY subscription is gone; only the server-tick subscriber remains.
 */
object EggDefeatHook {

    private val TIER_SECONDS = mapOf(
        "common" to 3600,        // 1h
        "uncommon" to 7200,      // 2h
        "rare" to 14400,         // 4h
        "ultra" to 28800,        // 8h
        "ultra_rare" to 28800,   // 8h
    )
    /** Default duration for eggs without a gacha tier tag — i.e. Cobreeding daycare bred eggs. */
    private const val BRED_DEFAULT_SECONDS = 1800  // 30m
    private const val BRED_LABEL = "bred"
    private const val TICKS_PER_SECOND = 20

    private const val NBT_TIER = "cobblemongacha_tier"
    private const val NBT_INITIALIZED = "cobblemongacha_bridge_initialized"
    private const val NBT_SECONDS_REMAINING = "cobblemongacha_seconds_remaining"
    private const val NBT_HATCH_READY = "cobblemongacha_hatch_ready"

    private var subTickCounter = 0

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        subTickCounter++
        if (subTickCounter < TICKS_PER_SECOND) return
        subTickCounter = 0
        if (!CobreedingBridge.available()) return
        for (player in event.server.playerList.players) {
            initializeNewEggs(player)
            // Always sync TIMER from our counter (overwrites Cobreeding's own decrement so the
            // tooltip stays accurate AND AFK time doesn't slip through). Only DECREMENT the
            // counter when the player isn't AFK.
            tickActiveEggs(player, decrementCounter = !AfkBridge.isAfk(player.uuid))
        }
    }

    // ─── Initialization ────────────────────────────────────────────────────
    private fun initializeNewEggs(player: ServerPlayer) {
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.isEmpty) continue
            if (!CobreedingBridge.isPokemonEgg(stack)) continue
            // Fast path: skip eggs we've already stamped.
            val existing = stack.get(DataComponents.CUSTOM_DATA)
            if (existing != null && existing.copyTag().getBoolean(NBT_INITIALIZED)) continue

            val tag = existing?.copyTag() ?: net.minecraft.nbt.CompoundTag()
            val gachaTier = tag.getString(NBT_TIER).takeIf { it.isNotEmpty() }
            val (label, durationSeconds) = if (gachaTier != null) {
                val seconds = TIER_SECONDS[gachaTier] ?: continue
                gachaTier to seconds
            } else {
                // Cobreeding daycare-bred (or any other untagged source) — 30-min default.
                BRED_LABEL to BRED_DEFAULT_SECONDS
            }

            // Sync Cobreeding TIMER to our duration (in cobreeding ticks, 20/sec). The per-second
            // tick re-syncs from seconds_remaining, so Cobreeding's tooltip stays accurate AND
            // its natural per-tick decrement gets continuously overwritten — that's how AFK
            // pause works (during AFK we don't decrement seconds_remaining, so the sync rewinds
            // Cobreeding's decrement back to where it was).
            CobreedingBridge.setTimer(stack, durationSeconds * TICKS_PER_SECOND)
            tag.putBoolean(NBT_INITIALIZED, true)
            tag.putInt(NBT_SECONDS_REMAINING, durationSeconds)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            // Force a slot broadcast so the client picks up the new TIMER immediately —
            // Cobreeding's tooltip reads TIMER directly off the client-side ItemStack, and
            // without an explicit slot packet the client keeps showing the egg's original
            // 10-minute timer until next relog.
            forceSlotSync(player, i, stack)

            val durationLabel = formatDuration(durationSeconds)
            player.sendSystemMessage(Component.literal(
                "§e✦ §fNew §a${label.replaceFirstChar { it.uppercase() }}§f egg detected. " +
                    "§7Will hatch after §f$durationLabel§7 of active (non-AFK) play."
            ))
            CobblemonBridge.logger.info(
                "Initialized egg slot {} for {} (label {}, {}s remaining)",
                i, player.gameProfile.name, label, durationSeconds,
            )
        }
    }

    /**
     * Push a slot update to the player's client. Required because writing a DataComponent
     * (Cobreeding's TIMER) in-place on the server-side ItemStack doesn't automatically trigger
     * an inventory sync packet — the client keeps showing whatever TIMER it cached when the
     * stack last entered the inventory. Without this, the tooltip lies about hatch time until
     * the player relogs.
     *
     * The packet uses the player's inventory container ID (0) and the inventory slot index
     * remapped through [Inventory.findSlotMatchingItem]'s vanilla convention. For the player
     * inventory: hotbar slots 0-8 map to container slots 36-44, main inventory slots 9-35 map
     * to 9-35. We mirror that mapping here.
     */
    private fun forceSlotSync(player: ServerPlayer, invSlot: Int, stack: ItemStack) {
        // Vanilla mapping: main inventory slots 9-35 are container slots 9-35;
        // hotbar slots 0-8 are container slots 36-44.
        val containerSlot = if (invSlot in 0..8) invSlot + 36 else invSlot
        player.connection.send(
            ClientboundContainerSetSlotPacket(
                player.inventoryMenu.containerId,
                player.inventoryMenu.incrementStateId(),
                containerSlot,
                stack,
            )
        )
    }

    private fun formatDuration(seconds: Int): String = when {
        seconds >= 3600 -> {
            val h = seconds / 3600
            if (h == 1) "1 hour" else "$h hours"
        }
        seconds >= 60 -> {
            val m = seconds / 60
            if (m == 1) "1 minute" else "$m minutes"
        }
        else -> "$seconds seconds"
    }

    // ─── Per-second tick (non-AFK players only) ────────────────────────────
    private fun tickActiveEggs(player: ServerPlayer, decrementCounter: Boolean) {
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.isEmpty) continue
            if (!CobreedingBridge.isPokemonEgg(stack)) continue
            val data = stack.get(DataComponents.CUSTOM_DATA) ?: continue
            val tag = data.copyTag()
            if (!tag.getBoolean(NBT_INITIALIZED)) continue
            if (tag.getBoolean(NBT_HATCH_READY)) continue  // counter already at 0, Cobreeding hatches naturally next tick
            // Label: gacha tier if present, otherwise "bred" (cobreeding daycare egg).
            val label = tag.getString(NBT_TIER).takeIf { it.isNotEmpty() } ?: BRED_LABEL

            val remaining = tag.getInt(NBT_SECONDS_REMAINING)
            val next = if (decrementCounter) (remaining - 1).coerceAtLeast(0) else remaining
            if (next != remaining) tag.putInt(NBT_SECONDS_REMAINING, next)

            // ALWAYS sync Cobreeding TIMER from our seconds counter — this overwrites Cobreeding's
            // own per-second decrement (so AFK time, during which we don't decrement, doesn't
            // slip through) AND keeps the Cobreeding tooltip showing a sensible countdown.
            CobreedingBridge.setTimer(stack, next * TICKS_PER_SECOND)
            // Force-broadcast the slot so the client's cached TIMER updates in real time
            // (Cobreeding's tooltip reads off the client-side ItemStack).
            forceSlotSync(player, i, stack)

            if (next == 0) {
                tag.putBoolean(NBT_HATCH_READY, true)
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                player.sendSystemMessage(Component.literal(
                    "§e✦ §fYour §a${label.replaceFirstChar { it.uppercase() }}§f egg is ready to hatch!"
                ))
                CobblemonBridge.logger.info(
                    "Egg ready to hatch for {}: slot {} label {}",
                    player.gameProfile.name, i, label,
                )
            } else if (next != remaining) {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            }
        }
    }
}
