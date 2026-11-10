package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TickClockTest {
    @Test
    fun `CD-CRY-02 random ticks at ticks 0, 10 and 20, and none at tick 9`() {
        assertEquals(listOf(0L, 10L, 20L), (0L..20L).filter { TickClock.every(10, since = 0, now = it) })
        assertFalse(TickClock.every(10, since = 0, now = 9))
    }

    @Test
    fun `CD-CRY-02 the count starts at the tick that the work starts`() {
        assertTrue(TickClock.every(10, since = 1_003, now = 1_003))
        assertTrue(TickClock.every(10, since = 1_003, now = 1_013))
        assertFalse(TickClock.every(10, since = 1_003, now = 1_010))
        assertFalse(TickClock.every(10, since = 1_003, now = 993))
    }

    @Test
    fun `an interval below 1 counts as every tick`() {
        assertEquals(listOf(0L, 1L, 2L), (0L..2L).filter { TickClock.every(0, since = 0, now = it) })
    }
}
