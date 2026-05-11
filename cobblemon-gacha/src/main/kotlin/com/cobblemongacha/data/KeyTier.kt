package com.cobblemongacha.data

/**
 * Three lootbox tiers. Each tier has a Common/Rare/Ultra key (vanilla item + custom_data tag)
 * and one configured crate coord. The order matters: higher ordinal = rarer.
 */
enum class KeyTier(val key: String, val displayName: String) {
    COMMON("common", "Common"),
    RARE("rare", "Rare"),
    ULTRA("ultra", "Ultra");

    companion object {
        fun fromKey(k: String): KeyTier? = entries.firstOrNull { it.key == k.lowercase() }
    }
}
