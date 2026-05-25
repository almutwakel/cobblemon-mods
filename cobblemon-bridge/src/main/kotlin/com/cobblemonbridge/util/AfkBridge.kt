package com.cobblemonbridge.util

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to NeoEssentials' {@code AfkManager} so we can ask "is this player AFK?"
 * without making NeoEssentials a hard compile dep. Resolves once on first call and caches the
 * method handle; subsequent calls are a single reflected invocation.
 *
 * If NeoEssentials isn't present, {@link isAfk} returns {@code false} (treat everyone as active)
 * so callers degrade gracefully — the worst case is that AFK players still progress timers,
 * which is the same behavior as before this bridge existed.
 */
object AfkBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/afk")
    private const val AFK_MANAGER_CLASS = "com.zerog.neoessentials.chat.AfkManager"

    @Volatile private var resolved: Boolean = false
    @Volatile private var unavailable: Boolean = false

    private var managerInstance: Any? = null
    private var isAfkMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                val cls = Class.forName(AFK_MANAGER_CLASS)
                managerInstance = cls.getMethod("getInstance").invoke(null)
                isAfkMethod = cls.getMethod("isAfk", UUID::class.java)
                resolved = true
                unavailable = false
                log.info("NeoEssentials AfkManager bridge resolved")
                return true
            } catch (e: ClassNotFoundException) {
                warnOnce("NeoEssentials not loaded — AFK exclusion disabled (everyone treated as active)")
            } catch (e: Throwable) {
                warnOnce("AfkManager reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    /** Returns true if the player is currently flagged AFK by NeoEssentials. Defaults to false
     *  when the bridge can't resolve (NeoEssentials missing). */
    fun isAfk(uuid: UUID): Boolean {
        if (!resolve()) return false
        return try {
            isAfkMethod!!.invoke(managerInstance, uuid) as Boolean
        } catch (t: Throwable) {
            warnOnce("AfkManager.isAfk invocation failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
