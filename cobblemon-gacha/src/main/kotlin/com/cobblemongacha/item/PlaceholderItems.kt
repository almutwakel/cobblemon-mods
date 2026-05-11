package com.cobblemongacha.item

import com.cobblemongacha.data.ItemSpec
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/**
 * Builds placeholder ItemStacks for unimplemented gacha rewards (Pokemon eggs, vouchers,
 * blank Ultra rows). The base vanilla item is chosen by [ItemSpec.Placeholder.kind]:
 *   - "pokemon_egg" → minecraft:egg
 *   - "voucher"     → minecraft:filled_map
 *   - "tbd_ultra"   → minecraft:knowledge_book (anything else)
 *
 * The stack is tagged `custom_data { gacha_placeholder=true, placeholder_id=<kind>:<label> }`
 * so a future `migratePlaceholders` command can swap them for real items.
 */
object PlaceholderItems {

    fun build(spec: ItemSpec.Placeholder): ItemStack {
        val (item, color) = when (spec.kind) {
            "pokemon_egg" -> Items.EGG to "§a"
            "voucher" -> Items.FILLED_MAP to "§6"
            else -> Items.KNOWLEDGE_BOOK to "§7"
        }
        val stack = ItemStack(item, spec.count)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("$color${spec.label} §8(Placeholder)"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§8Stand-in until the real item ships."),
            Component.literal("§8Admins can swap via /gacha admin migratePlaceholders (future)."),
        )))
        val tag = CompoundTag().apply {
            putBoolean("gacha_placeholder", true)
            putString("placeholder_id", "${spec.kind}:${spec.label}")
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }
}
