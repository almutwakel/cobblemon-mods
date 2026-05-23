package com.cobblemonbridge.adapters

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

/**
 * Stamps newly-spawned Cobbleloots loot ball entities with a `cobblemon_bridge.give_party_exp.<N>`
 * tag so the generic [com.cobblemonbridge.battle.GivePartyExpHook] handles them when the player
 * right-clicks.
 *
 * The adapter is gated by `ModList.isLoaded("cobbleloots")` so cobblemon-bridge stays usable
 * without Cobbleloots installed. Detection uses the entity's registry id (`cobbleloots:loot_ball`)
 * rather than an `instanceof` check, so we don't take a compile-time dep on Cobbleloots. Tier
 * is read off the loot ball's instance via reflection on `getLootBallDataId()` — that method is
 * stable across Cobbleloots 2.x and exposes the tier name ("poke" / "great" / "ultra" / etc.).
 *
 * Tier → EXP mapping is intentional and aligned to Cobblemon's Exp Candy item scale:
 *   poke  → 100   (Exp Candy XS)
 *   great → 800   (Exp Candy S)
 *   ultra → 3000  (Exp Candy M)
 * Other Cobbleloots tier variants (azure, master, safari, etc.) are intentionally NOT mapped —
 * the server's datapack should disable those via `data_pack_disabled_loot_balls`.
 */
object CobbleloootsAdapter {

    private val LOOT_BALL_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobbleloots", "loot_ball")

    private val EXP_BY_TIER: Map<String, Int> = mapOf(
        "poke" to 100,
        "great" to 800,
        "ultra" to 3000,
    )

    fun isPresent(): Boolean = ModList.get()?.isLoaded("cobbleloots") == true

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        if (event.level.isClientSide) return
        val entity = event.entity
        if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.type) != LOOT_BALL_ID) return

        // Avoid re-tagging if we (or a reload) already handled this entity.
        if (entity.tags.any { it.startsWith("${BridgeTags.GIVE_PARTY_EXP}.") }) return

        val tier = readTier(entity)
        if (tier == null) {
            CobblemonBridge.logger.debug("Loot ball at {} had no readable tier; skipping", entity.blockPosition())
            return
        }
        val amount = EXP_BY_TIER[tier]
        if (amount == null) {
            CobblemonBridge.logger.debug(
                "Loot ball tier '{}' is not mapped (disable it via data_pack_disabled_loot_balls)", tier,
            )
            return
        }
        entity.addTag("${BridgeTags.GIVE_PARTY_EXP}.$amount")
        CobblemonBridge.logger.debug("Tagged {} loot ball at {} with party_exp={}", tier, entity.blockPosition(), amount)
    }

    /**
     * Reflectively call `getLootBallDataId(): String` on the entity. Returns null if the method
     * doesn't exist (Cobbleloots not loaded, version mismatch, or wrong entity type).
     */
    private fun readTier(entity: Any): String? = try {
        val method = entity.javaClass.getMethod("getLootBallDataId")
        method.invoke(entity) as? String
    } catch (_: NoSuchMethodException) {
        null
    } catch (e: Exception) {
        CobblemonBridge.logger.warn("Reflective getLootBallDataId failed", e)
        null
    }

    /**
     * Public lookup so the interact handler can resolve EXP for a loot ball that didn't get
     * tagged at spawn time (e.g. it pre-dates the bridge install, or [EntityJoinLevelEvent]
     * didn't fire for it). Returns null when the tier isn't mapped or the reflection fails.
     */
    fun expFor(entity: Any): Int? = readTier(entity)?.let { EXP_BY_TIER[it] }
}
