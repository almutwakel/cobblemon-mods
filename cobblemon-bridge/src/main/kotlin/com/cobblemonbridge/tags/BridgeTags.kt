package com.cobblemonbridge.tags

/**
 * The bridge mod is driven entirely by entity tags (the vanilla `Tags: [String]` NBT array).
 * Each tag is namespaced under `cobblemon_bridge.` and may carry a dot-separated payload that
 * specifies the hook's parameter — for example `cobblemon_bridge.adjust_level.50` sets the
 * battle's `adjustLevel` to 50 for the duration of a fight with that NPC.
 *
 * The dot separator (not slash or colon) is required because vanilla `/tag` only accepts
 * scoreboard tag characters: `[0-9a-zA-Z_+\-.]`. Slashes and colons are rejected by the
 * `StringReader.readUnquotedString` parser.
 *
 * Authors add tags to an entity with `/tag <entity> add cobblemon_bridge.adjust_level.50`.
 * The bridge listens to Cobblemon events, walks the active entity's tag set, and applies any
 * matching hook. Adding a new hook means adding a prefix here + a handler that reads the suffix.
 *
 * Why tags instead of a per-mod config or NBT field:
 *   - Vanilla `/tag` works without any custom command schema.
 *   - Tags survive entity reloads (they're part of the entity's persistent NBT).
 *   - Datapack `loot_table` / `function` blocks can stamp tags on summon/spawn for free.
 *   - Discoverable via `/data get entity <selector> Tags`.
 */
object BridgeTags {

    /** Namespace prefix shared by every bridge-controlled tag. */
    const val NAMESPACE: String = "cobblemon_bridge"

    /** Tag prefix that scales the battle level (numeric suffix). */
    const val ADJUST_LEVEL: String = "$NAMESPACE.adjust_level"

    /**
     * Tag prefix that distributes Cobblemon Pokémon EXP across the player's party when they
     * right-click the tagged entity (e.g. a Cobbleloots loot ball). Numeric suffix is the total
     * EXP awarded — split equally across all non-empty party slots. The entity is despawned
     * after the grant so it's single-use, first-grab-wins.
     */
    const val GIVE_PARTY_EXP: String = "$NAMESPACE.give_party_exp"

    /**
     * Parse the numeric suffix from a `cobblemon_bridge.adjust_level.<N>` tag.
     * Returns null if the tag isn't an `adjust_level` tag or the suffix isn't a positive int.
     * Examples:
     *   `cobblemon_bridge.adjust_level.50` → 50
     *   `cobblemon_bridge.adjust_level.`   → null  (missing suffix)
     *   `cobblemon_bridge.adjust_level`    → null  (no separator)
     *   `cobblemon_bridge.adjust_level.0`  → null  (must be >= 1; 0 means "no scaling" so authors should remove the tag instead)
     *   `other.adjust_level.50`            → null  (wrong namespace)
     */
    fun parseAdjustLevel(tag: String): Int? {
        val prefix = "$ADJUST_LEVEL."
        if (!tag.startsWith(prefix)) return null
        val suffix = tag.removePrefix(prefix)
        val level = suffix.toIntOrNull() ?: return null
        return if (level in 1..100) level else null
    }

    /**
     * Convenience: find the first valid `adjust_level` tag in [tags] and return its numeric value,
     * or null if none. If multiple are present, the first one wins (authors shouldn't stack them).
     */
    fun findAdjustLevel(tags: Iterable<String>): Int? =
        tags.firstNotNullOfOrNull { parseAdjustLevel(it) }

    /**
     * Parse the numeric suffix from a `cobblemon_bridge.give_party_exp.<N>` tag. Same shape as
     * [parseAdjustLevel] but the range is wider — Cobblemon EXP totals can run into thousands
     * for high-tier loot balls (Ultra Ball ≈ 3000 EXP, ~ Exp Candy M).
     */
    fun parseGivePartyExp(tag: String): Int? {
        val prefix = "$GIVE_PARTY_EXP."
        if (!tag.startsWith(prefix)) return null
        val suffix = tag.removePrefix(prefix)
        val amount = suffix.toIntOrNull() ?: return null
        return if (amount in 1..1_000_000) amount else null
    }

    /** First valid `give_party_exp` tag wins. */
    fun findGivePartyExp(tags: Iterable<String>): Int? =
        tags.firstNotNullOfOrNull { parseGivePartyExp(it) }

    /**
     * Tag prefix marking an NPC as gym leader N. When a player wins a battle against an NPC
     * carrying this tag, the bridge awards `server:beat_gym_<N>` (defined in the quest datapack).
     * Used to gate progression — e.g., PvP unlocks after beat_gym_1.
     */
    const val GYM_ID: String = "$NAMESPACE.gym_id"

    /** `cobblemon_bridge.gym_id.<N>` → N. Range 1..30 to keep ids sensible. */
    fun parseGymId(tag: String): Int? {
        val prefix = "$GYM_ID."
        if (!tag.startsWith(prefix)) return null
        val id = tag.removePrefix(prefix).toIntOrNull() ?: return null
        return if (id in 1..30) id else null
    }

    fun findGymId(tags: Iterable<String>): Int? =
        tags.firstNotNullOfOrNull { parseGymId(it) }

    /**
     * Tag marking a gym leader as the **challenge variant**. Carried in addition to
     * `cobblemon_bridge.gym_id.<N>`, NOT as a replacement — so [GymBattleAdjustHook] still sees
     * the shared gym_id and downlevels the player to the gym's cap, while [GymDefeatHook]
     * routes the victory to `beat_gym_<N>_challenge` and [GymPrereqHook] gates the interact on
     * the player having beaten the corresponding mainline gym.
     */
    const val GYM_CHALLENGE: String = "$NAMESPACE.gym_challenge"

    fun isGymChallenge(tags: Iterable<String>): Boolean =
        tags.any { it == GYM_CHALLENGE }
}
