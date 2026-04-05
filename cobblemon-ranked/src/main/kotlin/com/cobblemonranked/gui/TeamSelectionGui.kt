package com.cobblemonranked.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import java.util.function.Consumer

class TeamSelectionGui(
    private val player: ServerPlayer,
    private val maxLegendaries: Int,
    private val onConfirm: Consumer<List<Pokemon>>,
    private val onCancel: Runnable
) {
    private val selected: MutableList<Pokemon> = mutableListOf()
    private var currentBox = 0
    private val party = Cobblemon.storage.getParty(player)
    private val pc = Cobblemon.storage.getPC(player)

    fun open() {
        rebuild()
    }

    private fun rebuild() {
        val gui = SimpleGui(MenuType.GENERIC_9x6, player, false)
        gui.title = Component.literal("Select Your Team (${selected.size}/6)")

        // Rows 1-2 (slots 0-17): PC Pokemon from current box
        val boxes = pc.boxes
        val boxCount = boxes.size
        val box = if (boxCount > 0 && currentBox < boxCount) boxes[currentBox] else null
        for (i in 0 until 18) {
            val pokemon: Pokemon? = try {
                box?.get(i)
            } catch (e: Exception) {
                null
            }
            if (pokemon != null) {
                gui.setSlot(i, pokemonElement(pokemon, pokemon in selected) {
                    toggleSelection(pokemon)
                    rebuild()
                })
            } else {
                gui.setSlot(i, filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
            }
        }

        // Row 3 (slots 18-26): PC navigation
        gui.setSlot(18, GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("<- Previous Box"))
            .setCallback { _, _, _ ->
                if (currentBox > 0) currentBox--
                rebuild()
            }.build())

        for (i in listOf(19, 20, 21, 23, 24, 25)) {
            gui.setSlot(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        }

        gui.setSlot(22, GuiElementBuilder(Items.NAME_TAG)
            .setName(Component.literal("Box ${currentBox + 1} / ${boxCount.coerceAtLeast(1)}"))
            .build())

        gui.setSlot(26, GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("Next Box ->"))
            .setCallback { _, _, _ ->
                if (currentBox < boxCount - 1) currentBox++
                rebuild()
            }.build())

        // Row 4 (slots 27-35): Party Pokemon
        for (i in 0 until 6) {
            val pokemon = party[i]
            if (pokemon != null) {
                gui.setSlot(27 + i, pokemonElement(pokemon, pokemon in selected) {
                    toggleSelection(pokemon)
                    rebuild()
                })
            } else {
                gui.setSlot(27 + i, filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
            }
        }
        gui.setSlot(33, GuiElementBuilder(Items.CHEST)
            .setName(Component.literal("^ PC | Party ^")).build())
        gui.setSlot(34, filler(Items.BLACK_STAINED_GLASS_PANE))
        gui.setSlot(35, filler(Items.BLACK_STAINED_GLASS_PANE))

        // Row 5 (slots 36-44): Info bar
        for (i in 36..44) {
            gui.setSlot(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        }
        gui.setSlot(40, GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("Selected: ${selected.size}/6"))
            .setLore(selected.map { Component.literal("- ${it.species.name} Lv.${it.level}") })
            .build())

        // Row 6 (slots 45-53): Selected team + Confirm + Cancel
        for (i in 0 until 6) {
            if (i < selected.size) {
                val pokemon = selected[i]
                gui.setSlot(45 + i, pokemonElement(pokemon, true) {
                    selected.remove(pokemon)
                    rebuild()
                })
            } else {
                gui.setSlot(45 + i, GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal("Empty Slot")).build())
            }
        }

        gui.setSlot(51, filler(Items.BLACK_STAINED_GLASS_PANE))

        gui.setSlot(52, GuiElementBuilder(Items.LIME_CONCRETE)
            .setName(Component.literal("Confirm Team").withStyle(Style.EMPTY.withBold(true)))
            .setCallback { _, _, _ ->
                if (selected.isEmpty()) {
                    player.sendSystemMessage(Component.literal("[Ranked] You must select at least 1 Pokemon!"))
                    return@setCallback
                }
                gui.close()
                onConfirm.accept(selected.toList())
            }.build())

        gui.setSlot(53, GuiElementBuilder(Items.RED_CONCRETE)
            .setName(Component.literal("Cancel").withStyle(Style.EMPTY.withBold(true)))
            .setCallback { _, _, _ ->
                gui.close()
                onCancel.run()
            }.build())

        gui.open()
    }

    private fun toggleSelection(pokemon: Pokemon) {
        if (pokemon in selected) {
            selected.remove(pokemon)
        } else if (selected.size < 6) {
            selected.add(pokemon)
        } else {
            player.sendSystemMessage(Component.literal("[Ranked] Team is full! Remove a Pokemon first."))
        }
    }

    private fun pokemonElement(
        pokemon: Pokemon,
        isSelected: Boolean,
        onClick: Runnable
    ): net.minecraft.world.item.ItemStack {
        val item = if (isSelected) Items.LIME_STAINED_GLASS_PANE else Items.WHITE_STAINED_GLASS_PANE
        val legendaryTag = if (pokemon.isLegendary()) " [LEGENDARY]" else ""
        return GuiElementBuilder(item)
            .setName(Component.literal("${pokemon.species.name} Lv.${pokemon.level}$legendaryTag"))
            .setLore(listOf(
                Component.literal("Type: ${pokemon.primaryType.name}" +
                    (pokemon.secondaryType?.let { "/${it.name}" } ?: "")),
                Component.literal("Ability: ${pokemon.ability.name}"),
                Component.literal("HP: ${pokemon.currentHealth}/${pokemon.hp}"),
                Component.literal(if (isSelected) "Click to deselect" else "Click to select")
            ))
            .setCallback { _, _, _ -> onClick.run() }
            .build()
    }

    private fun filler(item: net.minecraft.world.item.Item): net.minecraft.world.item.ItemStack {
        return GuiElementBuilder(item)
            .setName(Component.literal(" "))
            .build()
    }
}
