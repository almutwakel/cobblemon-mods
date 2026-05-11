package com.cobblemongacha.reward

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.data.ItemSpec
import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.item.PlaceholderItems
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * Materialises a `LootEntry`'s `ItemSpec`s into `ItemStack`s and inserts them into the player's
 * inventory. If the inventory cannot hold a stack, the stack is dropped as an `ItemEntity` at the
 * player's feet. Returns the materialised stacks so callers (announcer) can describe what was given.
 */
object RewardGranter {

    fun grant(player: ServerPlayer, entry: LootEntry): List<ItemStack> {
        val stacks = entry.items.mapNotNull { materialize(it) }
        for (stack in stacks) {
            if (stack.isEmpty) continue
            if (!player.inventory.add(stack)) {
                val drop = ItemEntity(player.serverLevel(), player.x, player.y, player.z, stack)
                drop.setDefaultPickUpDelay()
                player.serverLevel().addFreshEntity(drop)
            }
        }
        return stacks
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
            if (item == net.minecraft.world.item.Items.AIR) {
                CobblemonGacha.logger.warn("Unknown item id in loot table: {}", spec.id)
                null
            } else ItemStack(item, spec.count)
        }
        is ItemSpec.GachaKeyRef -> KeyItems.build(spec.tier, spec.count)
        is ItemSpec.Placeholder -> PlaceholderItems.build(spec)
    }
}
