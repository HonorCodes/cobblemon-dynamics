package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RetryBackoffTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val key = TargetKey.block(dim, BlockPos(3, 1, 3))

    @Test
    fun `CD-NAV-09 steps 200 to 600 to 2400, clamps to retryWaitTicks and resets on arrival`() {
        assertEquals(200, TravelTiming.retryTicks(1, 6_000))
        assertEquals(600, TravelTiming.retryTicks(2, 6_000))
        assertEquals(2_400, TravelTiming.retryTicks(3, 6_000))
        assertEquals(2_400, TravelTiming.retryTicks(4, 6_000))
        assertEquals(200, TravelTiming.retryTicks(1, 500))
        assertEquals(500, TravelTiming.retryTicks(2, 500))
        assertEquals(500, TravelTiming.retryTicks(3, 500))

        val w = Worker(UUID.randomUUID(), PastureKey(dim, 0))
        assertEquals(200, w.recordMiss(key, 6_000))
        assertEquals(600, w.recordMiss(key, 6_000))
        assertEquals(2_400, w.recordMiss(key, 6_000))
        assertEquals(2_400, w.recordMiss(key, 6_000))
        w.recordArrival(key)
        assertEquals(200, w.recordMiss(key, 6_000))
    }

    @Test
    fun `missedArrival unfreezes immediately when arrived at machine with demand`() {
        val machine = WorkerMachine(EngineSettings.DEFAULT)
        val w = Worker(UUID.randomUUID(), PastureKey(dim, 0)).also { it.leashed = true }
        machine.onClaim(w, 0)
        assertEquals(MachineEvent.MissedArrival, machine.onTick(w, 600, arrived = false, demand = { true }, key = key))
        assertTrue(w.missedArrival)
        assertEquals(600L + 200L, w.retryAt)
        val resumed = machine.onTick(w, 615, arrived = true, demand = { true }, key = key)
        assertEquals(MachineEvent.StartedWorking, resumed)
        assertFalse(w.missedArrival)
        assertEquals(WorkerStatus.WORKING, w.status)
    }
}
