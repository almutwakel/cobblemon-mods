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

    /**
     * Early-game linear chain — the action-bar HUD's focus. The full gym ladder (24 entries)
     * lives in [MAIN_GYMS] / [ROTATING_GYMS] / [ELITE_FOUR] / [CHAMPION] and shows in the
     * `/quests list` output but not the HUD, to avoid overwhelming new players.
     */
    private val LINEAR_CHAIN = listOf(
        "server:select_pokemon",
        "server:set_home",
        "server:craft_pokeball",
        "server:catch_pokemon",
        "server:farm_carrots",
        "server:beat_wild_trainer",
        "server:reach_party_level_20",
        "server:beat_gym_1",
        "server:reach_income_250",
        "server:first_pvp_win",
    )

    /** Main gym ladder, 1–10. Sequential chain. */
    private val MAIN_GYMS: List<String> = (1..10).map { "server:beat_gym_$it" }

    /** Rotating gym slots, 11–19. Branch off gym 10. */
    private val ROTATING_GYMS: List<String> = (11..19).map { "server:beat_gym_$it" }

    /** Elite Four chambers, 20–23. Sequential after gym 10. */
    private val ELITE_FOUR: List<String> = (20..23).map { "server:beat_gym_$it" }

    /** Champion (gym 24). Final node after E4. */
    private val CHAMPION: List<String> = listOf("server:beat_gym_24")

    private val INCOME_TRACK = listOf(
        "server:reach_income_250",
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

    /**
     * Per-quest reward label, surfaced in `/quests` + `/quests list`. Source of truth lives in
     * each `quests/rewards/<id>.mcfunction` (via its `cq_reward_<…>` tag) — this map mirrors
     * those tags so the chat output matches what actually gets granted.
     * `null` means the quest has no reward (e.g. select_pokemon — the act of picking is its own
     * payoff).
     */
    private val REWARDS: Map<String, String?> = buildMap {
        put("server:select_pokemon",       "§f10 Poké Balls")
        put("server:set_home",             "§f3 Red Apricorn Sprouts")
        put("server:craft_pokeball",       "§fIron Pickaxe")
        put("server:catch_pokemon",        "§f3 Carrots")
        put("server:farm_carrots",         "§f3 Blue Apricorn Sprouts")
        put("server:beat_wild_trainer",    "§fSophisticated Backpack")
        put("server:reach_party_level_20", "§aCommon Egg")
        put("server:reach_income_250",     "§fPasture Block")
        put("server:first_pvp_win",        "§aCommon Egg")
        put("server:reach_income_1000",    "§fMinecolonies Supply Camp")
        put("server:reach_income_10000",   "§f1 Master Ball")
        put("server:reach_income_100000",  "§6Ultra Key")
        put("server:reach_elo_1100",       "§fGreat Ball + Super Potion")
        put("server:reach_elo_1200",       "§fUltra Ball + Hyper Potion")
        put("server:reach_elo_1300",       "§f1 Rare Candy")
        put("server:reach_elo_1500",       "§f1 Master Ball")
        put("server:reach_elo_2000",       "§6Ultra Key")
        put("server:join_colony",          "§fPoké Healer")
        // Gyms: most Rare Key; 10/19/23/24 are Ultra Key.
        for (i in 1..24) {
            val tier = if (i == 10 || i == 19 || i == 23 || i == 24) "§6Ultra Key" else "§5Rare Key"
            put("server:beat_gym_$i", tier)
        }
    }

    private fun rewardLabel(questId: String): String? = REWARDS[questId]

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
        val reward = rewardLabel(current.id.toString())
        if (reward != null) {
            source.sendSystemMessage(Component.literal("§7   Reward: $reward"))
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
        emitSection(source, player, server, "Gym Ladder", MAIN_GYMS, showCurrentMarker = false)
        emitSection(source, player, server, "Rotating Gyms", ROTATING_GYMS, showCurrentMarker = false)
        emitSection(source, player, server, "Elite Four", ELITE_FOUR, showCurrentMarker = false)
        emitSection(source, player, server, "Champion", CHAMPION, showCurrentMarker = false)
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
            val reward = rewardLabel(id)
            val rewardSuffix = if (reward != null && !done) " §8— $reward" else ""
            source.sendSystemMessage(Component.literal("  $marker $titleColor$title$rewardSuffix"))
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
