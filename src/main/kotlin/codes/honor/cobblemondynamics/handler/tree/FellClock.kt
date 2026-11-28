package codes.honor.cobblemondynamics.handler.tree

import kotlin.math.roundToInt

/**
 * When a Lumberjack fells its tree (ADR-0017, decision 6): once for each start of its work, T ticks of Working for each log after the
 * start (CD-LUM-11). A refused fell gives the tree no demand for this worker for 6,000 ticks (CD-LUM-13). The claim memo keeps one clock.
 */
class FellClock {
    private var dueAt: Long? = null
    private var refusedUntil = Long.MIN_VALUE

    /** The work starts at [now] on a tree of [logs] logs, at [ticksPerLog] ticks a log. */
    fun start(now: Long, ticksPerLog: Int, logs: Int) {
        dueAt = now + ticksPerLog.coerceAtLeast(1).toLong() * logs.coerceAtLeast(1)
    }

    /**
     * True on the first tick of Working at or after the tick of the fell, and only once for each start, so the fell never comes twice. A
     * worker that waited through that tick fells on its next tick of Working.
     */
    fun isFellTick(now: Long): Boolean {
        val due = dueAt ?: return false
        if (now < due) return false
        dueAt = null
        return true
    }

    /** CD-LUM-13: the claim checks refused the fell at [now]. [stalled] is the work context's stall of the target for this worker. */
    fun refuse(now: Long, stalled: (Long) -> Unit) {
        dueAt = null
        refusedUntil = now + REFUSED_WAIT_TICKS
        stalled(REFUSED_WAIT_TICKS)
    }

    val isCountingDown: Boolean get() = dueAt != null

    fun isRefused(now: Long): Boolean = now < refusedUntil

    companion object {
        /** As a refused harvest waits (CD-HRV-13). */
        const val REFUSED_WAIT_TICKS = 6_000L

        /**
         * CD-LUM-11: T = round(A + (B - A) × (L - 1) ÷ 99) for a worker of level L, from `ticks_per_log_at_level_1` (A) to
         * `ticks_per_log_at_level_100` (B). The level is clamped to 1 to 100, and T is at least 1.
         */
        fun ticksPerLog(level: Int, params: LumberjackParams, multiplier: Double = 1.0): Int {
            val a = params.ticksPerLogAt1
            val b = params.ticksPerLogAt100
            val base = (a + (b - a) * (level.coerceIn(1, 100) - 1) / 99.0).roundToInt().coerceAtLeast(1)
            return if (multiplier <= 0.0 || multiplier == 1.0) base else maxOf(1, (base / multiplier).roundToInt())
        }
    }
}
