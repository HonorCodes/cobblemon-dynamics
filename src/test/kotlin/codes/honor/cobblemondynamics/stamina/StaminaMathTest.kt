package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every rule takes its ticks as a parameter, so these tests are the fake clock: one call per tick, or one call for many ticks. */
class StaminaMathTest {
    private val s = EngineSettings.DEFAULT

    private fun near(expected: Double, actual: Double, tol: Double) = assertEquals(expected, actual, tol)

    @Test
    fun `CD-STA-01 capacity is 3,600 ticks per level`() {
        near(3_600.0, StaminaMath.capacityTicks(1, s), 0.0)
        near(180_000.0, StaminaMath.capacityTicks(50, s), 0.0)
        near(360_000.0, StaminaMath.capacityTicks(100, s), 0.0)
    }

    /** The math takes a level and no species, so rarity cannot change it. */
    @Test
    fun `CD-STA-02 a Lv100 legendary has the same capacity`() = near(360_000.0, StaminaMath.capacityTicks(100, s), 0.0)

    @Test
    fun `CD-STA-03 a level-up keeps the percentage`() {
        val st = Stamina(0.40)
        st.rest(0, 31, RecoveryRule.WORKER, null, s)
        near(40.0, st.percent, 1e-9)
        st.work(0, 31, s)
        near(40.0, st.percent, 1e-9)
    }

    @Test
    fun `CD-STA-06 drain only while Working, recovery in Standby`() {
        val a = Stamina(1.0)
        a.work(1_800, 1, s)
        near(50.0, a.percent, 0.1)
        val b = Stamina(0.5)
        b.rest(600, 1, RecoveryRule.WORKER, null, s)
        near(66.7, b.percent, 0.1)
    }

    @Test
    fun `CD-STA-06 CD-STA-08 one-tick steps drain a Lv1 worker in exactly 3,600 ticks`() {
        val st = Stamina()
        repeat(3_599) { st.work(1, 1, s) }
        assertFalse(st.tired, "Tired after 3,599 ticks at ${st.percent}%")
        st.work(1, 1, s)
        assertTrue(st.tired, "not Tired after 3,600 ticks at ${st.percent}%")
        near(0.0, st.percent, 0.0)
    }

    @Test
    fun `CD-STA-07 a Lv10 worker recovers half in 15 min and all in 30 min`() {
        val st = Stamina(0.0, tired = true)
        st.rest(18_000, 10, RecoveryRule.WORKER, null, s)
        near(50.0, st.percent, 0.1)
        st.rest(18_000, 10, RecoveryRule.WORKER, null, s)
        near(100.0, st.percent, 0.1)
    }

    @Test
    fun `CD-STA-07 CD-STA-10 one-tick steps keep a Lv10 worker Tired until tick 36,000`() {
        val st = Stamina(0.0, tired = true)
        repeat(35_999) { st.rest(1, 10, RecoveryRule.WORKER, null, s) }
        assertTrue(st.tired, "not Tired after 35,999 ticks at ${st.percent}%")
        st.rest(1, 10, RecoveryRule.WORKER, null, s)
        assertFalse(st.tired, "still Tired after 36,000 ticks at ${st.percent}%")
        near(100.0, st.percent, 0.0)
    }

    @Test
    fun `CD-STA-08 the last tick of capacity makes the worker Tired`() {
        val st = Stamina(1.0 / 3_600)
        st.work(1, 1, s)
        assertTrue(st.tired)
        near(0.0, st.percent, 1e-9)
    }

    @Test
    fun `CD-STA-10 a Tired worker at 100% is no longer Tired`() {
        val st = Stamina(0.0, tired = true)
        st.rest(3_600, 1, RecoveryRule.WORKER, null, s)
        assertFalse(st.tired)
    }

    @Test
    fun `CD-HEAL-12 healer recovery is 1,200, 665_5 and 120 min`() {
        near(1_200.0, StaminaMath.healerMinutes(1, s), 0.0)
        near(665.5, StaminaMath.healerMinutes(50, s), 0.5)
        near(120.0, StaminaMath.healerMinutes(100, s), 0.0)
        near(120.0 * StaminaMath.TICKS_PER_MINUTE, StaminaMath.fullRecoveryTicks(100, RecoveryRule.HEALER, s), 0.0)
    }

    /** Cobblemon's level cap is a config value. The healer line ends at 100, so a recovery time never reaches 0. */
    @Test
    fun `CD-HEAL-12 healer levels above 100 recover like level 100`() {
        near(120.0, StaminaMath.healerMinutes(150, s), 0.0)
        assertTrue(StaminaMath.fullRecoveryTicks(250, RecoveryRule.HEALER, s) > 0.0)
    }

