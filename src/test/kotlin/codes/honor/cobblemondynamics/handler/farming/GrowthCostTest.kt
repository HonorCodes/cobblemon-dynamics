package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.role.BuiltInRoles
import codes.honor.cobblemondynamics.stamina.Stamina
import codes.honor.cobblemondynamics.stamina.StaminaMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GrowthCostTest {
    @Test
    fun `CD-CUL-06 a use drains its ticks beyond the Working drain`() {
        val settings = EngineSettings.DEFAULT
        assertEquals(36_000.0, StaminaMath.capacityTicks(10, settings))
        val params = CultivatorParams.of(BuiltInRoles.parse("cultivator").params)
        val stamina = Stamina()
        // 40 ticks of Working, then the one use of those ticks.
        stamina.work(40, 10, settings)
        stamina.work(params.staminaTicksPerUse.toLong(), 10, settings)
        assertEquals(1.0 - 140.0 / 36_000, stamina.fraction, 1e-12)
    }

    /** One use tick of a level 100 worker: the target and the 10 crops of its area, so 11 growth calls (CD-CRP-25). */
    private fun drainOfOneUse(calls: List<Boolean>): Double {
        val settings = EngineSettings.DEFAULT
        val params = CultivatorParams.of(BuiltInRoles.parse("cultivator").params)
        assertEquals(11, 1 + CropArea.count(100, AreaParams.of(BuiltInRoles.parse("cultivator").params)))
        val stamina = Stamina()
        stamina.work(40, 100, settings)
        val cost = CropArea.cost(calls.any { it }, params.staminaTicksPerUse)
        if (cost > 0) stamina.work(cost.toLong(), 100, settings)
        return (1.0 - stamina.fraction) * StaminaMath.capacityTicks(100, settings)
    }

    @Test
    fun `CD-CUL-12 a use that grows 11 crops drains 100 ticks beyond Working, and one that grows none drains nothing`() {
        assertEquals(140.0, drainOfOneUse(List(11) { true }), 1e-6)
        assertEquals(140.0, drainOfOneUse(List(11) { it == 7 }), 1e-6)
        assertEquals(40.0, drainOfOneUse(List(11) { false }), 1e-6)
    }
}
