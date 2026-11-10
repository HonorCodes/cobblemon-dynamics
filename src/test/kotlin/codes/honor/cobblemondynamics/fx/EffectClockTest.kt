package codes.honor.cobblemondynamics.fx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EffectClockTest {
    @Test
    fun `CD-FX-01 effects start at 0, 40 and 80 and never at 39`() {
        val plays = (0L..100L).filter { EffectClock.shouldPlay(0, it, 40) }
        assertEquals(listOf(0L, 40L, 80L), plays)
    }
}
