package com.cobblemonranked.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 6-row chest menu for picking up to 6 Pokemon from the player's PC + party.
 *
 * Layout:
 *  rows 0-1 (slots 0-17)  — current PC box (18 pokemon)
 *  row  2   (slots 18-26) — PC navigation: prev arrow, box label, next arrow + filler
 *  row  3   (slots 27-32) — party (6 pokemon); slot 33 PC/Party divider label
 *  row  4   (slots 36-44) — selection counter
 *  row  5   (slots 45-50) — selected team display (click to remove); 52 = confirm, 53 = cancel
 *
 * Click dispatch is server-side only. The client-side stub menu has no party/PC state and
 * never receives interactive logic — the client just renders the slot ItemStacks broadcast
 * from the server.
 */
class TeamSelectionMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val player: ServerPlayer?,
    private val maxLegendaries: Int,
    private val onConfirm: ((List<Pokemon>) -> Unit)?,
    private val onCancel: (() -> Unit)?,
) : AbstractContainerMenu(MenuRegistry.TEAM_SELECTION.get(), containerId) {

    private val display = SimpleContainer(SLOT_COUNT)
    private val selected = mutableListOf<Pokemon>()
    private var currentBox: Int = 0
    private val party: PartyStore? = player?.let { Cobblemon.storage.getParty(it) }
    private val pc: PCStore? = player?.let { Cobblemon.storage.getPC(it) }

    init {
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            addSlot(DisplaySlot(display, row * COLS + col, 8 + col * 18, 18 + row * 18))
        }
        repaint()
    }

    private fun repaint() {
        for (i in 0 until SLOT_COUNT) display.setItem(i, ItemStack.EMPTY)

        // Rows 0-1: current PC box
        val boxes = pc?.boxes
        val box = boxes?.getOrNull(currentBox)
        for (i in 0 until 18) {
            val pokemon: Pokemon? = try { box?.get(i) } catch (e: Exception) { null }
            display.setItem(i, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        // Row 2: PC navigation
        display.setItem(18, named(Items.ARROW, "§7← Previous Box"))
        for (i in listOf(19, 20, 21, 23, 24, 25)) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        display.setItem(22, named(Items.NAME_TAG, "Box ${currentBox + 1} / ${(boxes?.size ?: 1).coerceAtLeast(1)}"))
        display.setItem(26, named(Items.ARROW, "§7Next Box →"))

        // Row 3: party
        for (i in 0 until 6) {
            val pokemon = party?.get(i)
            display.setItem(27 + i, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                     else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        display.setItem(33, named(Items.CHEST, "§7^ PC | Party ^"))
        display.setItem(34, filler(Items.BLACK_STAINED_GLASS_PANE))
        display.setItem(35, filler(Items.BLACK_STAINED_GLASS_PANE))

        // Row 4: info bar
        for (i in 36..44) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        val info = ItemStack(Items.PAPER)
        info.set(DataComponents.CUSTOM_NAME, Component.literal("§eSelected: ${selected.size}/6"))
        if (selected.isNotEmpty()) {
            val lore = selected.map { Component.literal("§7- ${it.species.name} Lv.${it.level}") }
            info.set(DataComponents.LORE, net.minecraft.world.item.component.ItemLore(lore))
        }
        display.setItem(40, info)

        // Row 5: selected team + confirm + cancel
        for (i in 0 until 6) {
            display.setItem(45 + i, if (i < selected.size) pokemonStack(selected[i], true)
                                     else named(Items.GRAY_STAINED_GLASS_PANE, "§8Empty Slot"))
        }
        display.setItem(51, filler(Items.BLACK_STAINED_GLASS_PANE))
        display.setItem(52, named(Items.LIME_CONCRETE,
            Component.literal("Confirm Team").withStyle(Style.EMPTY.withBold(true))))
        display.setItem(53, named(Items.RED_CONCRETE,
            Component.literal("Cancel").withStyle(Style.EMPTY.withBold(true))))

        broadcastChanges()
    }

    private fun pokemonStack(pokemon: Pokemon, isSelected: Boolean): ItemStack {
        val item = if (isSelected) Items.LIME_STAINED_GLASS_PANE else Items.WHITE_STAINED_GLASS_PANE
        val stack = ItemStack(item)
        val legendary = if (pokemon.isLegendary()) " §c[LEGENDARY]" else ""
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("${pokemon.species.name} Lv.${pokemon.level}$legendary"))
        val lore = listOf(
            Component.literal("§7Type: ${pokemon.primaryType.name}" +
                (pokemon.secondaryType?.let { "/${it.name}" } ?: "")),
            Component.literal("§7Ability: ${pokemon.ability.name}"),
            Component.literal("§7HP: ${pokemon.currentHealth}/${pokemon.maxHealth}"),
            Component.literal(if (isSelected) "§eClick to deselect" else "§aClick to select"),
        )
        stack.set(DataComponents.LORE, net.minecraft.world.item.component.ItemLore(lore))
        return stack
    }

    private fun named(item: Item, name: String): ItemStack =
        named(item, Component.literal(name))

    private fun named(item: Item, name: Component): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, name)
        return stack
    }

    private fun filler(item: Item): ItemStack = named(item, " ")

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val sp = this.player ?: return
        val boxes = pc?.boxes ?: return
        when (slotId) {
            in 0 until 18 -> {
                // PC slot click — toggle selection
                val box = boxes.getOrNull(currentBox) ?: return
                val pokemon = try { box.get(slotId) } catch (e: Exception) { null } ?: return
                togglePokemon(pokemon, sp)
                repaint()
            }
            in 27 until 33 -> {
                // Party slot click — toggle selection
                val pokemon = party?.get(slotId - 27) ?: return
                togglePokemon(pokemon, sp)
                repaint()
            }
            in 45 until 51 -> {
                // Selected team click — remove
                val idx = slotId - 45
                if (idx < selected.size) {
                    selected.removeAt(idx)
                    repaint()
                }
            }
            18 -> {
                if (currentBox > 0) { currentBox--; repaint() }
            }
            26 -> {
                if (currentBox < boxes.size - 1) { currentBox++; repaint() }
            }
            52 -> {
                if (selected.isEmpty()) {
                    sp.sendSystemMessage(Component.literal("§c[Ranked] You must select at least 1 Pokemon!"))
                    return
                }
                sp.closeContainer()
                onConfirm?.invoke(selected.toList())
            }
            53 -> {
                sp.closeContainer()
                onCancel?.invoke()
            }
        }
    }

    private fun togglePokemon(pokemon: Pokemon, sp: ServerPlayer) {
        if (pokemon in selected) {
            selected.remove(pokemon)
        } else if (selected.size < 6) {
            selected.add(pokemon)
        } else {
            sp.sendSystemMessage(Component.literal("§c[Ranked] Team is full! Remove a Pokemon first."))
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private class DisplaySlot(c: SimpleContainer, slot: Int, x: Int, y: Int) : Slot(c, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = false
        override fun mayPickup(player: Player) = false
    }

    companion object {
        const val ROWS = 6
        const val COLS = 9
        const val SLOT_COUNT = ROWS * COLS

        /** Server-side factory used by TeamSelectionMenuProvider with real player state. */
        internal fun forServer(
            containerId: Int,
            playerInventory: Inventory,
            player: ServerPlayer,
            maxLegendaries: Int,
            onConfirm: (List<Pokemon>) -> Unit,
            onCancel: () -> Unit,
        ): TeamSelectionMenu = TeamSelectionMenu(containerId, playerInventory, player, maxLegendaries, onConfirm, onCancel)

        /** Client-side stub factory used by the registered MenuType factory. */
        fun clientStub(containerId: Int, playerInventory: Inventory): TeamSelectionMenu =
            TeamSelectionMenu(containerId, playerInventory, null, 0, null, null)
    }
}

class TeamSelectionMenuProvider(
    private val player: ServerPlayer,
    private val maxLegendaries: Int,
    private val onConfirm: (List<Pokemon>) -> Unit,
    private val onCancel: () -> Unit,
) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Select Your Team")
    override fun createMenu(containerId: Int, inv: Inventory, ignored: Player): AbstractContainerMenu =
        TeamSelectionMenu.forServer(containerId, inv, player, maxLegendaries, onConfirm, onCancel)
}
