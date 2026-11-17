package codes.honor.cobblemondynamics.handler.item

import codes.honor.cobblemondynamics.handler.xp.SoulCollectorParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryClockTest {
    @Test
    fun `CD-MAG-08 asked on every tick, the clock lets a query run at ticks 0, 20 and 40 only`() {
        val clock = QueryClock()
        val runs = (0L..40L).filter { now -> clock.isDue(now).also { if (it) clock.ran(now) } }
        assertEquals(listOf(0L, 20L, 40L), runs)
    }

    @Test
    fun `CD-SOU-09 the Soul Collector's clock lets one query of the orbs run in each 20 ticks`() {
        val clock = QueryClock(SoulCollectorParams.BATCH_TICKS)
        val runs = (5L..65L).filter { now -> clock.isDue(now).also { if (it) clock.ran(now) } }
        assertEquals(listOf(5L, 25L, 45L, 65L), runs)
    }
}
