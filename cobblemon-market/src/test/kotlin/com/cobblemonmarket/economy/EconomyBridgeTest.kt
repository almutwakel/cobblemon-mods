package com.cobblemonmarket.economy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import java.util.UUID

class EconomyBridgeTest {

    @Test
    fun `getBalance returns zero when economy class is not on classpath`() {
        assertEquals(0, EconomyBridge.getBalance(UUID.randomUUID()))
    }

    @Test
    fun `withdraw returns false when economy class is not on classpath`() {
        assertFalse(EconomyBridge.withdraw(UUID.randomUUID(), 100))
    }

    @Test
    fun `deposit is a no-op when economy class is not on classpath`() {
        EconomyBridge.deposit(UUID.randomUUID(), 100)
    }

    @Test
    fun `isAvailable is false until a successful call has occurred`() {
        assertFalse(EconomyBridge.isAvailable())
    }
}
