package com.cobblemonbridge.quests

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Source of truth for the player's wild-Pokémon level cap.
 *
 * Cap formula:
 *   - Pre-gym 1:                20  (BASE)
 *   - Each mainline gym (1–10): +5 each → max of 70 after all 10 (matches E4 player cap)
 *   - Rotating gyms (11–18):    no contribution; they're side content
 *   - Elite Four (20–23):       no contribution UNTIL all 4 are beaten
 *   - Beating all 4 E4 (gym 23 = final E4 = Lance): cap removed entirely → [UNCAPPED]
 *
 * The cap is a soft upper bound used by:
 *   - `WildSpawnLevelCapHook` to clamp spawn level (legendaries exempt — see [isUncapped]).
 *   - `TradeCapHook` to reject incoming trades over-cap.
 *
 * Wild battles and non-gym trainer battles do NOT downlevel the player's team — only official
 * gym leaders do, via RCT's `adjustPlayerLevels: true` in the gym trainer JSON.
 */
object LevelCap {

    const val BASE: Int = 20
    const val PER_MAINLINE_GYM: Int = 5
    /** Sentinel return value when the player has finished the Elite Four. */
    const val UNCAPPED: Int = Int.MAX_VALUE

    private val MAINLINE_GYMS: List<ResourceLocation> = (1..10).map {
        ResourceLocation.fromNamespaceAndPath("server", "beat_gym_$it")
    }
    private val E4_FINAL: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("server", "beat_gym_23")  // last E4 trainer

    fun forPlayer(player: ServerPlayer): Int {
        val mgr = player.server.advancements
        // E4-complete: full uncap.
        val e4 = mgr.get(E4_FINAL)
        if (e4 != null && player.advancements.getOrStartProgress(e4).isDone) return UNCAPPED
        // Otherwise: BASE + (count of mainline gyms beaten) * 5.
        var beaten = 0
        for (rl in MAINLINE_GYMS) {
            val h = mgr.get(rl) ?: continue
            if (player.advancements.getOrStartProgress(h).isDone) beaten++
        }
        return BASE + beaten * PER_MAINLINE_GYM
    }

    /** Highest cap across a set of players. [UNCAPPED] from any one player short-circuits. */
    fun highestInRange(players: Iterable<ServerPlayer>): Int {
        var best = BASE
        for (p in players) {
            val c = forPlayer(p)
            if (c == UNCAPPED) return UNCAPPED
            if (c > best) best = c
        }
        return best
    }

    fun isUncapped(cap: Int): Boolean = cap == UNCAPPED
}
