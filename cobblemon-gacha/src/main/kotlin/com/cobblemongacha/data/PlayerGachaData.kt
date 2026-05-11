package com.cobblemongacha.data

/**
 * Per-player gacha record. `lastLoginGrantDate` and `lastRankedGrantDate` are `LocalDate.toString()`
 * values (yyyy-MM-dd) so each is a single field and they compare with `!=`. `null` means the player
 * has never received that grant.
 */
data class PlayerGachaData(
    var name: String,
    var lastLoginGrantDate: String? = null,
    var lastRankedGrantDate: String? = null,
)
