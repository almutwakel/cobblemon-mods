package com.cobblemonmarket.economy

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to Cobblemon Economy 0.0.17 (Fabric mod loaded under Sinytra Connector).
 *
 * The class FQN starts with `com.cobblemon.economy.fabric.` even on NeoForge — Connector
 * preserves the Fabric package layout when translating. `getEconomyManager()` is a public
 * static method, so we don't need any loader-specific entrypoint lookup.
 *
 * Method handles cache after first successful resolution; failures degrade silently to
 * no-ops with a single warning so the rest of the mod keeps running when CE is absent.
 */
object EconomyBridge {

    private val log = LoggerFactory.getLogger("cobblemon-market/economy")
    private const val ECONOMY_CLASS = "com.cobblemon.economy.fabric.CobblemonEconomy"
    private const val M_GET_BALANCE = "getBalance"
    private const val M_ADD_BALANCE = "addBalance"
    private const val M_SUB_BALANCE = "subtractBalance"

    @Volatile private var resolvedManager: Any? = null
    @Volatile private var getBalanceMethod: Method? = null
    @Volatile private var addBalanceMethod: Method? = null
    @Volatile private var subBalanceMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)
    private val available = AtomicBoolean(false)

    private fun manager(): Any? {
        resolvedManager?.let { return it }
        return try {
            val cls = Class.forName(ECONOMY_CLASS)
            val mgr = cls.getMethod("getEconomyManager").invoke(null)
            resolvedManager = mgr
            getBalanceMethod = mgr.javaClass.getMethod(M_GET_BALANCE, UUID::class.java)
            addBalanceMethod = mgr.javaClass.getMethod(M_ADD_BALANCE, UUID::class.java, BigDecimal::class.java)
            subBalanceMethod = mgr.javaClass.getMethod(M_SUB_BALANCE, UUID::class.java, BigDecimal::class.java)
            available.set(true)
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
        log.error("EconomyBridge.getBalance failed", e); 0
    }

    fun deposit(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        try {
            val mgr = manager() ?: return
            addBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount))
        } catch (e: Throwable) {
            log.error("EconomyBridge.deposit failed", e)
        }
    }

    fun withdraw(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            subBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount)) as Boolean
        } catch (e: Throwable) {
            log.error("EconomyBridge.withdraw failed", e); false
        }
    }

    /**
     * Returns the top [limit] CE balances as `(uuid, balance)` pairs, sorted by balance
     * descending. Pulls directly from `EconomyManager.getTopBalance(int)`, so this
     * includes offline players whose balances exist in CE's persistence.
     *
     * Returns an empty list when CE is not loaded.
     */
    fun getTopBalance(limit: Int): List<Pair<UUID, Int>> = try {
        val mgr = manager() ?: return emptyList()
        val method = mgr.javaClass.getMethod("getTopBalance", Int::class.javaPrimitiveType)
        @Suppress("UNCHECKED_CAST")
        val raw = method.invoke(mgr, limit) as List<Map.Entry<UUID, BigDecimal>>
        raw.map { it.key to it.value.toInt() }
    } catch (e: Throwable) {
        log.error("EconomyBridge.getTopBalance failed", e); emptyList()
    }

    fun isAvailable(): Boolean = available.get()

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) {
            log.warn(msg)
        }
    }
}
