package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineRecoveryTest {
    private val s = EngineSettings.DEFAULT

    @Test
    fun `CD-STA-11 a Lv10 worker at 0 percent reads 50 percent after 18,000 ticks away`() {
        val st = Stamina(0.0, tired = true)
        OfflineRecovery.apply(st, elapsed = 18_000, level = 10, rule = RecoveryRule.WORKER, s = s)
        assertEquals(50.0, st.percent, 0.1)
        assertTrue(st.tired, "a worker at 50% is still Tired")
    }

    @Test
    fun `CD-STA-11 time away follows the healer rule of a healer`() {
        val st = Stamina(0.0, tired = true)
        OfflineRecovery.apply(st, elapsed = 60L * StaminaMath.TICKS_PER_MINUTE, level = 100, rule = RecoveryRule.HEALER, s = s)
        assertEquals(50.0, st.percent, 0.1)
    }

    /** A Pokémon can come from a world whose game time ran further. Its time away is then unknown, and it gains nothing. */
    @Test
    fun `CD-STA-11 a negative time away changes nothing`() {
        val st = Stamina(0.37)
        OfflineRecovery.apply(st, elapsed = -5_000, level = 10, rule = RecoveryRule.WORKER, s = s)
        assertEquals(37.0, st.percent, 1e-9)
    }
}
