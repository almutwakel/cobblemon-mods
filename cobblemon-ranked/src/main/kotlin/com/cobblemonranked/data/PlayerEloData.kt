package com.cobblemonranked.data

data class PlayerEloData(
    val name: String,
    var elo: Int = 1200,
    var wins: Int = 0,
    var losses: Int = 0,
    var lastBattleDate: String? = null,
    val forceLog: MutableMap<String, String> = mutableMapOf()
)
