package com.cobblemongacha.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class PlayerGachaStoreTest {

    @Test
    fun `round-trip preserves all fields`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        val data = store.getOrCreate(u, "SixthSense")
        data.lastLoginGrantDate = "2026-05-11"
        data.lastRankedGrantDate = "2026-05-10"
        store.save()

        val rehydrated = PlayerGachaStore(tmp)
        rehydrated.load()
        val loaded = rehydrated.get(u)!!
        assertEquals("SixthSense", loaded.name)
        assertEquals("2026-05-11", loaded.lastLoginGrantDate)
        assertEquals("2026-05-10", loaded.lastRankedGrantDate)
    }

    @Test
    fun `missing file is silent on load`(@TempDir tmp: Path) {
        val store = PlayerGachaStore(tmp)
        store.load()
        assertNull(store.get(UUID.randomUUID()))
    }

    @Test
    fun `getOrCreate refreshes cached name`(@TempDir tmp: Path) {
        val u = UUID.randomUUID()
        val store = PlayerGachaStore(tmp)
        store.getOrCreate(u, "OldName")
        val refreshed = store.getOrCreate(u, "NewName")
        assertEquals("NewName", refreshed.name)
    }
}