    @Test
    fun `CD-HEAL-13 a leashed Fairy-type follows the worker rule, 150 min at Lv50`() {
        val rule = RecoveryRule.of(hasFairyType = true, hasMachineBinding = true)
        assertEquals(RecoveryRule.WORKER, rule)
        near(150.0, StaminaMath.fullRecoveryTicks(50, rule, s) / StaminaMath.TICKS_PER_MINUTE, 0.5)
        val st = Stamina(0.0, tired = true)
        st.rest(150L * StaminaMath.TICKS_PER_MINUTE, 50, rule, null, s)
        assertFalse(st.tired)
    }

    @Test
    fun `CD-HEAL-13 only an unleashed Fairy-type is a healer`() {
        assertEquals(RecoveryRule.HEALER, RecoveryRule.of(hasFairyType = true, hasMachineBinding = false))
        assertEquals(RecoveryRule.WORKER, RecoveryRule.of(hasFairyType = false, hasMachineBinding = false))
        assertEquals(RecoveryRule.WORKER, RecoveryRule.of(hasFairyType = false, hasMachineBinding = true))
    }

    @Test
    fun `CD-BED-03 bed tiers divide recovery by 2, 3, 4 and 8`() {
        val worker = StaminaMath.fullRecoveryTicks(100, RecoveryRule.WORKER, s) / StaminaMath.TICKS_PER_MINUTE
        val healer = StaminaMath.fullRecoveryTicks(100, RecoveryRule.HEALER, s) / StaminaMath.TICKS_PER_MINUTE
        listOf(150.0, 100.0, 75.0, 37.5).forEachIndexed { i, m -> near(m, worker / StaminaMath.bedFactor(i + 1, s), m * 0.01) }
        listOf(60.0, 40.0, 30.0, 15.0).forEachIndexed { i, m -> near(m, healer / StaminaMath.bedFactor(i + 1, s), m * 0.01) }
        assertEquals(1, StaminaMath.bedFactor(null, s))
    }

    @Test
    fun `CD-BED-03 a Lv100 worker on a Starlit Bed is full after 37_5 min, not before`() {
        val st = Stamina(0.0, tired = true)
        val ticks = (37.5 * StaminaMath.TICKS_PER_MINUTE).toLong()
        st.rest(ticks - 1, 100, RecoveryRule.WORKER, bedTier = 4, s)
        assertTrue(st.tired)
        st.rest(1, 100, RecoveryRule.WORKER, bedTier = 4, s)
        assertFalse(st.tired)
    }

    @Test
    fun `CD-BED-04 the factor applies only on the bed`() {
        val st = Stamina(0.0, tired = true)
        st.rest(9_000, 10, RecoveryRule.WORKER, bedTier = 1, s)
        st.rest(9_000, 10, RecoveryRule.WORKER, bedTier = null, s)
        near(75.0, st.percent, 0.1)
    }

    @Test
    fun `CD-CFG-04 7,200 ticks per level makes Lv1 Tired at 7,200 ticks, not before`() {
        val cfg = s.copy(ticksPerLevel = 7_200)
        val st = Stamina(1.0)
        st.work(7_199, 1, cfg)
        assertFalse(st.tired)
        st.work(2, 1, cfg)
        assertTrue(st.tired)
        assertEquals(3_600, s.ticksPerLevel)
        assertEquals(listOf(2, 3, 4, 8), s.bedFactors)
        assertEquals(1_200, s.healerMinutesAtLevel1)
        assertEquals(120, s.healerMinutesAtLevel100)
    }

    @Test
    fun `CD-CFG-04 one-tick steps make a Lv1 worker Tired on tick 7,200 at 7,200 ticks per level`() {
        val cfg = s.copy(ticksPerLevel = 7_200)
        val st = Stamina()
        repeat(7_199) { st.work(1, 1, cfg) }
        assertFalse(st.tired)
        st.work(1, 1, cfg)
        assertTrue(st.tired)
    }

    @Test
    fun `CD-CFG-04 the configured healer times and bed factors set the recovery`() {
        val cfg = s.copy(healerMinutesAtLevel1 = 600, healerMinutesAtLevel100 = 60, bedFactors = listOf(1, 2, 5, 10))
        near(600.0, StaminaMath.healerMinutes(1, cfg), 0.0)
        near(60.0, StaminaMath.healerMinutes(100, cfg), 0.0)
        assertEquals(10, StaminaMath.bedFactor(4, cfg))
        val st = Stamina(0.0, tired = true)
        st.rest(3_600, 10, RecoveryRule.WORKER, bedTier = 4, cfg)
        assertFalse(st.tired)
    }
}
