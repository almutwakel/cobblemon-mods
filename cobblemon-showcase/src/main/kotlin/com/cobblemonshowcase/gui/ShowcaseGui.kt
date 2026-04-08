package com.cobblemonshowcase.gui

import com.cobblemonshowcase.CobblemonShowcase
import com.cobblemonshowcase.data.BadgeReader
import com.cobblemonshowcase.data.CrossModReader
import com.mojang.authlib.properties.PropertyMap
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Optional
import java.util.UUID

class ShowcaseGui(
    private val viewer: ServerPlayer,
    private val targetUuid: UUID,
    private val targetName: String,
    private val server: MinecraftServer
) {
    fun open() {
        val gui = SimpleGui(MenuType.GENERIC_9x6, viewer, false)
        gui.title = Component.literal("$targetName's Profile")

        val playerData = CobblemonShowcase.playerDataStore.load(targetUuid)
        val eloData = CrossModReader.getEloData(targetUuid)
        val eloRank = CrossModReader.getEloRank(targetUuid)
        val lastTeam = CrossModReader.getLastTeam(targetUuid)
        val badges = BadgeReader.loadBadges(targetUuid)
        val balance = CrossModReader.getBalance(targetUuid)
        val marketSpend = CrossModReader.getMarketSpend(targetUuid)

        val targetPlayer = server.playerList.getPlayer(targetUuid)
        val playtimeHours = if (targetPlayer != null) CrossModReader.getPlaytimeHours(targetPlayer) else null

        // === Row 1: Identity ===
        fillRow(gui, 0)

        val headBuilder = GuiElementBuilder(Items.PLAYER_HEAD)
            .setName(Component.literal(targetName))
        headBuilder.setComponent(
            DataComponents.PROFILE,
            ResolvableProfile(Optional.of(targetName), Optional.of(targetUuid), PropertyMap())
        )
        gui.setSlot(4, headBuilder.build())

        val teamName = playerData.team
        if (teamName != null) {
            gui.setSlot(6, GuiElementBuilder(Items.RED_BANNER)
                .setName(Component.literal("Team: $teamName"))
                .build())
        } else {
            gui.setSlot(6, GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                .setName(Component.literal("No Team"))
                .build())
        }

        // === Row 2: PVP Stats ===
        fillRow(gui, 1)

        val eloLore = if (eloData != null) {
            buildList {
                add(Component.literal("ELO: ${eloData.elo}"))
                add(Component.literal("Record: ${eloData.wins}W / ${eloData.losses}L"))
                if (eloRank != null) add(Component.literal("Rank: #$eloRank"))
            }
        } else {
            listOf(Component.literal("N/A"))
        }
        gui.setSlot(13, GuiElementBuilder(Items.DIAMOND_SWORD)
            .setName(Component.literal("PVP Stats"))
            .setLore(eloLore)
            .hideDefaultTooltip()
            .build())

        // === Row 3: Economy & Activity ===
        fillRow(gui, 2)

        gui.setSlot(20, GuiElementBuilder(Items.GOLD_INGOT)
            .setName(Component.literal("Balance"))
            .setLore(listOf(Component.literal("$balance PokeDollars")))
            .build())

        gui.setSlot(22, GuiElementBuilder(Items.EMERALD)
            .setName(Component.literal("Market Spend"))
            .setLore(listOf(Component.literal("$marketSpend PokeDollars spent")))
            .build())

        val playtimeText = if (playtimeHours != null) "${playtimeHours}h" else "Offline"
        gui.setSlot(24, GuiElementBuilder(Items.CLOCK)
            .setName(Component.literal("Playtime"))
            .setLore(listOf(Component.literal(playtimeText)))
            .build())

        // === Row 4: Last PVP Team ===
        fillRow(gui, 3)

        if (lastTeam != null) {
            for (i in 0 until 6) {
                val slot = 29 + i
                if (i < lastTeam.team.size) {
                    val pokemon = lastTeam.team[i]
                    val displayName = pokemon.nickname ?: pokemon.species
                    gui.setSlot(slot, GuiElementBuilder(Items.FIRE_CHARGE)
                        .setName(Component.literal(displayName))
                        .setLore(listOf(
                            Component.literal("Species: ${pokemon.species}"),
                            Component.literal("Level: ${pokemon.level}")
                        ))
                        .build())
                } else {
                    gui.setSlot(slot, GuiElementBuilder(Items.BARRIER)
                        .setName(Component.literal("Empty Slot"))
                        .build())
                }
            }
        } else {
            for (i in 0 until 6) {
                gui.setSlot(29 + i, GuiElementBuilder(Items.BARRIER)
                    .setName(Component.literal("No PVP Team"))
                    .build())
            }
        }

        // === Rows 5-6: Badges ===
        fillRow(gui, 4)
        fillRow(gui, 5)

        for (i in 0 until 18) {
            val slot = 36 + i
            if (i < badges.size) {
                val badge = badges[i]
                gui.setSlot(slot, GuiElementBuilder(Items.NETHER_STAR)
                    .setName(Component.literal(badge.name))
                    .setLore(listOf(Component.literal(badge.description)))
                    .build())
            } else {
                gui.setSlot(slot, GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal(" "))
                    .build())
            }
        }

        gui.open()
    }

    private fun fillRow(gui: SimpleGui, row: Int) {
        for (i in 0 until 9) {
            gui.setSlot(row * 9 + i, GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" "))
                .build())
        }
    }
}
