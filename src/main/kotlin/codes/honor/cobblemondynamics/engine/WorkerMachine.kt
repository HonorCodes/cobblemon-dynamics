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

    /** A Slacking worker's break ended, and it travels back to revalidate its retained claim (CD-PRG-16). */
    data object SlackEnded : MachineEvent
}

/** The states of one worker, on the tick that the caller passes in. */
class WorkerMachine(private val settings: EngineSettings) {
    fun onSlack(w: Worker, now: Long) {
        if (w.status == WorkerStatus.WORKING) set(w, WorkerStatus.SLACKING, now)
    }

    fun onClaim(w: Worker, now: Long, timeoutTicks: Int = settings.travelTimeoutTicks) {
        set(w, WorkerStatus.TRAVELING, now)
        w.claimTick = now
        w.travelTimeoutTicks = timeoutTicks
        w.retryAt = -1
        w.missedArrival = false
    }

    fun onTick(
        w: Worker,
        now: Long,
        arrived: Boolean,
        demand: () -> Boolean,
        key: TargetKey?,
        releasesWhenDone: Boolean = false,
    ): MachineEvent? {
        val release = releasesWhenDone && !w.leashed && w.pasture != null
        val timeout = if (w.travelTimeoutTicks > 0) w.travelTimeoutTicks else settings.travelTimeoutTicks
        return when (w.status) {
            WorkerStatus.TRAVELING -> when {
                arrived -> {
                    w.recordArrival(key)
                    when {
                        demand() -> startWorking(w, now)
                        release -> done(w, now)
                        else -> enterStandby(w, now)
                    }
                }

                now - w.claimTick >= timeout -> timeOut(w, now, key)

                else -> null
            }

            WorkerStatus.STANDBY -> when {
                w.missedArrival -> when {
                    arrived -> {
                        w.missedArrival = false
                        w.retryAt = -1
                        w.recordArrival(key)
                        when {
                            demand() -> startWorking(w, now)

                            release -> done(w, now)

                            else -> {
                                w.statusSince = now
                                null
                            }
                        }
                    }

                    now >= w.retryAt -> retry(w, now)

                    else -> null
                }

                (now - w.statusSince) % DEMAND_CHECK_TICKS == 0L -> when {
                    demand() -> startWorking(w, now)
                    release -> done(w, now)
                    else -> null
                }

                else -> null
            }

            WorkerStatus.WORKING -> when {
                demand() -> null
                release -> done(w, now)
                else -> enterStandby(w, now)
            }

            WorkerStatus.SLACKING -> if (now - w.statusSince >= settings.slackDurationTicks) {
                onClaim(w, now, if (w.travelTimeoutTicks > 0) w.travelTimeoutTicks else settings.travelTimeoutTicks)
                MachineEvent.SlackEnded
            } else {
                null
            }

            else -> null
        }
    }

    fun onRelease(w: Worker, now: Long) {
        set(w, WorkerStatus.IDLE, now)
        w.claimTick = -1
        w.travelTimeoutTicks = 0
        w.retryAt = -1
        w.missedArrival = false
        w.bed = null
    }

    fun canMoveFromStandby(w: Worker, now: Long): Boolean =
        !w.leashed && w.status == WorkerStatus.STANDBY && !w.missedArrival && now - w.statusSince >= settings.standbyMoveTicks

    fun onTired(w: Worker, now: Long, bed: BedCandidate?, timeoutTicks: Int = settings.travelTimeoutTicks): MachineEvent {
        w.claimTick = if (bed == null) -1 else now
        w.travelTimeoutTicks = if (bed == null) 0 else timeoutTicks
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

    fun onRestTick(w: Worker, now: Long, atBed: Boolean, bedPresent: Boolean): MachineEvent? {
        val timeout = if (w.travelTimeoutTicks > 0) w.travelTimeoutTicks else settings.travelTimeoutTicks
        return when (w.status) {
            WorkerStatus.TO_BED -> when {
                !bedPresent -> loseBed(w, now)

                atBed -> {
                    set(w, WorkerStatus.SLEEPING, now)
                    MachineEvent.FellAsleep
                }

                now - w.claimTick >= timeout -> loseBed(w, now)

                else -> null
            }

            WorkerStatus.SLEEPING -> if (bedPresent) null else loseBed(w, now)

            else -> null
        }
    }

    fun onRested(w: Worker, now: Long): MachineEvent {
        onRelease(w, now)
        return MachineEvent.Rested(toBinding = w.leashed)
    }

    fun onHeal(w: Worker, now: Long) {
        set(w, WorkerStatus.HEALING, now)
        w.claimTick = now
        w.travelTimeoutTicks = settings.travelTimeoutTicks
        w.retryAt = -1
        w.missedArrival = false
    }

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
        w.travelTimeoutTicks = 0
        set(w, WorkerStatus.RESTING_IN_PLACE, now)
        return MachineEvent.LostBed
    }

    private fun startWorking(w: Worker, now: Long): MachineEvent {
        set(w, WorkerStatus.WORKING, now)
        return MachineEvent.StartedWorking
    }

    private fun done(w: Worker, now: Long): MachineEvent {
        onRelease(w, now)
        return MachineEvent.ReleaseClaim(null, now)
    }

    private fun enterStandby(w: Worker, now: Long): MachineEvent {
        set(w, WorkerStatus.STANDBY, now)
        return MachineEvent.EnteredStandby
    }

    private fun timeOut(w: Worker, now: Long, key: TargetKey?): MachineEvent {
        val wait = if (key != null) {
            w.recordMiss(key, settings.retryWaitTicks).toLong()
        } else {
            TravelTiming.retryTicks(1, settings.retryWaitTicks).toLong()
        }
        if (!w.leashed) {
            onRelease(w, now)
            return MachineEvent.ReleaseClaim(key, now + wait)
        }
        set(w, WorkerStatus.STANDBY, now)
        w.missedArrival = true
        w.retryAt = now + wait
        return MachineEvent.MissedArrival
    }

    private fun retry(w: Worker, now: Long): MachineEvent {
        onClaim(w, now, if (w.travelTimeoutTicks > 0) w.travelTimeoutTicks else settings.travelTimeoutTicks)
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
