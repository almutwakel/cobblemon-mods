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
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 9-slot read-only menu used for the rolling animation. The reward is decided up front and
 * passed in — the animation is purely cosmetic. Slots 0 and 8 are tier-coloured glass borders.
 * Slot 4 (centre) cycles through random candidates per the configured tick intervals before
 * settling on the decided reward.
 *
 * State lives in `activeRolls` keyed by player UUID so close/disconnect can finalise gracefully.
 */
class RollMenu(
    syncId: Int,
    private val playerInventory: Inventory,
    private val display: SimpleContainer,
) : AbstractContainerMenu(GachaMenuRegistry.ROLL.get(), syncId) {

    init {
        for (i in 0 until 9) {
            addSlot(object : Slot(display, i, 8 + i * 18, 18) {
                override fun mayPickup(player: Player) = false
                override fun mayPlace(stack: ItemStack) = false
            })
        }
        for (i in 0 until 3) for (j in 0 until 9) {
            addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 50 + i * 18))
        }
        for (i in 0 until 9) addSlot(Slot(playerInventory, i, 8 + i * 18, 108))
    }

    override fun quickMoveStack(player: Player, slot: Int): ItemStack = ItemStack.EMPTY
    override fun stillValid(player: Player) = true

    companion object {
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

        fun clientStub(syncId: Int, inv: Inventory): RollMenu =
            RollMenu(syncId, inv, SimpleContainer(9))

        fun openFor(player: ServerPlayer, tier: KeyTier, table: LootTable, cratePos: BlockPos?) {
            val decided = RewardRoller.roll(table)
            val container = SimpleContainer(9)
            val borderColor = tierBorder(tier)
            container.setItem(0, borderColor); container.setItem(8, borderColor)
            val state = RollState(tier, decided, container, cratePos)
            activeRolls[player.uuid] = state

            val provider = object : MenuProvider {
                override fun getDisplayName(): Component =
                    Component.literal("§e${tier.displayName} Box — §6Rolling…")
                override fun createMenu(syncId: Int, inv: Inventory, p: Player): AbstractContainerMenu =
                    RollMenu(syncId, inv, container)
            }
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
            if (player.containerMenu is RollMenu) player.closeContainer()
            RewardGranter.grant(player, state.decided)
            PullAnnouncer.broadcast(player.server, player, state.tier, state.decided, state.cratePos)
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
}
