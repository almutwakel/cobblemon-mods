package com.cobblemongacha.gui

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.announce.PullAnnouncer
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.reward.RewardGranter
import com.cobblemongacha.reward.RewardRoller
import com.cobblemongacha.util.TickScheduler
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Opens a 9-slot vanilla chest menu (MenuType.GENERIC_9x1) and animates the centre slot. The mod
 * is server-only, so we do NOT register a custom MenuType — that would require the client to also
 * have the mod (custom registry entries are synced during the join handshake). Using the vanilla
 * chest menu means any client can render the GUI.
 *
 * The reward is decided up front and stored in `activeRolls`. The animation is cosmetic. Grant +
 * announce happens exactly once via `finalise`, which is called from animation end, the close
 * event, or the logged-out event.
 *
 * Read-only enforcement is server-side only: the vanilla client UI lets players try to click slots,
 * but the server rejects the click and corrects the client. For these brief (~4s) menus that's
 * acceptable.
 */
object RollMenu {

    private val log = org.slf4j.LoggerFactory.getLogger("cobblemon-gacha/roll")

    private data class RollState(
        val tier: KeyTier,
        val decided: LootEntry,
        val display: SimpleContainer,
        val cratePos: BlockPos?,
        var animation: TickScheduler.Cancellable? = null,
        var finalized: Boolean = false,
    )

    private val activeRolls = ConcurrentHashMap<UUID, RollState>()

    /** Is there an in-flight roll for this player? Used by the container-close listener. */
    fun isRolling(uuid: UUID): Boolean = activeRolls.containsKey(uuid)

    fun openFor(player: ServerPlayer, tier: KeyTier, table: LootTable, cratePos: BlockPos?) {
        val decided = RewardRoller.roll(table)
        val container = SimpleContainer(9)
        val borderColor = tierBorder(tier)
        container.setItem(0, borderColor); container.setItem(8, borderColor)
        val state = RollState(tier, decided, container, cratePos)
        activeRolls[player.uuid] = state

        val title = Component.literal("§e${tier.displayName} Box — §6Rolling…")
        val provider = SimpleMenuProvider({ syncId, inv, _ ->
            GachaChestMenu(rows = 1, syncId = syncId, inv = inv, container = container)
        }, title)
        player.openMenu(provider)

        val intervals = CobblemonGacha.config.animationTicks
        val candidatePool = table.entries.filter { it.weightPct > 0.0 }
        val random = Random.Default
        val sequence = List(intervals.size - 1) { candidatePool.random(random) } + decided

        state.animation = TickScheduler.chain(
            intervals = intervals,
            stepRun = { i ->
                val entry = sequence.getOrNull(i) ?: return@chain
                val stack = RewardGranter.representative(entry)
                container.setItem(4, stack)
                // Roulette tick — pitch slides from high to low as the wheel decelerates so the
                // run feels like it's slowing down, then the final settle pling fires in PullAnnouncer.
                val progress = if (intervals.size > 1) i.toFloat() / (intervals.size - 1) else 1f
                val pitch = (1.6f - 0.9f * progress).coerceIn(0.5f, 2.0f)
                player.serverLevel().playSound(
                    null, player.x, player.y, player.z,
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, pitch,
                )
            },
            finalRun = {
                container.setItem(4, RewardGranter.representative(decided))
                TickScheduler.later(CobblemonGacha.config.jackpotHoldTicks) {
                    finalise(player.uuid, player)
                }
            },
        )
    }

    /**
     * Finalise the roll for the given player. Idempotent — safe to call from animation end,
     * container-close handler, or PlayerLoggedOutEvent. Performs grant + announce exactly once.
     */
    fun finalise(uuid: UUID, player: ServerPlayer?) {
        val state = activeRolls.remove(uuid) ?: return
        if (state.finalized) return
        state.finalized = true
        state.animation?.cancel()
        if (player == null) {
            log.warn("Player {} disconnected during roll; reward dropped", uuid)
            return
        }
        player.closeContainer()
        val result = RewardGranter.grant(player, state.decided)
        PullAnnouncer.broadcast(
            player.server, player, state.tier, state.decided, state.cratePos, result.labelOverride,
        )
    }

    fun onPlayerClosedContainer(player: ServerPlayer) {
        finalise(player.uuid, player)
    }

    fun onPlayerLoggedOut(player: ServerPlayer) {
        finalise(player.uuid, player)
    }

    private fun tierBorder(tier: KeyTier): ItemStack {
        val item = when (tier) {
            KeyTier.COMMON -> Items.WHITE_STAINED_GLASS_PANE
            KeyTier.RARE -> Items.RED_STAINED_GLASS_PANE
            KeyTier.ULTRA -> Items.BLACK_STAINED_GLASS_PANE
        }
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§7${tier.displayName} Box"))
        return stack
    }
}
