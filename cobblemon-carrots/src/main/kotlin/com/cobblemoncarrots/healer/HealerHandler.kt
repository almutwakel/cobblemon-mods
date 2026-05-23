package com.cobblemoncarrots.healer

import com.cobblemon.mod.common.Cobblemon
import com.cobblemoncarrots.CobblemonCarrots
import com.cobblemoncarrots.economy.EconomyBridge
import com.cobblemoncarrots.economy.MarketBridge
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Replaces Cobblemon's free Poké Healer behaviour with a carrot+money cost flow.
 *
 * Flow:
 *   1. Player right-clicks `cobblemon:healing_machine`.
 *   2. We cancel the vanilla interact, compute deficit + carrot need + money short, send a
 *      chat prompt with clickable [CONFIRM] / [CANCEL].
 *   3. Server stashes a `PendingHeal` by player UUID with a 30s TTL.
 *   4. Player clicks the chat element → `/cobblemoncarrots heal confirm <token>` runs.
 *   5. We re-verify the cost is still affordable, consume carrots from inventory + money from
 *      the economy, then call `pokemon.heal()` on every party member.
 *
 * Why chat instead of a chest GUI: zero new registry IDs (server stays single-binary, vanilla
 * clients connect without a client mod), and the UX is one click per decision — same as the
 * gacha odds preview already in chat.
 */
object HealerHandler {

