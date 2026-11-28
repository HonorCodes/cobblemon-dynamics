package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkerChainingTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val key = TargetKey.block(dim, BlockPos(2, 1, 2), "harvest")

    @Test
    fun `CD-NAV-10 requestImmediate starts next pasture scan on the following tick without waiting 40 ticks`() {
        val scheduler = ScanScheduler(intervalTicks = 40, firstStart = 0)
        assertTrue(scheduler.shouldStart(0))
        scheduler.start(0)
        scheduler.complete(0)
        assertFalse(scheduler.shouldStart(1))

        val machine = WorkerMachine(EngineSettings.DEFAULT)
        val worker = Worker(UUID.randomUUID(), PastureKey(dim, 0))
        machine.onClaim(worker, 0)
        machine.onTick(worker, 5, arrived = true, demand = { true }, key = key, releasesWhenDone = true)
        val event = machine.onTick(worker, 6, arrived = true, demand = {
            false
        }, key = key, releasesWhenDone = true) as MachineEvent.ReleaseClaim
        assertTrue(event.skip == null)
        if (event.skip == null) scheduler.requestImmediate()

        assertTrue(scheduler.shouldStart(7), "next tick must start an immediate scan after releasesWhenDone")
        scheduler.start(7)
        scheduler.complete(7)
        assertFalse(scheduler.shouldStart(8))
    }

    @Test
    fun `CD-NAV-10 chaining wander pause stays active across a 59-tick 64-blocks-per-tick scan`() {
        val scheduler = ScanScheduler(intervalTicks = 40, firstStart = 0)
        scheduler.requestImmediate()
        scheduler.start(10)
        val chainingUntil = 10L + 40L
        for (tick in 10L..68L) {
            assertTrue(
                scheduler.holdsChainingPause(awaitingScan = true, now = tick, chainingUntil = chainingUntil),
                "wander pause lapsed at tick $tick while 59-tick scan was still running",
            )
        }
        scheduler.complete(68)
        assertFalse(scheduler.holdsChainingPause(awaitingScan = false, now = 69, chainingUntil = chainingUntil))
    }
}
