package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.bed.BedCandidate
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.heal.HealTargets
import codes.honor.cobblemondynamics.heal.HealTransaction
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.role.role
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import codes.honor.cobblemondynamics.stamina.Stamina
import codes.honor.cobblemondynamics.stamina.StaminaMath
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.EnumSet
import java.util.UUID

/** The Tired states of [WorkerMachine] on a fake clock: to bed, asleep, resting in place and awake again. */
class RestMachineTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val s = EngineSettings.DEFAULT
    private val machine = WorkerMachine(s)
    private val furnace = TargetKey.block(dim, BlockPos(0, 64, 0))
    private val hayBed = BedCandidate(TargetKey.block(dim, BlockPos(3, 64, 0)), BedCandidate.sleepPoint(BlockPos(3, 64, 0)), 1)

    private fun worker(leashed: Boolean = false) = Worker(UUID.randomUUID(), PastureKey(dim, 0)).also { it.leashed = leashed }

    /** A Lv1 worker that works its last tick of Work Stamina at tick 100. */
    private fun spent(leashed: Boolean = false): Worker {
        val w = worker(leashed)
        machine.onClaim(w, 0)
        machine.onTick(w, 10, arrived = true, demand = { true }, key = furnace)
        w.stamina = Stamina(1.0 / StaminaMath.capacityTicks(1, s))
        w.stamina.work(1, 1, s)
        return w
    }

    @Test
    fun `CD-STA-13 CD-BED-05 a Tired worker walks to the bed that it claimed`() {
        val w = spent()
        assertTrue(w.tired)
        assertEquals(MachineEvent.ToBed(hayBed), machine.onTired(w, 100, hayBed))
        assertEquals(WorkerStatus.TO_BED, w.status)
        assertEquals(hayBed, w.bed)
        assertEquals(100, w.statusSince)
    }

    @Test
    fun `CD-BED-08 a Tired worker with no free bed rests in place`() {
        val w = spent()
        assertEquals(MachineEvent.RestInPlace, machine.onTired(w, 100, null))
        assertEquals(WorkerStatus.RESTING_IN_PLACE, w.status)
        assertNull(w.bed)
        assertNull(machine.onRestTick(w, 5_000, atBed = false, bedPresent = false))
        assertEquals(WorkerStatus.RESTING_IN_PLACE, w.status)
    }

    @Test
    fun `CD-BED-10 ToBed becomes Sleeping on the tick that the worker reaches its bed`() {
        val w = spent()
        machine.onTired(w, 100, hayBed)
        assertNull(machine.onRestTick(w, 101, atBed = false, bedPresent = true))
        assertEquals(MachineEvent.FellAsleep, machine.onRestTick(w, 102, atBed = true, bedPresent = true))
        assertEquals(WorkerStatus.SLEEPING, w.status)
        assertEquals(102, w.statusSince)
        assertNull(machine.onRestTick(w, 103, atBed = true, bedPresent = true))
    }

    @Test
    fun `a bed not reached 600 ticks after its claim gives RestingInPlace`() {
        val w = spent()
        machine.onTired(w, 100, hayBed)
        assertNull(machine.onRestTick(w, 699, atBed = false, bedPresent = true))
        assertEquals(MachineEvent.LostBed, machine.onRestTick(w, 700, atBed = false, bedPresent = true))
        assertEquals(WorkerStatus.RESTING_IN_PLACE, w.status)
        assertNull(w.bed)
    }

    @Test
    fun `CD-BED-11 a broken bed moves its sleeper to RestingInPlace in the same tick`() {
        val walking = spent()
        machine.onTired(walking, 100, hayBed)
        assertEquals(MachineEvent.LostBed, machine.onRestTick(walking, 105, atBed = false, bedPresent = false))
        assertEquals(WorkerStatus.RESTING_IN_PLACE, walking.status)
        val sleeper = spent()
        machine.onTired(sleeper, 100, hayBed)
        machine.onRestTick(sleeper, 110, atBed = true, bedPresent = true)
        assertEquals(MachineEvent.LostBed, machine.onRestTick(sleeper, 111, atBed = true, bedPresent = false))
        assertEquals(WorkerStatus.RESTING_IN_PLACE, sleeper.status)
        assertNull(sleeper.bed)
    }

    @Test
    fun `CD-BED-12 CD-STA-14 at 100 percent an auto worker is Idle, and a leashed worker travels to its binding`() {
        val auto = spent()
        machine.onTired(auto, 100, hayBed)
        machine.onRestTick(auto, 110, atBed = true, bedPresent = true)
        assertEquals(MachineEvent.Rested(toBinding = false), machine.onRested(auto, 900))
        assertEquals(WorkerStatus.IDLE, auto.status)
        assertNull(auto.bed)
        val leashed = spent(leashed = true)
        machine.onTired(leashed, 100, null)
        assertEquals(MachineEvent.Rested(toBinding = true), machine.onRested(leashed, 900))
        // The engine claims the bound target in the same tick.
        machine.onClaim(leashed, 900)
        assertEquals(WorkerStatus.TRAVELING, leashed.status)
    }

    @Test
    fun `CD-STA-09 a Tired worker at 99_9 percent is refused a furnace, and at 100 percent it gets it`() {
        val stamina = Stamina(0.999, tired = true)
        val candidate = CandidateView(furnace, Vec3(0.5, 64.5, 0.5), listOf(role("stoker", setOf("fire")))) { true }
        fun view() = WorkerView(UUID.randomUUID(), Vec3(3.5, 64.0, 0.5), setOf("fire"), false, stamina.tired) { false }
        assertNull(Assigner.pickForIdle(view(), listOf(candidate)) { true })
        stamina.rest(3, 1, RecoveryRule.WORKER, null, s)
        assertTrue(stamina.tired, "Tired at ${stamina.percent}%")
        assertNull(Assigner.pickForIdle(view(), listOf(candidate)) { true })
        stamina.rest(1, 1, RecoveryRule.WORKER, null, s)
        assertFalse(stamina.tired)
        assertEquals(furnace, Assigner.pickForIdle(view(), listOf(candidate)) { true }?.first?.key)
    }

    /**
     * A scripted day of a Lv1 worker, in the engine's order: the worker's tick, then Work Stamina, then the Tired transitions. It
     * works until Tired, walks 30 ticks to a Hay Bed and sleeps to 100%, or rests in place when no bed is free (every other rest),
     * waits 40 ticks for a scan, travels 50 ticks and works again.
     */
    @Test
    fun `CD-ASG-18 a scripted day with work, bed, sleep and rest in place keeps one state and the Tired flag in step`() {
        val w = worker()
        val seen = EnumSet.noneOf(WorkerStatus::class.java)
        var rests = 0
        var arriveAt = 50L
        var bedAt = -1L
        var idleSince = 0L
        machine.onClaim(w, 0)
        for (t in 0L until 24_000L) {
            when (w.status) {
                WorkerStatus.TRAVELING, WorkerStatus.WORKING, WorkerStatus.STANDBY ->
                    machine.onTick(w, t, arrived = t >= arriveAt, demand = { true }, key = furnace)

                WorkerStatus.TO_BED, WorkerStatus.SLEEPING, WorkerStatus.RESTING_IN_PLACE ->
                    machine.onRestTick(w, t, atBed = t >= bedAt, bedPresent = true)

                WorkerStatus.IDLE -> if (t - idleSince >= 40) {
                    machine.onClaim(w, t)
                    arriveAt = t + 50
                }

                WorkerStatus.HEALING -> error("a worker never heals")
            }
            if (w.status == WorkerStatus.WORKING) {
                w.stamina.work(1, 1, s)
            } else {
                w.stamina.rest(1, 1, RecoveryRule.WORKER, if (w.status == WorkerStatus.SLEEPING) w.bed?.tier else null, s)
            }
            if (w.tired && !w.status.isResting) {
                machine.onTired(w, t, if (rests++ % 2 == 0) hayBed else null)
                bedAt = t + 30
            } else if (!w.tired && w.status.isResting) {
                machine.onRested(w, t)
                idleSince = t
            }
            seen += w.status
            assertEquals(w.tired, w.status.isResting, "tick $t: Tired ${w.tired} in ${w.status}")
        }
        val phases = listOf(
            WorkerStatus.IDLE,
            WorkerStatus.TRAVELING,
            WorkerStatus.WORKING,
            WorkerStatus.TO_BED,
            WorkerStatus.SLEEPING,
            WorkerStatus.RESTING_IN_PLACE,
        )
        assertTrue(seen.containsAll(phases), "states seen: $seen")
        assertTrue(rests >= 3, "only $rests rests in a day")
    }

    @Test
    fun `CD-HEAL-10 a healer completes its heal on the tick that it reaches its ally`() {
        val healer = worker()
        machine.onHeal(healer, 0)
        assertEquals(WorkerStatus.HEALING, healer.status)
        assertNull(machine.onHealTick(healer, 10, qualifies = true, inRange = false))
        assertEquals(MachineEvent.Healed, machine.onHealTick(healer, 11, qualifies = true, inRange = true))
        assertEquals(WorkerStatus.IDLE, healer.status)
    }

    @Test
    fun `CD-HEAL-11 a target that stops qualifying cancels the heal, and so does an ally not reached in 600 ticks`() {
        val healer = worker()
        machine.onHeal(healer, 0)
        assertEquals(MachineEvent.HealCancelled(timedOut = false), machine.onHealTick(healer, 5, qualifies = false, inRange = true))
        assertEquals(WorkerStatus.IDLE, healer.status)
        val slow = worker()
        machine.onHeal(slow, 0)
        assertNull(machine.onHealTick(slow, 599, qualifies = true, inRange = false))
        assertEquals(MachineEvent.HealCancelled(timedOut = true), machine.onHealTick(slow, 600, qualifies = true, inRange = false))
        assertEquals(WorkerStatus.IDLE, slow.status)
    }

    @Test
    fun `CD-HEAL-17 a heal that spends the healer's last 25 percent sends it to a free bed, or to rest in place`() {
        for (bed in listOf(hayBed, null)) {
            val healer = worker()
            healer.stamina = Stamina(0.25)
            machine.onHeal(healer, 0)
            assertEquals(MachineEvent.Healed, machine.onHealTick(healer, 10, qualifies = true, inRange = true))
            HealTransaction.complete(healer.stamina, Stamina(0.0, tired = true), s)
            assertTrue(healer.tired)
            machine.onTired(healer, 10, bed)
            assertEquals(if (bed == null) WorkerStatus.RESTING_IN_PLACE else WorkerStatus.TO_BED, healer.status)
        }
    }

    /**
     * CD-ASG-18 with a healer: a Lv1 worker works until Tired and rests in place, as no bed is free. A Lv50 healer at 75% takes a heal
     * claim on the next tick, walks 20 ticks and heals it: the healer pays 25 points, and the worker gets up at once. The healer
     * recovers at the healer rule in between, so after its third heal it stays just above 0% and can heal no more that day. Both
     * keep one state on every tick, and the Tired flag only in the rest states.
     */
    @Test
    fun `CD-ASG-18 a scripted day of a worker and a healer keeps one state each and the Tired flag in step`() {
        val w = worker()
        val h = worker()
        h.stamina = Stamina(0.75)
        val seen = EnumSet.noneOf(WorkerStatus::class.java)
        var heals = 0
        var arriveAt = 50L
        var idleSince = 0L
        var reachAt = -1L
        machine.onClaim(w, 0)
        for (t in 0L until 24_000L) {
            when (w.status) {
                WorkerStatus.TRAVELING, WorkerStatus.WORKING, WorkerStatus.STANDBY ->
                    machine.onTick(w, t, arrived = t >= arriveAt, demand = { true }, key = furnace)

                WorkerStatus.IDLE -> if (t - idleSince >= 40) {
                    machine.onClaim(w, t)
                    arriveAt = t + 50
                }

                else -> {}
            }
            if (h.status == WorkerStatus.IDLE && w.tired && HealTargets.canStart(h.stamina.fraction, h.tired, s)) {
                machine.onHeal(h, t)
                reachAt = t + 20
            } else if (machine.onHealTick(h, t, qualifies = w.tired, inRange = t >= reachAt) == MachineEvent.Healed) {
                HealTransaction.complete(h.stamina, w.stamina, s)
                heals++
            }
            for ((x, rule, level) in listOf(Triple(w, RecoveryRule.WORKER, 1), Triple(h, RecoveryRule.HEALER, 50))) {
                if (x.status == WorkerStatus.WORKING) x.stamina.work(1, level, s) else x.stamina.rest(1, level, rule, null, s)
                if (x.tired && !x.status.isResting) {
                    machine.onTired(x, t, null)
                } else if (!x.tired && x.status.isResting) {
                    machine.onRested(x, t)
                    if (x === w) idleSince = t
                }
                seen += x.status
                assertEquals(x.tired, x.status.isResting, "tick $t: Tired ${x.tired} in ${x.status}")
            }
        }
        assertEquals(3, heals)
        val phases = listOf(
            WorkerStatus.IDLE,
            WorkerStatus.TRAVELING,
            WorkerStatus.WORKING,
            WorkerStatus.RESTING_IN_PLACE,
            WorkerStatus.HEALING,
        )
        assertTrue(seen.containsAll(phases), "states seen: $seen")
        assertFalse(HealTargets.canStart(h.stamina.fraction, h.tired, s), "the healer could still heal at ${h.stamina.percent}%")
    }
}
