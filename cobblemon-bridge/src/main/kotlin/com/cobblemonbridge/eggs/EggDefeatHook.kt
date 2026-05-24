package com.cobblemonbridge.eggs

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * Defeat-driven egg hatching. Replaces Cobreeding's playtime-based tick with a counter that
 * advances when the holder defeats a wild Pokémon. Threshold per tier:
 *   common = 5, uncommon = 10, rare = 15, ultra / ultra_rare = 20.
 *
 * Two responsibilities:
 *   1. **Initialization** — every 20 server ticks (1s), scan online players' inventories for
 *      cobblemon-gacha tagged eggs (`cobblemongacha_tier` in `minecraft:custom_data`) whose
 *      `cobblemongacha_bridge_initialized` flag is missing. Bump Cobreeding's `TIMER` to a
 *      huge value so the natural playtime decrement never finishes the hatch on its own.
 *      Set the flag so we don't repeat the bump.
 *   2. **Defeat hook** — on wild [BattleVictoryEvent], find the **leftmost** tagged egg in the
 *      winner's inventory (hotbar 0–8 first, then main 9–35). Increment its
 *      `cobblemongacha_defeats_consumed` counter, send a chat message naming the egg's species
 *      + current progress, and on threshold reach set Cobreeding's `TIMER` to 1 so the next
 *      Cobreeding inventoryTick hatches it.
 */
object EggDefeatHook {

    private val THRESHOLDS = mapOf(
        "common" to 5,
        "uncommon" to 10,
        "rare" to 15,
        "ultra" to 20,
        "ultra_rare" to 20,
    )
    private const val NEVER_HATCH_TIMER = 999_999_999
    private const val HATCH_NOW_TIMER = 1
    private const val INIT_SCAN_INTERVAL_TICKS = 20  // 1 second

    private const val NBT_TIER = "cobblemongacha_tier"
    private const val NBT_DEFEATS = "cobblemongacha_defeats_consumed"
    private const val NBT_INITIALIZED = "cobblemongacha_bridge_initialized"

    private var tickCounter = 0

    fun registerEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            handleWildDefeat(event)
        }
    }

    @SubscribeEvent
    fun onServerTickPost(event: ServerTickEvent.Post) {
        tickCounter++
        if (tickCounter < INIT_SCAN_INTERVAL_TICKS) return
        tickCounter = 0
        if (!CobreedingBridge.available()) return
        for (player in event.server.playerList.players) {
            initializeNewEggs(player)
        }
    }

    // ─── Initialization ────────────────────────────────────────────────────
    private fun initializeNewEggs(player: ServerPlayer) {
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.isEmpty) continue
            if (!CobreedingBridge.isPokemonEgg(stack)) continue
            val data = stack.get(DataComponents.CUSTOM_DATA) ?: continue
            val tag = data.copyTag()
            if (!tag.contains(NBT_TIER)) continue  // not one of ours
            if (tag.getBoolean(NBT_INITIALIZED)) continue  // already done
            val priorTimer = CobreedingBridge.getTimer(stack)
            CobreedingBridge.setTimer(stack, NEVER_HATCH_TIMER)
            tag.putBoolean(NBT_INITIALIZED, true)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            CobblemonBridge.logger.info(
                "Initialized egg slot {} for {} (tier {}, TIMER {} → {})",
                i, player.gameProfile.name, tag.getString(NBT_TIER), priorTimer, NEVER_HATCH_TIMER,
            )
        }
    }

    // ─── Defeat hook ───────────────────────────────────────────────────────
    private fun handleWildDefeat(event: BattleVictoryEvent) {
        val isWildBattle = event.losers.isNotEmpty() &&
            event.losers.all { it is PokemonBattleActor } &&
            event.losers.none { it is TrainerBattleActor }
        if (!isWildBattle) return

        for (winner in event.winners) {
            val playerActor = winner as? PlayerBattleActor ?: continue
            val player = playerActor.entity as? ServerPlayer ?: continue
            advanceLeftmostEgg(player)
        }
    }

    private fun advanceLeftmostEgg(player: ServerPlayer) {
        if (!CobreedingBridge.available()) return
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.isEmpty) continue
            if (!CobreedingBridge.isPokemonEgg(stack)) continue
            val data = stack.get(DataComponents.CUSTOM_DATA) ?: continue
            val tag = data.copyTag()
            val tier = tag.getString(NBT_TIER).takeIf { it.isNotEmpty() } ?: continue
            val threshold = THRESHOLDS[tier] ?: continue

            val current = tag.getInt(NBT_DEFEATS)
            val next = current + 1
            tag.putInt(NBT_DEFEATS, next)
            // Re-pack custom_data with the bumped counter.
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

            val species = extractSpeciesName(stack)
            val displayName = if (species != null) "§a$species§7" else "§a${tier.replaceFirstChar { it.uppercase() }}§7"

            if (next >= threshold) {
                CobreedingBridge.setTimer(stack, HATCH_NOW_TIMER)
                player.sendSystemMessage(Component.literal(
                    "§e✦ §fYour $displayName§f egg is ready to hatch! §7(${next}/${threshold} defeats)"
                ))
                CobblemonBridge.logger.info(
                    "Egg ready to hatch for {}: slot {} tier {} ({} defeats)",
                    player.gameProfile.name, i, tier, next,
                )
            } else {
                player.sendSystemMessage(Component.literal(
                    "§7Egg progress: §f${next}§7/§f${threshold}§7 defeats toward your $displayName§7 egg"
                ))
            }
            return  // only the leftmost egg progresses
        }
    }

    /**
     * Tries to extract a friendly species name from Cobreeding's encrypted POKEMON_PROPERTIES
     * payload. Decryption proper would require Cobreeding's `EggUtilities.decrypt` — for the
     * chat hint we'd rather not pay that round-trip every battle, so we read the egg's display
     * name (which Cobreeding sets to e.g. "Pikachu Egg"). Returns null if we can't extract.
     */
    private fun extractSpeciesName(stack: ItemStack): String? {
        val name = stack.get(DataComponents.CUSTOM_NAME)?.string
            ?: stack.get(DataComponents.ITEM_NAME)?.string
        if (name.isNullOrBlank()) return null
        // Cobreeding's auto-named eggs end in " Egg"; strip that for the chat hint.
        return name.removeSuffix(" Egg").trim().ifEmpty { null }
    }
}
