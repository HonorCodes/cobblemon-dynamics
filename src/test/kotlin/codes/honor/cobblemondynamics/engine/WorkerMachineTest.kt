package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkerMachineTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val key = TargetKey.block(dim, BlockPos.ZERO)
    private val machine = WorkerMachine(EngineSettings.DEFAULT)
    private fun worker(leashed: Boolean = false) = Worker(UUID.randomUUID(), PastureKey(dim, 0)).also { it.leashed = leashed }

    @Test
    fun `CD-ASG-11 arrival with demand puts the worker in Working on the same tick`() {
        val w = worker()
        machine.onClaim(w, 0)
        machine.onTick(w, 50, arrived = true, demand = { true }, key = key)
        assertEquals(WorkerStatus.WORKING, w.status)
    }

    @Test
    fun `CD-ASG-12 no demand keeps Standby, and new demand resumes work within 20 ticks`() {
        val w = worker()
        machine.onClaim(w, 0)
        machine.onTick(w, 10, arrived = true, demand = { true }, key = key)
        machine.onTick(w, 11, arrived = true, demand = { false }, key = key)
        assertEquals(WorkerStatus.STANDBY, w.status)
        var demand = false
        for (t in 12L..200L) {
            if (t == 100L) demand = true
            machine.onTick(w, t, true, { demand }, key)
            if (w.status == WorkerStatus.WORKING) {
                assertTrue(t - 100 <= 20)
                return
            }
        }
        error("never resumed")
    }

    @Test
    fun `CD-ASG-13 an auto claim is held at tick 599 and released at 600`() {
        val w = worker()
        machine.onClaim(w, 0)
        assertEquals(null, machine.onTick(w, 599, arrived = false, demand = { true }, key = key))
        val e = machine.onTick(w, 600, arrived = false, demand = { true }, key = key)
        assertInstanceOf(MachineEvent.ReleaseClaim::class.java, e)
        assertEquals(WorkerStatus.IDLE, w.status)
    }

    @Test
    fun `CD-ASG-14 CD-NAV-09 the timed-out target backs off progressively at 200, 600 and 2,400 ticks`() {
        val w = worker()
        machine.onClaim(w, 0)
        val e1 = machine.onTick(w, 600, false, { true }, key) as MachineEvent.ReleaseClaim
        w.skipUntil[key] = e1.until
        assertTrue(w.isSkipped(key, 600 + 199))
        assertFalse(w.isSkipped(key, 600 + 200))

        machine.onClaim(w, 1_000)
        val e2 = machine.onTick(w, 1_600, false, { true }, key) as MachineEvent.ReleaseClaim
        w.skipUntil[key] = e2.until
        assertTrue(w.isSkipped(key, 1_600 + 599))
        assertFalse(w.isSkipped(key, 1_600 + 600))

        machine.onClaim(w, 2_500)
        val e3 = machine.onTick(w, 3_100, false, { true }, key) as MachineEvent.ReleaseClaim
        w.skipUntil[key] = e3.until
        assertTrue(w.isSkipped(key, 3_100 + 2_399))
        assertFalse(w.isSkipped(key, 3_100 + 2_400))
    }

    /** R84: the engine drops a worker's expired skips and stalls when a claim ends, so neither map grows for the worker's whole life. */
    @Test
    fun `CD-ASG-14 CD-FRZ-11 a worker drops its expired skips and stalls and keeps the others`() {
        val w = worker()
        val keys = (1..4).map { TargetKey.block(dim, BlockPos(it, 0, 0)) }
        w.skipUntil[keys[0]] = 100
        w.skipUntil[keys[1]] = 300
        w.stalledUntil[keys[2]] = 200
        w.stalledUntil[keys[3]] = 201
        w.dropExpired(200)
        assertEquals(setOf(keys[1]), w.skipUntil.keys)
        assertEquals(setOf(keys[3]), w.stalledUntil.keys)
        assertTrue(w.isSkipped(keys[1], 200) && w.isStalled(keys[3], 200))
    }

    @Test
    fun `CD-ASG-17 CD-NAV-09 a leashed worker waits in Standby from 600 to 799 on first miss and travels at 800`() {
        val w = worker(leashed = true)
        machine.onClaim(w, 0)
        assertEquals(MachineEvent.MissedArrival, machine.onTick(w, 600, false, { true }, key))
        for (t in 601L..799L) {
            machine.onTick(w, t, false, { true }, key)
            assertEquals(WorkerStatus.STANDBY, w.status, "tick $t")
        }
        assertEquals(MachineEvent.Retry, machine.onTick(w, 800, false, { true }, key))
        assertEquals(WorkerStatus.TRAVELING, w.status)
    }

    @Test
    fun `CD-ASG-12 a leashed worker that arrives after its retry stays in Standby`() {
        val w = worker(leashed = true)
        machine.onClaim(w, 0)
        machine.onTick(w, 600, false, { true }, key)
        machine.onTick(w, 800, false, { true }, key)
        machine.onTick(w, 900, true, { false }, key)
        assertEquals(null, machine.onTick(w, 901, true, { false }, key))
        assertEquals(WorkerStatus.STANDBY, w.status)
    }

    @Test
    fun `CD-CFG-12 a 1,200-tick timeout holds at 1,199 and releases at 1,200`() {
        val m = WorkerMachine(EngineSettings.DEFAULT.copy(travelTimeoutTicks = 1_200))
        val w = worker()
        m.onClaim(w, 0)
        assertEquals(null, m.onTick(w, 1_199, false, { true }, key))
        assertInstanceOf(MachineEvent.ReleaseClaim::class.java, m.onTick(w, 1_200, false, { true }, key))
    }

    @Test
    fun `CD-ASG-19 CD-CFG-15 the Standby move opens at the configured delay`() {
        val w = worker()
        machine.onClaim(w, 0)
        machine.onTick(w, 5, true, { false }, key) // Standby since 5
        assertFalse(machine.canMoveFromStandby(w, 5 + 199))
        assertTrue(machine.canMoveFromStandby(w, 5 + 200))
        val slow = WorkerMachine(EngineSettings.DEFAULT.copy(standbyMoveTicks = 400))
        assertFalse(slow.canMoveFromStandby(w, 5 + 399))
        assertTrue(slow.canMoveFromStandby(w, 5 + 400))
    }

    @Test
    fun `CD-ASG-20 a leashed worker never moves from Standby`() {
        val w = worker(leashed = true)
        machine.onClaim(w, 0)
        machine.onTick(w, 5, true, { false }, key)
        assertFalse(machine.canMoveFromStandby(w, 10_000))
    }

    @Test
    fun `CD-ASG-18 a scripted day leaves exactly one state on every tick`() {
        val w = worker()
        var arrived = false
        var demand = true
        machine.onClaim(w, 0)
        for (t in 0L until 24_000L) {
            if (t == 100L) arrived = true
            if (t % 3_000 == 0L) demand = !demand
            if (t == 20_000L) machine.onRelease(w, t)
            machine.onTick(w, t, arrived, { demand }, key)
            assertTrue(w.status in WorkerStatus.entries)
        }
    }

    @Test
    fun `CD-CRP-10 a releasing worker with no demand releases its claim at once`() {
        val w = worker()
        machine.onClaim(w, 0)
        val onArrival = machine.onTick(w, 30, arrived = true, demand = { false }, key = key, releasesWhenDone = true)
        assertInstanceOf(MachineEvent.ReleaseClaim::class.java, onArrival)
        assertEquals(WorkerStatus.IDLE, w.status)
        machine.onClaim(w, 40)
        machine.onTick(w, 50, arrived = true, demand = { true }, key = key, releasesWhenDone = true)
        assertEquals(WorkerStatus.WORKING, w.status)
        val whileWorking = machine.onTick(w, 51, arrived = true, demand = { false }, key = key, releasesWhenDone = true)
        assertInstanceOf(MachineEvent.ReleaseClaim::class.java, whileWorking)
        assertEquals(WorkerStatus.IDLE, w.status)
        // The release skips nothing: the next scan pass gives the worker the nearest crop with demand.
        assertEquals(null, (whileWorking as MachineEvent.ReleaseClaim).skip)
    }

    @Test
    fun `CD-CRP-11 a leashed farming worker enters Standby`() {
        val w = worker(leashed = true)
        machine.onClaim(w, 0)
        machine.onTick(w, 10, arrived = true, demand = { true }, key = key, releasesWhenDone = true)
        assertEquals(null, machine.onTick(w, 11, arrived = true, demand = { true }, key = key, releasesWhenDone = true))
        val done = machine.onTick(w, 12, arrived = true, demand = { false }, key = key, releasesWhenDone = true)
        assertEquals(MachineEvent.EnteredStandby, done)
        assertEquals(WorkerStatus.STANDBY, w.status)
    }

    @Test
    fun `CD-CRP-12 a Quick Worker of a farming role enters Standby, which ends its order`() {
        val w = Worker(UUID.randomUUID(), pasture = null)
        machine.onClaim(w, 0)
        machine.onTick(w, 10, arrived = true, demand = { false }, key = key, releasesWhenDone = true)
        assertEquals(WorkerStatus.STANDBY, w.status)
    }

    @Test
    fun `CD-PRG-16 Working enters Slacking, holds for slackDurationTicks, and expires to Traveling to resume or Standby or release`() {
        val m = WorkerMachine(EngineSettings.DEFAULT.copy(slackDurationTicks = 300))
        val w = worker()
        m.onClaim(w, 0)
        assertEquals(MachineEvent.StartedWorking, m.onTick(w, 10, arrived = true, demand = { true }, key = key))
        assertEquals(WorkerStatus.WORKING, w.status)

        m.onSlack(w, 100)
        assertEquals(WorkerStatus.SLACKING, w.status)
        assertEquals(100L, w.statusSince)
        for (t in 101L..399L) {
            assertEquals(null, m.onTick(w, t, arrived = false, demand = { true }, key = key), "tick $t")
            assertEquals(WorkerStatus.SLACKING, w.status)
        }
        assertEquals(MachineEvent.SlackEnded, m.onTick(w, 400, arrived = false, demand = { true }, key = key))
        assertEquals(WorkerStatus.TRAVELING, w.status)
        assertEquals(400L, w.claimTick)

        assertEquals(MachineEvent.StartedWorking, m.onTick(w, 420, arrived = true, demand = { true }, key = key))
        assertEquals(WorkerStatus.WORKING, w.status)

        m.onSlack(w, 500)
        assertEquals(MachineEvent.SlackEnded, m.onTick(w, 800, arrived = true, demand = { false }, key = key))
        assertEquals(WorkerStatus.TRAVELING, w.status)
        assertEquals(MachineEvent.EnteredStandby, m.onTick(w, 801, arrived = true, demand = { false }, key = key))
        assertEquals(WorkerStatus.STANDBY, w.status)

        val relWorker = worker()
        m.onClaim(relWorker, 0)
        m.onTick(relWorker, 10, arrived = true, demand = { true }, key = key, releasesWhenDone = true)
        m.onSlack(relWorker, 50)
        assertEquals(
            MachineEvent.SlackEnded,
            m.onTick(relWorker, 350, arrived = true, demand = {
                false
            }, key = key, releasesWhenDone = true),
        )
        assertInstanceOf(
            MachineEvent.ReleaseClaim::class.java,
            m.onTick(relWorker, 351, arrived = true, demand = { false }, key = key, releasesWhenDone = true),
        )
        assertEquals(WorkerStatus.IDLE, relWorker.status)
    }
}
