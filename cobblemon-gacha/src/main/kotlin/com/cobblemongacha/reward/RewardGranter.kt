package com.cobblemongacha.reward

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.item.PlaceholderItems
import com.cobblemongacha.util.TickScheduler
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.component.CustomData
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Materialises a `LootEntry`'s `ItemSpec`s into `ItemStack`s and inserts them into the player's
 * inventory. If the inventory cannot hold a stack, the stack is dropped as an `ItemEntity` at the
 * player's feet. Returns the materialised stacks so callers (announcer) can describe what was given.
 *
 * Two specs need side-effects beyond inventory insertion:
 *   - `CobbreedingEgg` dispatches `/givepokemonegg <player> <species> min_perfect_ivs=2 [shiny=true] [ha=yes]`
 *     server-side and returns a *display-only* egg stack for the announce hover. The real egg is
 *     created by the Cobbreeding mod via the command. Eggs always carry `min_perfect_ivs=2` so the
 *     hatched Pokémon has two randomly-chosen perfect IVs. When the source entry requires HA, the
 *     species was already filtered to HA-capable picks and `ha=yes` is passed so the hatched mon
 *     gets the hidden ability for certain.
 *   - `RandomItem` picks one id from its list uniformly at random and falls through to vanilla
 *     materialisation.
 */
object RewardGranter {

    /**
     * Return type for `grant()`. `labelOverride`, when present, replaces `entry.label` in the
     * server-wide pull announce so eggs read as "Shiny Pikachu Egg §d[Hidden Ability]" rather
     * than the generic "Shiny Egg" wording from the loot CSV.
     */
    data class GrantResult(val stacks: List<ItemStack>, val labelOverride: String? = null)

    fun grant(player: ServerPlayer, entry: LootEntry): GrantResult {
        val stacks = mutableListOf<ItemStack>()
        var labelOverride: String? = null
        for (spec in entry.items) {
            when (spec) {
                is ItemSpec.CobbreedingEgg -> {
                    val outcome = dispatchEgg(player, spec)
                    if (outcome != null) {
                        stacks.add(outcome.display)
                        // First egg in the entry wins the announce-label override.
                        if (labelOverride == null) labelOverride = outcome.announceLabel
                    }
                }
                else -> {
                    val stack = materialize(spec) ?: continue
                    if (stack.isEmpty) continue
                    if (!player.inventory.add(stack)) {
                        val drop = ItemEntity(player.serverLevel(), player.x, player.y, player.z, stack)
                        drop.setDefaultPickUpDelay()
                        player.serverLevel().addFreshEntity(drop)
                    }
                    stacks.add(stack)
                }
            }
        }
        return GrantResult(stacks, labelOverride)
    }

    /**
     * Build the first representative ItemStack for an entry — used by OddsMenu to render the
     * "what does this entry give?" tile, and by RollMenu as the centre-slot reveal.
     */
    fun representative(entry: LootEntry): ItemStack {
        val first = entry.items.firstOrNull() ?: return ItemStack.EMPTY
        return materialize(first) ?: ItemStack.EMPTY
    }

