package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings

sealed interface MachineEvent {
    data object StartedWorking : MachineEvent

    data object EnteredStandby : MachineEvent

    /** An auto worker's travel timed out. The engine releases the claim and skips [skip] until [until]. */
    data class ReleaseClaim(val skip: TargetKey?, val until: Long) : MachineEvent

    /** A leashed worker's travel timed out. The engine messages the owner. */
    data object MissedArrival : MachineEvent

    /** A leashed worker's wait after a missed arrival is over, and it travels again. */
    data object Retry : MachineEvent
}

/** The work-claim states of one worker, on the tick that the caller passes in. */
class WorkerMachine(private val settings: EngineSettings) {
    fun onClaim(w: Worker, now: Long) {
        set(w, WorkerStatus.TRAVELING, now)
        w.claimTick = now
        w.retryAt = -1
        w.missedArrival = false
    }

    /** Applies the first rule that matches the worker's status and returns at most one event. */
    fun onTick(w: Worker, now: Long, arrived: Boolean, demand: () -> Boolean, key: TargetKey?): MachineEvent? = when (w.status) {
        WorkerStatus.TRAVELING -> when {
            arrived -> if (demand()) startWorking(w, now) else enterStandby(w, now)
            now - w.claimTick >= settings.travelTimeoutTicks -> timeOut(w, now, key)
            else -> null
        }

        WorkerStatus.STANDBY -> when {
            w.missedArrival -> if (now >= w.retryAt) retry(w, now) else null

            // CD-ASG-12: a waiting worker checks for new demand every 20 ticks of Standby.
            (now - w.statusSince) % DEMAND_CHECK_TICKS == 0L && demand() -> startWorking(w, now)

            else -> null
        }

        WorkerStatus.WORKING -> if (demand()) null else enterStandby(w, now)

        else -> null
    }

    fun onRelease(w: Worker, now: Long) {
        set(w, WorkerStatus.IDLE, now)
        w.claimTick = -1
        w.retryAt = -1
        w.missedArrival = false
    }

    fun canMoveFromStandby(w: Worker, now: Long): Boolean =
        !w.leashed && w.status == WorkerStatus.STANDBY && !w.missedArrival && now - w.statusSince >= settings.standbyMoveTicks

    private fun startWorking(w: Worker, now: Long): MachineEvent {
        set(w, WorkerStatus.WORKING, now)
        return MachineEvent.StartedWorking
    }

    private fun enterStandby(w: Worker, now: Long): MachineEvent {
        set(w, WorkerStatus.STANDBY, now)
        return MachineEvent.EnteredStandby
    }

    private fun timeOut(w: Worker, now: Long, key: TargetKey?): MachineEvent {
        if (!w.leashed) {
            onRelease(w, now)
            return MachineEvent.ReleaseClaim(key, now + settings.retryWaitTicks)
        }
        // CD-ASG-17: a leashed worker keeps its binding and claim, and waits in Standby before it travels again.
        set(w, WorkerStatus.STANDBY, now)
        w.missedArrival = true
        w.retryAt = w.claimTick + settings.travelTimeoutTicks + settings.retryWaitTicks
        return MachineEvent.MissedArrival
    }

    // A retry is a fresh claim: it clears missedArrival, or a worker that then arrives to no demand would retry every tick.
    private fun retry(w: Worker, now: Long): MachineEvent {
        onClaim(w, now)
        return MachineEvent.Retry
    }

    private fun set(w: Worker, status: WorkerStatus, now: Long) {
        w.status = status
        w.statusSince = now
    }

    private companion object {
        const val DEMAND_CHECK_TICKS = 20L
    }
}
