package codes.honor.cobblemondynamics.handler.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryClockTest {
    @Test
    fun `CD-MAG-08 asked on every tick, the clock lets a query run at ticks 0, 20 and 40 only`() {
        val clock = QueryClock()
        val runs = (0L..40L).filter { now -> clock.isDue(now).also { if (it) clock.ran(now) } }
        assertEquals(listOf(0L, 20L, 40L), runs)
    }
}
