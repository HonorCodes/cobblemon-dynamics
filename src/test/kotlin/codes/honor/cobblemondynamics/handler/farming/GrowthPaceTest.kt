package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import codes.honor.cobblemondynamics.role.BuiltInRoles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrowthPaceTest {
    @Test
    fun `CD-CUL-04 the interval scales with level`() {
        assertEquals(1_200, GrowthPace.intervalTicks(1, 30))
        assertEquals(80, GrowthPace.intervalTicks(50, 30))
        assertEquals(40, GrowthPace.intervalTicks(100, 30))
        // No level above 100 goes faster (CD-BAL-08), and no level below 1 slower.
        assertEquals(40, GrowthPace.intervalTicks(150, 30))
        assertEquals(1_200, GrowthPace.intervalTicks(0, 30))
    }

    @Test
    fun `CD-CUL-04 CD-CRP-20 uses fall on the interval only`() {
        val interval = GrowthPace.intervalTicks(100, 30)
        assertEquals(listOf(0L, 40L, 80L), (0L..80L).filter { TickClock.every(interval, 0, it) })
    }

    @Test
    fun `CD-CRP-20 no level gives two uses in one tick`() {
        for (level in 1..100) assertTrue(GrowthPace.intervalTicks(level, 30) >= 1, "level $level")
    }

    @Test
    fun `CD-CUL-08 the pace follows the role file`() {
        assertEquals(80, GrowthPace.intervalTicks(100, 15))
        val params = CultivatorParams.of(BuiltInRoles.parse("cultivator").params)
        assertEquals(40, GrowthPace.intervalTicks(100, params.usesPerMinuteAt100))
    }
}
