package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.engine.WorkerStatus
import kotlin.math.abs

/** When a worker's `stamina_view` changes, which sends it to clients. */
object ViewSync {
    /** One point, read back through one-tick rounding error, still counts as one point. */
    private const val STEP = 1.0 - 1e-9

    /** CD-STA-15: a change of 1 point or more since the last sync, or any state change. */
    fun shouldSync(lastSyncedPercent: Double, nowPercent: Double, lastState: WorkerStatus, nowState: WorkerStatus): Boolean =
        lastState != nowState || abs(nowPercent - lastSyncedPercent) >= STEP
}

/** When the server sends an open pasture panel its Work Stamina values. */
object PanelCadence {
    const val INTERVAL_TICKS = 20L

    /** CD-UI-05: on the tick that the panel opened and every 20 ticks after it. */
    fun shouldSend(openedAt: Long, now: Long): Boolean = now >= openedAt && (now - openedAt) % INTERVAL_TICKS == 0L
}
