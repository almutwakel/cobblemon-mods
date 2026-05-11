package com.cobblemongacha.util

import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple delayed-runnable scheduler. Hooked from `CobblemonGacha.onServerTickPost` once on
 * startup; tasks are evaluated each server tick (50 ms at 20 TPS).
 *
 * Tasks may cancel via the returned `Cancellable` — useful when the player closes the rolling
 * menu early and we want to drop any pending animation updates.
 */
object TickScheduler {

    fun interface Cancellable { fun cancel() }
    private data class Task(val dueTick: Long, val run: () -> Unit, var cancelled: Boolean = false)

    private val tasks = ConcurrentLinkedQueue<Task>()
    private val tickCounter = AtomicLong(0)

    /** Schedule [run] to fire after [ticks] server ticks. */
    fun later(ticks: Int, run: () -> Unit): Cancellable {
        val task = Task(tickCounter.get() + ticks.toLong(), run)
        tasks.add(task)
        return Cancellable { task.cancelled = true }
    }

    /** Schedule a series of tasks at cumulative intervals. */
    fun chain(intervals: List<Int>, stepRun: (index: Int) -> Unit, finalRun: () -> Unit): Cancellable {
        var running = true
        var cumulative = 0
        val cancels = mutableListOf<Cancellable>()
        for ((i, interval) in intervals.withIndex()) {
            cumulative += interval
            cancels.add(later(cumulative) { if (running) stepRun(i) })
        }
        cancels.add(later(cumulative) { if (running) finalRun() })
        return Cancellable {
            running = false
            cancels.forEach { it.cancel() }
        }
    }

    /** Called by the mod entry once per server tick. */
    fun onServerTickPost(@Suppress("UNUSED_PARAMETER") event: ServerTickEvent.Post) {
        val now = tickCounter.incrementAndGet()
        val iter = tasks.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            if (t.dueTick <= now) {
                iter.remove()
                if (!t.cancelled) {
                    try { t.run() } catch (e: Throwable) {
                        org.slf4j.LoggerFactory.getLogger("cobblemon-gacha/tick").error("TickScheduler task threw", e)
                    }
                }
            }
        }
    }
}
