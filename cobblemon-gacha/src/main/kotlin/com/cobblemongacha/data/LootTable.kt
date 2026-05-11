package com.cobblemongacha.data

/** Tier banner within a loot table: drives lore/announcements (e.g. "(HIGH)" tag). */
enum class LootTier { Floor, Mid, High, Jackpot }

/**
 * One materialisable item inside a `LootEntry`. Three forms (sealed):
 *   - `Vanilla` — a regular vanilla or modded item id with count and optional name/lore overrides.
 *   - `GachaKeyRef` — emit a Common/Rare/Ultra Key ItemStack (so jackpot entries can grant keys).
 *   - `Placeholder` — emit a placeholder ItemStack (Pokemon egg, voucher, TBD ultra reward).
 *
 * `RewardGranter` walks one of these into an actual `ItemStack`.
 */
sealed class ItemSpec {
    data class Vanilla(
        val id: String,
        val count: Int,
        val nameOverride: String? = null,
        val loreLines: List<String> = emptyList(),
    ) : ItemSpec()

    data class GachaKeyRef(val tier: KeyTier, val count: Int) : ItemSpec()

    /** kind: "pokemon_egg" | "voucher" | "tbd_ultra" — picks the vanilla base item. */
    data class Placeholder(val kind: String, val label: String, val count: Int) : ItemSpec()
}

/**
 * One row in a loot table. `weightPct` is the raw percentage from the CSV (before normalisation).
 * 0% entries are kept in the table but skipped by RewardRoller (used to record unfinished entries).
 * `label` is the human-readable string shown in announcements; copied verbatim from the CSV "Item" cell.
 * `items` is the list of stacks delivered if this entry is rolled (one entry may bundle several stacks).
 */
data class LootEntry(
    val lootTier: LootTier,
    val label: String,
    val weightPct: Double,
    val items: List<ItemSpec>,
    val notes: String = "",
)

/**
 * A whole loot table. `entries` preserves CSV order. `totalWeightPct` is the raw sum of `weightPct`
 * before normalisation — kept so admins editing the JSON can see if their odds drift from 100%.
 */
data class LootTable(
    val tier: KeyTier,
    val totalWeightPct: Double,
    val entries: List<LootEntry>,
)
