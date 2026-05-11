package com.cobblemongacha.item

import com.cobblemongacha.data.KeyTier
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Builds keyed ItemStacks for the three gacha tiers. Keys are vanilla items (trial_key,
 * ominous_trial_key, nether_star) tagged with `custom_data { gacha_key = <tier.key> }` so
 * the crate interaction handler can recognise them without registering custom items.
 */
object KeyItems {

    private const val TAG_NAME = "gacha_key"

    /** Build a single Key ItemStack of [count]. */
    fun build(tier: KeyTier, count: Int = 1): ItemStack {
        val (item, displayName) = when (tier) {
            KeyTier.COMMON -> Items.TRIAL_KEY to Component.literal("§e§lCommon Key")
            KeyTier.RARE -> Items.OMINOUS_TRIAL_KEY to Component.literal("§5§lRare Key")
            KeyTier.ULTRA -> Items.NETHER_STAR to Component.literal("§6§lUltra Key")
        }
        val stack = ItemStack(item, count)
        stack.set(DataComponents.CUSTOM_NAME, displayName)
        val lore = listOf(
            Component.literal("§7Right-click the §f${tier.displayName} Crate §7at spawn"),
            Component.literal("§7to roll for a reward."),
        )
        stack.set(DataComponents.LORE, ItemLore(lore))
        val tag = CompoundTag()
        tag.putString(TAG_NAME, tier.key)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }

    /** Inverse of [build]: returns the tier encoded in the stack's custom_data, or null. */
    fun tierOf(stack: ItemStack): KeyTier? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        if (!tag.contains(TAG_NAME)) return null
        return KeyTier.fromKey(tag.getString(TAG_NAME))
    }
}
