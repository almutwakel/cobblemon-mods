package com.cobblemonbridge.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BridgeTagsTest {

    @Test
    fun `parseAdjustLevel reads numeric suffix`() {
        assertEquals(50, BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.50"))
        assertEquals(1, BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.1"))
        assertEquals(100, BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.100"))
    }

    @Test
    fun `parseAdjustLevel rejects missing or empty suffix`() {
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level"))
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level."))
    }

    @Test
    fun `parseAdjustLevel rejects non-numeric suffix`() {
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.foo"))
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.50.5"))
    }

    @Test
    fun `parseAdjustLevel rejects out-of-range`() {
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.0"))
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.-5"))
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.adjust_level.101"))
    }

    @Test
    fun `parseAdjustLevel rejects wrong namespace`() {
        assertNull(BridgeTags.parseAdjustLevel("other.adjust_level.50"))
        assertNull(BridgeTags.parseAdjustLevel("adjust_level.50"))
    }

    @Test
    fun `findAdjustLevel returns first valid tag`() {
        val tags = listOf(
            "minecraft.something",
            "cobblemon_bridge.adjust_level.42",
            "cobblemon_bridge.adjust_level.99",
        )
        assertEquals(42, BridgeTags.findAdjustLevel(tags))
    }

    @Test
    fun `findAdjustLevel skips invalid tags`() {
        val tags = listOf(
            "cobblemon_bridge.adjust_level.foo",
            "cobblemon_bridge.adjust_level.",
            "cobblemon_bridge.adjust_level.30",
        )
        assertEquals(30, BridgeTags.findAdjustLevel(tags))
    }

    @Test
    fun `findAdjustLevel returns null when no tags match`() {
        assertNull(BridgeTags.findAdjustLevel(listOf("minecraft.a", "other.b")))
        assertNull(BridgeTags.findAdjustLevel(emptyList()))
    }

    @Test
    fun `parseGivePartyExp reads numeric suffix`() {
        assertEquals(100, BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.100"))
        assertEquals(3000, BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.3000"))
        assertEquals(1_000_000, BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.1000000"))
    }

    @Test
    fun `parseGivePartyExp rejects malformed and out-of-range`() {
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp"))
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp."))
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.0"))
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.-1"))
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.2000000"))
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.give_party_exp.abc"))
        assertNull(BridgeTags.parseGivePartyExp("other.give_party_exp.100"))
    }

    @Test
    fun `findGivePartyExp returns first valid tag`() {
        val tags = listOf(
            "minecraft.something",
            "cobblemon_bridge.adjust_level.50",
            "cobblemon_bridge.give_party_exp.800",
        )
        assertEquals(800, BridgeTags.findGivePartyExp(tags))
    }

    @Test
    fun `adjust_level and give_party_exp parsers don't cross-match`() {
        assertNull(BridgeTags.parseAdjustLevel("cobblemon_bridge.give_party_exp.50"))
        assertNull(BridgeTags.parseGivePartyExp("cobblemon_bridge.adjust_level.50"))
    }

    @Test
    fun `parseGymId reads numeric suffix in range`() {
        assertEquals(1, BridgeTags.parseGymId("cobblemon_bridge.gym_id.1"))
        assertEquals(8, BridgeTags.parseGymId("cobblemon_bridge.gym_id.8"))
        assertEquals(30, BridgeTags.parseGymId("cobblemon_bridge.gym_id.30"))
    }

    @Test
    fun `parseGymId rejects out-of-range and malformed`() {
        assertNull(BridgeTags.parseGymId("cobblemon_bridge.gym_id.0"))
        assertNull(BridgeTags.parseGymId("cobblemon_bridge.gym_id.31"))
        assertNull(BridgeTags.parseGymId("cobblemon_bridge.gym_id.foo"))
        assertNull(BridgeTags.parseGymId("cobblemon_bridge.gym_id"))
        assertNull(BridgeTags.parseGymId("other.gym_id.1"))
    }

    @Test
    fun `findGymId returns first valid tag`() {
        val tags = listOf("minecraft.misc", "cobblemon_bridge.gym_id.3", "cobblemon_bridge.adjust_level.50")
        assertEquals(3, BridgeTags.findGymId(tags))
    }
}
