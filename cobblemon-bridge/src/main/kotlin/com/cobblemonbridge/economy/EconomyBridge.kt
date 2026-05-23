package com.cobblemonbridge.economy

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to Cobblemon Economy 0.0.17 (Fabric, loaded via Sinytra Connector).
 * Same pattern as `cobblemon-market`'s and `cobblemon-carrots`' EconomyBridge — kept local so
 * cobblemon-bridge stays independent of those mods at compile time.
 */
object EconomyBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/economy")
    private const val ECONOMY_CLASS = "com.cobblemon.economy.fabric.CobblemonEconomy"

    @Volatile private var resolvedManager: Any? = null
    @Volatile private var getBalanceMethod: Method? = null
    @Volatile private var addBalanceMethod: Method? = null
    @Volatile private var subBalanceMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun manager(): Any? {
        resolvedManager?.let { return it }
        return try {
            val cls = Class.forName(ECONOMY_CLASS)
            val mgr = cls.getMethod("getEconomyManager").invoke(null)
            resolvedManager = mgr
            getBalanceMethod = mgr.javaClass.getMethod("getBalance", UUID::class.java)
            addBalanceMethod = mgr.javaClass.getMethod("addBalance", UUID::class.java, BigDecimal::class.java)
            subBalanceMethod = mgr.javaClass.getMethod("subtractBalance", UUID::class.java, BigDecimal::class.java)
            mgr
        } catch (e: ClassNotFoundException) {
            warnOnce("Cobblemon Economy not loaded — currency operations disabled")
            null
        } catch (e: Throwable) {
            warnOnce("Cobblemon Economy reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun getBalance(uuid: UUID): Int = try {
        val mgr = manager() ?: return 0
        (getBalanceMethod!!.invoke(mgr, uuid) as BigDecimal).toInt()
    } catch (e: Throwable) {
        log.error("getBalance failed", e); 0
    }

    fun deposit(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        try {
            val mgr = manager() ?: return
            addBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount))
        } catch (e: Throwable) {
            log.error("deposit failed", e)
        }
    }

    fun withdraw(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            subBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount)) as Boolean
        } catch (e: Throwable) {
            log.error("withdraw failed", e); false
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
