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
}
