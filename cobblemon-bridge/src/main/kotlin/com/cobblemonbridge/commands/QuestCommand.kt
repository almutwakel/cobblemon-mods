package com.cobblemonbridge.commands

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.ChatFormatting
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Player-facing `/quests` command. Reads advancement state at runtime, formats output in chat,
 * and toggles the action-bar HUD by manipulating the `cq_hud_off` tag (same tag the
 * datapack-side `/trigger cq_hud_toggle` flips, so the two interfaces converge on one state).
 *
 * The quest IDs are hardcoded here because the chain ordering and section grouping are
 * presentation concerns that don't naturally live in advancement metadata. To add a new quest:
 * append its ResourceLocation to the right list and reload the mod. Display title and frame
 * are read from the advancement's `display` block at runtime.
 */
object QuestCommand {

    /** Quests shown in order on the linear chain (the action-bar HUD's focus). */
    private val LINEAR_CHAIN = listOf(
        "server:craft_pokeball",
        "server:catch_pokemon",
        "server:farm_carrots",
        "server:beat_gym_1",
        "server:first_pvp_win",
        "server:reach_elo_1100",
    )

    private val INCOME_TRACK = listOf(
        "server:reach_income_100",
        "server:reach_income_1000",
        "server:reach_income_10000",
        "server:reach_income_100000",
    )

    private val ELO_TRACK = listOf(
        "server:reach_elo_1100",
        "server:reach_elo_1200",
        "server:reach_elo_1300",
        "server:reach_elo_1500",
        "server:reach_elo_2000",
    )

    private val STANDALONE = listOf(
        "server:join_colony",
    )

    private const val HUD_OFF_TAG = "cq_hud_off"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("quests")
                .executes { ctx -> showCurrent(ctx.source) }
                .then(Commands.literal("current").executes { ctx -> showCurrent(ctx.source) })
                .then(Commands.literal("list").executes { ctx -> showList(ctx.source) })
                .then(Commands.literal("help").executes { ctx -> showHelp(ctx.source) })
                .then(Commands.literal("hud")
                    .executes { ctx -> showHudState(ctx.source) }
                    .then(Commands.literal("on").executes { ctx -> setHud(ctx.source, on = true) })
                    .then(Commands.literal("off").executes { ctx -> setHud(ctx.source, on = false) })
                    .then(Commands.literal("toggle").executes { ctx -> toggleHud(ctx.source) }))
        )
    }

    // ─── handlers ────────────────────────────────────────────────────────────

    private fun showHelp(source: CommandSourceStack): Int {
        source.sendSystemMessage(Component.literal("§e[Quests] §fCommands:"))
        source.sendSystemMessage(Component.literal("§7  /quests §f— current quest"))
        source.sendSystemMessage(Component.literal("§7  /quests list §f— full quest tree"))
        source.sendSystemMessage(Component.literal("§7  /quests hud on|off|toggle §f— on-screen HUD"))
        return 1
    }

    private fun showCurrent(source: CommandSourceStack): Int {
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c/quests must be run by a player."))
            return 0
        }
        val server = player.server
        val current = LINEAR_CHAIN
            .mapNotNull { resolveHolder(server, it) }
            .firstOrNull { !player.advancements.getOrStartProgress(it).isDone }
        if (current == null) {
            source.sendSystemMessage(Component.literal("§a✓ All main quests complete! Check §f/quests list§a for side goals."))
            return 1
        }
        val title = current.value().display().map { it.title.string }.orElse(current.id.toString())
        val desc = current.value().display().map { it.description.string }.orElse("")
        source.sendSystemMessage(Component.literal("§e★ Current: §f$title"))
        if (desc.isNotEmpty()) {
            source.sendSystemMessage(Component.literal("§7   $desc"))
        }
        return 1
    }

    private fun showList(source: CommandSourceStack): Int {
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c/quests must be run by a player."))
            return 0
        }
        val server = player.server

        source.sendSystemMessage(Component.literal("§8§m                 §r §e§lServer Progression §8§m                 "))

        emitSection(source, player, server, "Main Quests", LINEAR_CHAIN, showCurrentMarker = true)
        emitSection(source, player, server, "Income", INCOME_TRACK, showCurrentMarker = false)
        emitSection(source, player, server, "Ranked Ladder", ELO_TRACK, showCurrentMarker = false)
        emitSection(source, player, server, "Other", STANDALONE, showCurrentMarker = false)

        return 1
    }

    private fun emitSection(
        source: CommandSourceStack,
        player: ServerPlayer,
        server: net.minecraft.server.MinecraftServer,
        sectionName: String,
        ids: List<String>,
        showCurrentMarker: Boolean,
    ) {
        source.sendSystemMessage(Component.literal("§7§l[ §f$sectionName §7§l]"))
        var firstIncompleteSeen = false
        for (id in ids) {
            val holder = resolveHolder(server, id) ?: continue
            val title = holder.value().display().map { it.title.string }.orElse(id)
            val done = player.advancements.getOrStartProgress(holder).isDone
            val marker = when {
                done -> "§a✓"
                showCurrentMarker && !firstIncompleteSeen -> { firstIncompleteSeen = true; "§e▶" }
                else -> "§7○"
            }
            val titleColor = if (done) "§a" else if (marker.startsWith("§e")) "§f" else "§7"
            source.sendSystemMessage(Component.literal("  $marker $titleColor$title"))
        }
    }

    private fun showHudState(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val on = !player.tags.contains(HUD_OFF_TAG)
        val state = if (on) "§aON" else "§cOFF"
        source.sendSystemMessage(Component.literal("§7Quest HUD is currently $state§7. Toggle: §f/quests hud toggle"))
        return 1
    }

    private fun setHud(source: CommandSourceStack, on: Boolean): Int {
        val player = source.player ?: return 0
        if (on) {
            player.removeTag(HUD_OFF_TAG)
            source.sendSystemMessage(Component.literal("§7Quest HUD §aON§7 — current quest will appear above your hotbar."))
        } else {
            player.addTag(HUD_OFF_TAG)
            source.sendSystemMessage(Component.literal("§7Quest HUD §cOFF§7 — chat updates only on quest completion."))
        }
        return 1
    }

    private fun toggleHud(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val isOff = player.tags.contains(HUD_OFF_TAG)
        return setHud(source, on = isOff)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun resolveHolder(server: net.minecraft.server.MinecraftServer, id: String): AdvancementHolder? {
        val rl = try { ResourceLocation.parse(id) } catch (_: Exception) { return null }
        return server.advancements.get(rl)
    }
}
