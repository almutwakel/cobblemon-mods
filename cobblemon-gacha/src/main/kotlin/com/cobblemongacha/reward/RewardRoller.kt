package com.cobblemongacha.reward

import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import kotlin.random.Random

/**
 * Stateless weighted picker. Filters out 0-weight entries (which the loot table keeps for record
 * purposes), then picks one entry proportionally to `weightPct`. Random is injected so tests can
 * seed it for determinism.
 */
object RewardRoller {

    fun roll(table: LootTable, random: Random = Random.Default): LootEntry {
        val candidates = table.entries.filter { it.weightPct > 0.0 }
        check(candidates.isNotEmpty()) {
            "Loot table for ${table.tier.key} has no positive-weight entries — refusing to roll"
        }
        val total = candidates.sumOf { it.weightPct }
        val r = random.nextDouble() * total
        var acc = 0.0
        for (entry in candidates) {
            acc += entry.weightPct
            if (r < acc) return entry
        }
        return candidates.last()
    }
}
