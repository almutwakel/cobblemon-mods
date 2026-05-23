package com.cobblemonbridge.eggs

import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge into Cobreeding 2.2.1 (`ludichat.cobbreeding.PokemonEgg`) for reading +
 * writing the egg's `TIMER` and `POKEMON_PROPERTIES` data components. We use reflection so
 * cobblemon-bridge compiles without a hard dep on Cobreeding.
 *
 * The Companion's getter methods (`getTIMER()`, `getSECOND()`, `getPOKEMON_PROPERTIES()`) each
 * return a `DataComponentType<T>` instance — once resolved, we cache it and read/write via
 * `ItemStack.get(...)` / `ItemStack.set(...)` directly. No further reflection per operation.
 */
object CobreedingBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/cobreeding")
    private const val EGG_CLASS = "ludichat.cobbreeding.PokemonEgg"
    private const val COMPANION_CLASS = "ludichat.cobbreeding.PokemonEgg\$Companion"

    @Volatile private var resolved: Boolean = false
    @Volatile private var unavailable: Boolean = false

    private var timerComponent: DataComponentType<Int>? = null
    private var pokemonPropertiesComponent: DataComponentType<String>? = null
    private var eggClass: Class<*>? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                eggClass = Class.forName(EGG_CLASS)
                val companion = Class.forName(COMPANION_CLASS)
                val instance = eggClass!!.getField("Companion").get(null)
                @Suppress("UNCHECKED_CAST")
                timerComponent = companion.getMethod("getTIMER").invoke(instance) as DataComponentType<Int>
                @Suppress("UNCHECKED_CAST")
                pokemonPropertiesComponent = companion.getMethod("getPOKEMON_PROPERTIES")
                    .invoke(instance) as DataComponentType<String>
                resolved = true
                unavailable = false
                log.info("Cobreeding bridge resolved — TIMER + POKEMON_PROPERTIES components ready")
                return true
            } catch (e: ClassNotFoundException) {
                warnOnce("Cobreeding not loaded — egg-by-defeat hatching disabled")
            } catch (e: Throwable) {
                warnOnce("Cobreeding reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    fun available(): Boolean = resolve()

    fun isPokemonEgg(stack: ItemStack): Boolean {
        if (!resolve() || stack.isEmpty) return false
        return eggClass!!.isInstance(stack.item)
    }

    fun getTimer(stack: ItemStack): Int? {
        if (!resolve()) return null
        return stack.get(timerComponent!!)
    }

    fun setTimer(stack: ItemStack, ticks: Int) {
        if (!resolve()) return
        stack.set(timerComponent!!, ticks)
    }

    /** Returns the encrypted PokemonProperties string. Decoding would need Cobreeding's
     *  EggUtilities.decrypt — for chat display we just parse the leading `species=foo` token. */
    fun getPokemonPropertiesRaw(stack: ItemStack): String? {
        if (!resolve()) return null
        return stack.get(pokemonPropertiesComponent!!)
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
