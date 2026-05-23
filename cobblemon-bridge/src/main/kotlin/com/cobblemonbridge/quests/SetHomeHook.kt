package com.cobblemonbridge.quests

import com.cobblemonbridge.CobblemonBridge
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.CommandEvent

/**
 * Awards `server:set_home` the first time a player runs `/sethome`. We listen to
 * [CommandEvent] (fires before the command executes) and pattern-match on the parsed root
 * literal — works regardless of which sethome alias the player typed (NeoEssentials supports
 * `/sethome`, `/setwarp` aliases under different roots).
 */
object SetHomeHook {

    private val SETHOME_ALIASES = setOf("sethome", "homeset")

    @SubscribeEvent
    fun onCommand(event: CommandEvent) {
        val source = event.parseResults.context.source
        val player = source.entity as? ServerPlayer ?: return
        val input = event.parseResults.reader.string.trimStart().removePrefix("/").lowercase()
        val first = input.substringBefore(' ')
        if (first !in SETHOME_ALIASES) return
        // Fire on the next tick — the command hasn't actually run yet, but for our purposes
        // (awarding an advancement on intent) we don't need to wait.
        val awarded = QuestAdvancements.award(player, "server:set_home", criterion = "done")
        if (awarded) {
            CobblemonBridge.logger.info(
                "cobblemon-bridge: awarded server:set_home to {} (cmd: /{})",
                player.gameProfile.name, first,
            )
        }
    }
}
