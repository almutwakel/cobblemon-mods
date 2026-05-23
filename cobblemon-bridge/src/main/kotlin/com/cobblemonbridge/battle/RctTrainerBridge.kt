package com.cobblemonbridge.battle

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to RCTmod's `TrainerMob.startBattleWith(player)` — lets us kick off a
 * trainer battle programmatically (used by the E4 gauntlet to chain consecutive fights).
 *
 * RCTmod isn't a compile-time dep, so we resolve the class + method lazily and degrade
 * silently when the mod is absent.
 */
object RctTrainerBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/rctmod")
    private const val TRAINER_MOB_CLASS = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob"

    @Volatile private var resolved: Boolean = false
    @Volatile private var unavailable: Boolean = false
    private var trainerMobClass: Class<*>? = null
    private var startBattleWith: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                trainerMobClass = Class.forName(TRAINER_MOB_CLASS)
                startBattleWith = trainerMobClass!!.getMethod("startBattleWith", Player::class.java)
                resolved = true
                unavailable = false
                log.info("RCTmod bridge resolved — TrainerMob.startBattleWith ready")
                return true
            } catch (e: ClassNotFoundException) {
                warnOnce("RCTmod not loaded — E4 gauntlet auto-start disabled")
            } catch (e: Throwable) {
                warnOnce("RCTmod reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    fun available(): Boolean = resolve()

    fun isTrainerMob(entity: Entity): Boolean {
        if (!resolve()) return false
        return trainerMobClass!!.isInstance(entity)
    }

    fun startBattleWith(trainer: Entity, player: Player): Boolean {
        if (!resolve()) return false
        if (!trainerMobClass!!.isInstance(trainer)) return false
        return try {
            startBattleWith!!.invoke(trainer, player)
            true
        } catch (e: Throwable) {
            log.error("startBattleWith failed", e)
            false
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