    private val HEALING_MACHINE_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "healing_machine")
    private const val SESSION_TTL_MS = 30_000L

    private data class PendingHeal(val cost: HealCost, val createdAtMs: Long)
    private val pending: MutableMap<UUID, PendingHeal> = ConcurrentHashMap()

    @SubscribeEvent
    fun onUseBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val block = event.level.getBlockState(event.pos).block
        val blockId = BuiltInRegistries.BLOCK.getKey(block)
        if (blockId != HEALING_MACHINE_ID) return

        // Don't run on sneaking — let players still place / break with the healer.
        if (player.isShiftKeyDown) return

        promptCost(player)
        event.cancellationResult = InteractionResult.SUCCESS
        event.isCanceled = true
    }

    private fun promptCost(player: ServerPlayer) {
        val party = Cobblemon.storage.getParty(player)
        val members = party.iterator().asSequence().toList()
        if (members.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Healer] You have no Pokémon to heal."))
            return
        }
        val deficits = members.filter { !it.isFainted() }.map { it.maxHealth - it.currentHealth }
        val faintedMaxHps = members.filter { it.isFainted() }.map { it.maxHealth }
        if (deficits.all { it == 0 } && faintedMaxHps.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[Healer] Your party is already at full HP."))
            return
        }

        val cfg = CobblemonCarrots.config
        val carrotsHeld = countCarrots(player)
        // Live market price replaces the flat config when cobblemon-market is loaded.
        val marketAvailable = MarketBridge.available()
        val effectivePrice = if (marketAvailable) MarketBridge.getCarrotBuyPrice() ?: cfg.carrotPrice else cfg.carrotPrice
        val cost = HealCalculator.compute(
            hpDeficits = deficits,
            faintedMaxHps = faintedMaxHps,
            hpPerCarrot = cfg.hpPerCarrot,
            healerReviveCarrotCost = cfg.healerReviveCarrotCost,
            carrotsInInventory = carrotsHeld,
            carrotPrice = effectivePrice,
            minCarrots = cfg.healerMinCarrots,
        )

        // Market stock check — if the shop can't cover the shortage, refuse the heal entirely
        // (the heal call is atomic on Pokémon.heal(), no partial path).
        val stockAvailable = if (marketAvailable && cost.carrotsShort > 0) MarketBridge.getCarrotStock() else null
        if (stockAvailable != null && stockAvailable < cost.carrotsShort) {
            player.sendSystemMessage(Component.literal("§8§m                §r §e§l[Poké Healer] §8§m                "))
            player.sendSystemMessage(Component.literal(
                "§c[Healer] Market is short — only §f$stockAvailable§c carrots in stock, you need §f${cost.carrotsShort}§c more."
            ))
            player.sendSystemMessage(Component.literal(
                "§7Bring more carrots, wait for restock (hourly), or grow your own."
            ))
            return
        }

        pending[player.uuid] = PendingHeal(cost, System.currentTimeMillis())

        val totalDeficit = deficits.sum()
        val fainted = faintedMaxHps.size
        player.sendSystemMessage(Component.literal("§8§m                §r §e§l[Poké Healer] §8§m                "))
        player.sendSystemMessage(Component.literal(
            "§7Party deficit: §f$totalDeficit HP §7across §f${deficits.count { it > 0 }}§7 mons" +
                if (fainted > 0) " + §c$fainted fainted§7" else ""
        ))
        player.sendSystemMessage(Component.literal(
            "§7Carrots needed: §f${cost.totalCarrots}§7 (§a${carrotsHeld}§7 you have / §c${cost.carrotsShort}§7 short)"
        ))
        if (cost.moneyCost > 0) {
            val priceLabel = if (marketAvailable) "§f$$effectivePrice§7 ea (market)" else "§f$$effectivePrice§7 ea"
            player.sendSystemMessage(Component.literal(
                "§7Money cost (for the §c${cost.carrotsShort}§7 short carrots @ $priceLabel): §6$${cost.moneyCost}"
            ))
        }
        // Clickable confirm / cancel
        val confirm = clickable("§a§l[CONFIRM]", "/cobblemoncarrots heal confirm")
        val cancel = clickable("§c§l[CANCEL]", "/cobblemoncarrots heal cancel")
        player.sendSystemMessage(Component.literal("    ").append(confirm).append(Component.literal("   ")).append(cancel))
    }

    /** Called by the registered `/cobblemoncarrots heal confirm` command. */
    fun executeConfirm(player: ServerPlayer) {
        val now = System.currentTimeMillis()
        val pendingHeal = pending.remove(player.uuid)
        if (pendingHeal == null) {
            player.sendSystemMessage(Component.literal("§c[Healer] No pending heal. Right-click a Poké Healer first."))
            return
        }
        if (now - pendingHeal.createdAtMs > SESSION_TTL_MS) {
            player.sendSystemMessage(Component.literal("§c[Healer] Quote expired (>30s). Right-click the healer again."))
            return
        }
        val cost = pendingHeal.cost
        // Re-check carrot count (player may have used some)
        val carrotsNow = countCarrots(player)
        if (carrotsNow < cost.carrotsInInventory - 0) {
            // they consumed carrots since the quote was issued; re-prompt them
            player.sendSystemMessage(Component.literal("§c[Healer] You've used carrots since the quote — please right-click the healer again for a fresh estimate."))
            return
        }
        // Payment path: when cobblemon-market is loaded, route through the market so the buy
        // hits the live price and decrements stock atomically. Otherwise fall back to a flat
        // withdrawal at the locked carrotPrice config (legacy path; price won't move).
        val actualMoneyCost: Int
        if (cost.carrotsShort > 0 && MarketBridge.available()) {
            when (val r = MarketBridge.buyCarrots(player, cost.carrotsShort)) {
                is MarketBridge.BuyResult.Success -> actualMoneyCost = r.totalCost
                is MarketBridge.BuyResult.OutOfStock -> {
                    player.sendSystemMessage(Component.literal(
                        "§c[Healer] Market ran out of carrots — only §f${r.available}§c left, you needed §f${cost.carrotsShort}§c."
                    ))
                    return
                }
                is MarketBridge.BuyResult.InsufficientBalance -> {
                    player.sendSystemMessage(Component.literal(
                        "§c[Healer] Insufficient funds — need §6$${r.need}§c, have §6$${r.have}§c."
                    ))
                    return
                }
                MarketBridge.BuyResult.Unknown -> {
                    player.sendSystemMessage(Component.literal("§c[Healer] Market transaction failed. Try again."))
                    return
                }
            }
        } else if (cost.moneyCost > 0) {
            val ok = EconomyBridge.withdraw(player.uuid, cost.moneyCost)
            if (!ok) {
                player.sendSystemMessage(Component.literal("§c[Healer] Insufficient funds for the carrots short (§6$${cost.moneyCost}§c). Buy carrots elsewhere first."))
                return
            }
            actualMoneyCost = cost.moneyCost
        } else {
            actualMoneyCost = 0
        }
        // Consume up to `totalCarrots` carrots from inventory (whichever we have, up to the need).
        val toConsume = minOf(cost.totalCarrots, carrotsNow)
        consumeCarrots(player, toConsume)
        // Heal everyone
        val party = Cobblemon.storage.getParty(player)
        for (mon in party) mon.heal()  // heals HP + status + fainted

        player.serverLevel().playSound(
            null, player.x, player.y, player.z,
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.8f, 1.2f,
        )
        val msg = StringBuilder("§a[Healer] Party fully restored. ")
        msg.append("§7Consumed §f$toConsume§7 carrot${if (toConsume == 1) "" else "s"}")
        if (actualMoneyCost > 0) msg.append("§7 + §6$${actualMoneyCost}")
        msg.append("§7.")
        player.sendSystemMessage(Component.literal(msg.toString()))
        CobblemonCarrots.logger.info(
            "Healed {} ({} carrots, {} money)",
            player.gameProfile.name, toConsume, actualMoneyCost,
        )
    }

    fun executeCancel(player: ServerPlayer) {
        if (pending.remove(player.uuid) != null) {
            player.sendSystemMessage(Component.literal("§7[Healer] Heal cancelled."))
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun countCarrots(player: ServerPlayer): Int =
        player.inventory.items.filter { it.item == Items.CARROT }.sumOf { it.count }

    private fun consumeCarrots(player: ServerPlayer, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val stack = player.inventory.getItem(i)
            if (stack.item != Items.CARROT) continue
            val take = minOf(stack.count, remaining)
            stack.shrink(take)
            remaining -= take
        }
    }

    private fun clickable(label: String, command: String): MutableComponent =
        Component.literal(label).withStyle(
            Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        )
}
