package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BalanceTest {
    private val s = EngineSettings.DEFAULT

    /** Simulates work until Tired, then rest until 100%, one tick per step, and returns the fraction of ticks spent working. */
    private fun uptime(level: Int, bedTier: Int?, cycles: Int): Double {
        val st = Stamina()
        var work = 0L
        var total = 0L
        repeat(cycles) {
            while (!st.tired) {
                st.work(1, level, s)
                work++
                total++
            }
            while (st.tired) {
                st.rest(1, level, RecoveryRule.WORKER, bedTier, s)
                total++
            }
        }
        return work.toDouble() / total
    }

    @Test
    fun `CD-BAL-03 a Lv50 worker on a Starlit Bed works 88_9 percent of the time`() {
        val u = uptime(50, bedTier = 4, cycles = 10)
        assertEquals(8.0 / 9.0, u, 0.001)
        assertTrue(u <= 0.890, "uptime $u")
    }

    /** Uptime = 1 / (1 + 1 / f) for every level, because work time and rest time both scale with level (ADR-0008). */
    @Test
    fun `CD-BAL-03 uptime depends only on the bed tier`() {
        val expected = mapOf(null to 1.0 / 2, 1 to 2.0 / 3, 2 to 3.0 / 4, 3 to 4.0 / 5, 4 to 8.0 / 9)
        for ((tier, value) in expected) {
            for (level in listOf(1, 10)) assertEquals(value, uptime(level, tier, cycles = 3), 0.001, "tier $tier at Lv$level")
        }
    }

    @Test
    fun `CD-BAL-02 sixteen Lv100 Generators with no beds average at most 40,000 FE per tick`() {
        val average = 16 * 5_000 * uptime(100, bedTier = null, cycles = 1)
        assertTrue(average <= 40_000.0 + 1e-6, "average $average")
    }
}