    private fun materialize(spec: ItemSpec): ItemStack? = when (spec) {
        is ItemSpec.Vanilla -> {
            val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.id))
            if (item == Items.AIR) {
                CobblemonGacha.logger.warn("Unknown item id in loot table: {}", spec.id)
                null
            } else ItemStack(item, spec.count)
        }
        is ItemSpec.GachaKeyRef -> KeyItems.build(spec.tier, spec.count)
        is ItemSpec.Placeholder -> PlaceholderItems.build(spec)
        is ItemSpec.RandomItem -> {
            if (spec.ids.isEmpty()) {
                CobblemonGacha.logger.warn("RandomItem with empty id list")
                null
            } else {
                val pick = spec.ids.random()
                val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pick))
                if (item == Items.AIR) {
                    CobblemonGacha.logger.warn("RandomItem picked unknown id: {}", pick)
                    null
                } else ItemStack(item, spec.count)
            }
        }
        // CobbreedingEgg has no in-band stack representation — `grant()` handles it directly,
        // and `representative()` falls back to a display-only egg via `eggDisplayStack`.
        is ItemSpec.CobbreedingEgg -> eggDisplayStack(spec)
    }

    private data class EggOutcome(val display: ItemStack, val announceLabel: String)

    /**
     * Pick a species from the pool, dispatch `/givepokemonegg`, and return both the display stack
     * (for inventory placeholder + announce hover) and the announce label. The label includes the
     * species, shiny tag, and a `[Hidden Ability]` suffix when applicable. If the pool is unknown
     * or filtered to nothing, logs and skips (returns null).
     */
    private fun dispatchEgg(player: ServerPlayer, spec: ItemSpec.CobbreedingEgg): EggOutcome? {
        val species = CobblemonGacha.eggPools.pick(spec.pool, spec.requireHiddenAbility)
        if (species == null) {
            CobblemonGacha.logger.warn(
                "Egg pool '{}' produced no species (requireHA={}); skipping grant for {}",
                spec.pool, spec.requireHiddenAbility, player.gameProfile.name,
            )
            return null
        }
        // Build the PokemonProperties argument list. `min_perfect_ivs=2` is always present so every
        // egg hatches with two random perfect IVs. `shiny=true` and `ha=yes` are conditional.
        val args = buildList {
            add(species)
            add("min_perfect_ivs=2")
            if (spec.shiny) add("shiny=true")
            if (spec.requireHiddenAbility) add("ha=yes")
        }.joinToString(" ")
        val cmd = "givepokemonegg ${player.gameProfile.name} $args"
        val src = player.server.createCommandSourceStack()
            .withPermission(4)
            .withSuppressedOutput()
        player.server.commands.performPrefixedCommand(src, cmd)
        // Cobreeding's /givepokemonegg places the egg via inventory operations that may not be
        // synchronously visible to ItemStack.get(...). Use TickScheduler so the tag-pass runs on
        // a *later* server tick (server.execute(...) runs on the same tick — too eager).
        TickScheduler.later(2) { tagGrantedEggWithTier(player, spec.pool) }
        return EggOutcome(eggDisplayStack(spec, species), announceLabel(spec, species))
    }

    /** Public entry for callers outside the normal gacha-pull flow (e.g. `/gacha admin giveegg`).
     *  Defers the tag-write by 2 ticks so the egg has time to land in inventory after
     *  `/givepokemonegg`. Without this call, the bridge's egg-timer code skips the egg and it
     *  falls back to Cobreeding's default ~10-minute hatch. */
    fun scheduleTagGrantedEgg(player: ServerPlayer, tier: String) {
        TickScheduler.later(2) { tagGrantedEggWithTier(player, tier) }
    }

    /**
     * Stamps `cobblemongacha:tier` onto the just-created Cobreeding egg's `minecraft:custom_data`
     * so cobblemon-bridge's defeat-driven hatch can look up the per-tier threshold (5/10/15/20).
     * The egg item class FQN is `ludichat.cobbreeding.PokemonEgg`; we identify it by class name
     * rather than item ID to stay forward-compatible with Cobreeding renames. Picks the first
     * eligible egg (no tier tag yet) starting from the hotbar — should be the one we just
     * granted, since `/givepokemonegg` places into the first empty slot.
     */
    private fun tagGrantedEggWithTier(player: ServerPlayer, tier: String) {
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.isEmpty) continue
            if (stack.item.javaClass.name != "ludichat.cobbreeding.PokemonEgg") continue
            val existing = stack.get(DataComponents.CUSTOM_DATA)
            val tag: CompoundTag = existing?.copyTag() ?: CompoundTag()
            if (tag.contains("cobblemongacha_tier")) continue  // already tagged — keep looking
            tag.putString("cobblemongacha_tier", tier)
            tag.putInt("cobblemongacha_defeats_consumed", 0)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            CobblemonGacha.logger.info(
                "Tagged egg slot {} for {} with tier={}", i, player.gameProfile.name, tier,
            )
            return
        }
        CobblemonGacha.logger.warn(
            "Couldn't find newly-granted egg in {}'s inventory to tag (tier={}) — inventory may be full or the egg was placed asynchronously",
            player.gameProfile.name, tier,
        )
    }

    /**
     * Format: `[§eShiny ]§fPikachu Egg[ §d(Hidden Ability)]`. Always prefixed with two random
     * perfect IVs — but we don't surface IVs in the announce text (they're per-pull and not
     * particularly readable in chat).
     */
    private fun announceLabel(spec: ItemSpec.CobbreedingEgg, species: String): String {
        val shinyTag = if (spec.shiny) "§e✦ Shiny §f" else "§f"
        val speciesTitle = species.replaceFirstChar { it.uppercase() }
        val haTag = if (spec.requireHiddenAbility) " §d(Hidden Ability)" else ""
        return "$shinyTag$speciesTitle Egg$haTag"
    }

    private fun eggDisplayStack(spec: ItemSpec.CobbreedingEgg, species: String? = null): ItemStack {
        val stack = ItemStack(Items.EGG)
        val tierLabel = spec.pool.replace('_', ' ').replaceFirstChar { it.uppercase() }
        val shinyPrefix = if (spec.shiny) "§e✦ Shiny " else "§a"
        val base = if (species != null) {
            "$shinyPrefix${species.replaceFirstChar { it.uppercase() }} Egg"
        } else {
            "$shinyPrefix$tierLabel Pokémon Egg"
        }
        val name = if (spec.requireHiddenAbility) "$base §d(HA)" else base
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        return stack
    }
}
