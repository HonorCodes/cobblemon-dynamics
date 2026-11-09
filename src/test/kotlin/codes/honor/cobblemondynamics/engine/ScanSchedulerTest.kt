package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ScanSchedulerTest {
    /** Runs a fake clock and returns the ticks at which passes start. Each pass needs `work` ticks. */
    private fun starts(interval: Int, work: Int, until: Long): List<Long> {
        val s = ScanScheduler(interval, firstStart = 0)
        val out = mutableListOf<Long>()
        var remaining = 0
        for (now in 0..until) {
            if (s.shouldStart(now)) {
                s.start(now)
                out += now
                remaining = work
            }
            if (s.running) {
                remaining--
                if (remaining == 0) s.complete(now)
            }
        }
        return out
    }

    @Test
    fun `CD-ASG-06 passes start at ticks 0, 40 and 80 and never at 39`() {
        val s = starts(interval = 40, work = 1, until = 100)
        assertEquals(listOf(0L, 40L, 80L), s)
        assertFalse(39L in s)
    }

    @Test
    fun `CD-ASG-06 a 108-tick pass is followed by a pass at tick 120`() {
        assertEquals(listOf(0L, 120L, 240L), starts(interval = 40, work = 108, until = 250))
    }

    @Test
    fun `CD-ASG-06 a pass never starts before the previous one completes`() {
        assertEquals(listOf(0L, 80L), starts(interval = 40, work = 41, until = 100))
    }

    @Test
    fun `CD-CFG-11 an 80-tick interval starts passes every 80 ticks`() {
        assertEquals(listOf(0L, 80L, 160L), starts(interval = 80, work = 1, until = 200))
    }
}
