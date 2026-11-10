package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.function.LongSupplier

class EngineTimingTest {
    private class FakeClock : LongSupplier {
        var now = 0L
        var reads = 0

        override fun getAsLong(): Long {
            reads++
            return now
        }
    }

    private fun times(vararg millis: Double): TickTimes = TickTimes(millis.size).apply {
        for (m in millis) add((m * 1_000_000).toLong())
    }

    @Test
    fun `CD-PERF-01 the mean is the recorded time over the recorded ticks`() {
        assertEquals(2.5, times(1.0, 2.0, 3.0, 4.0).meanMillis(), 1e-9)
    }

    @Test
    fun `CD-PERF-02 the p95 is the nearest-rank 95th percentile`() {
        val hundred = TickTimes(100).apply { for (m in 100 downTo 1) add(m * 1_000_000L) }
        assertEquals(95.0, hundred.percentileMillis(0.95), 1e-9)
        val twenty = TickTimes(20).apply { for (m in 1..20) add(m * 1_000_000L) }
        assertEquals(19.0, twenty.percentileMillis(0.95), 1e-9)
        assertEquals(1.0, times(1.0).percentileMillis(0.95), 1e-9)
    }

    @Test
    fun `the ring keeps the last ticks, oldest first`() {
        val ring = TickTimes(3)
        for (n in 1L..4L) ring.add(n)
        assertEquals(3, ring.count)
        assertArrayEquals(longArrayOf(2, 3, 4), ring.values())
    }

    @Test
    fun `an empty recording has no mean or percentile`() {
        assertThrows(IllegalStateException::class.java) { TickTimes(5).meanMillis() }
        assertThrows(IllegalStateException::class.java) { TickTimes(5).percentileMillis(0.95) }
    }

    @Test
    fun `the sections of one tick add up, and a section inside another counts once`() {
        val clock = FakeClock()
        val timer = SectionTimer(clock)
        timer.startRecording(10)
        timer.endTick()
        timer.time { clock.now += 5 }
        timer.time {
            clock.now += 3
            timer.time { clock.now += 2 }
        }
        clock.now += 100 // between sections, which is not Dynamics time
        timer.endTick()
        timer.time { clock.now += 7 }
        timer.endTick()
        assertArrayEquals(longArrayOf(10, 7), timer.recorded?.values())
    }

    @Test
    fun `a recording starts with the next whole tick`() {
        val clock = FakeClock()
        val timer = SectionTimer(clock)
        timer.startRecording(10)
        timer.time { clock.now += 4 }
        timer.endTick()
        assertEquals(0, timer.recorded?.count)
        timer.time { clock.now += 6 }
        timer.endTick()
        assertArrayEquals(longArrayOf(6), timer.recorded?.values())
    }

    @Test
    fun `nothing is timed while no recording runs`() {
        val clock = FakeClock()
        val timer = SectionTimer(clock)
        assertEquals(3, timer.time { 3 })
        timer.endTick()
        assertEquals(0, clock.reads)
        assertNull(timer.recorded)
    }

    @Test
    fun `a section that throws keeps its time, and the next section is timed`() {
        val clock = FakeClock()
        val timer = SectionTimer(clock)
        timer.startRecording(10)
        timer.endTick()
        assertThrows(IllegalStateException::class.java) {
            timer.time {
                clock.now += 2
                error("handler failed")
            }
        }
        timer.time { clock.now += 3 }
        timer.endTick()
        assertArrayEquals(longArrayOf(5), timer.recorded?.values())
    }

    @Test
    fun `stopping hands back the recording and stops the timing`() {
        val clock = FakeClock()
        val timer = SectionTimer(clock)
        timer.startRecording(10)
        timer.endTick()
        timer.time { clock.now += 1 }
        timer.endTick()
        val recording = timer.stopRecording()
        assertArrayEquals(longArrayOf(1), recording?.values())
        val reads = clock.reads
        timer.time { clock.now += 1 }
        timer.endTick()
        assertEquals(reads, clock.reads)
        assertNull(timer.recorded)
    }
}
