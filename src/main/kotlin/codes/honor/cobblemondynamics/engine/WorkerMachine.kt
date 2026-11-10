package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.bed.BedCandidate
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

    /** A Tired worker walks to [bed], which the engine claimed for it. */
    data class ToBed(val bed: BedCandidate) : MachineEvent

    /** A Tired worker with no free bed rests where it stands. */
    data object RestInPlace : MachineEvent

    /** A worker in ToBed reached its bed. The engine lays it on the sleep point. */
    data object FellAsleep : MachineEvent

    /** The bed is gone, its claim is gone, or the worker did not reach it in time. The engine releases the bed claim. */
    data object LostBed : MachineEvent

    /** A Tired worker reached 100%. [toBinding] is true for a leashed worker, whose bound target the engine claims at once. */
    data class Rested(val toBinding: Boolean) : MachineEvent

    /** A healer reached its ally. The engine applies the heal and releases the heal claim. */
    data object Healed : MachineEvent

    /** The heal ends at no cost: the ally stopped needing it, or the healer did not reach it in time ([timedOut]). */
    data class HealCancelled(val timedOut: Boolean) : MachineEvent
}

/** The states of one worker, on the tick that the caller passes in. */
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
        w.bed = null
    }

    fun canMoveFromStandby(w: Worker, now: Long): Boolean =
        !w.leashed && w.status == WorkerStatus.STANDBY && !w.missedArrival && now - w.statusSince >= settings.standbyMoveTicks

    /**
     * A Tired worker, after the engine released its work claim (CD-STA-13), walks to [bed], on which the engine took a bed claim
     * (CD-BED-05), or rests where it stands when no bed is free (CD-BED-08).
     */
    fun onTired(w: Worker, now: Long, bed: BedCandidate?): MachineEvent {
        w.claimTick = if (bed == null) -1 else now
        w.retryAt = -1
        w.missedArrival = false
        w.bed = bed
        if (bed == null) {
            set(w, WorkerStatus.RESTING_IN_PLACE, now)
            return MachineEvent.RestInPlace
        }
        set(w, WorkerStatus.TO_BED, now)
        return MachineEvent.ToBed(bed)
    }

    /**
     * One tick of a Tired worker. [atBed] is true when it has reached its bed's sleep point, and [bedPresent] while the bed stands and
     * the worker still holds its claim on it. A bed that is not reached `travelTimeoutTicks` after the claim, or that breaks, leaves
     * the worker resting in place (CD-BED-11).
     */
    fun onRestTick(w: Worker, now: Long, atBed: Boolean, bedPresent: Boolean): MachineEvent? = when (w.status) {
        WorkerStatus.TO_BED -> when {
            !bedPresent -> loseBed(w, now)

            atBed -> {
                set(w, WorkerStatus.SLEEPING, now)
                MachineEvent.FellAsleep
            }

            now - w.claimTick >= settings.travelTimeoutTicks -> loseBed(w, now)

            else -> null
        }

        WorkerStatus.SLEEPING -> if (bedPresent) null else loseBed(w, now)

        else -> null
    }

    /** At 100%, Tired clears: the worker leaves its bed and is Idle (CD-BED-12), and a leashed one returns to its binding (CD-STA-14). */
    fun onRested(w: Worker, now: Long): MachineEvent {
        onRelease(w, now)
        return MachineEvent.Rested(toBinding = w.leashed)
    }

    /** A healer took a heal claim on an ally and walks to it (CD-HEAL-01). */
    fun onHeal(w: Worker, now: Long) {
        set(w, WorkerStatus.HEALING, now)
        w.claimTick = now
        w.retryAt = -1
        w.missedArrival = false
    }

    /**
     * One tick of a healer. [qualifies] is false once the ally stops needing the heal, or the healer stops being one, which cancels
     * the heal at no cost (CD-HEAL-11). [inRange] completes it (CD-HEAL-10). An ally not reached `travelTimeoutTicks` after the claim
     * cancels it too. Each end leaves the healer Idle.
     */
    fun onHealTick(w: Worker, now: Long, qualifies: Boolean, inRange: Boolean): MachineEvent? {
        if (w.status != WorkerStatus.HEALING) return null
        val event = when {
            !qualifies -> MachineEvent.HealCancelled(timedOut = false)
            inRange -> MachineEvent.Healed
            now - w.claimTick >= settings.travelTimeoutTicks -> MachineEvent.HealCancelled(timedOut = true)
            else -> return null
        }
        onRelease(w, now)
        return event
    }

    private fun loseBed(w: Worker, now: Long): MachineEvent {
        w.bed = null
        w.claimTick = -1
        set(w, WorkerStatus.RESTING_IN_PLACE, now)
        return MachineEvent.LostBed
    }

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
