package codes.honor.cobblemondynamics.compat.mekanism

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeaterMathTest {
    @Test
    fun `CD-HTR-04 level 1 gives 15, level 50 gives 750 and level 100 gives 1,500 J per tick`() {
        val heat = HeaterParams.of(BuiltInRoles.parse("heater_mekanism").params).heatPerTickAt100
        assertEquals(1_500.0, heat)
        assertEquals(15, HeaterMath.heatPerTick(heat, 1))
        assertEquals(750, HeaterMath.heatPerTick(heat, 50))
        assertEquals(1_500, HeaterMath.heatPerTick(heat, 100))
        // ceil: a fraction of a joule rounds up.
        assertEquals(8, HeaterMath.heatPerTick(750.0, 1))
    }

    @Test
    fun `CD-HTR-04 a missing or broken parameter takes 1,500`() {
        assertEquals(1_500.0, HeaterParams.of(JsonObject()).heatPerTickAt100)
        val broken = JsonObject().apply { addProperty("heat_per_tick_at_level_100", "hot") }
        assertEquals(1_500.0, HeaterParams.of(broken).heatPerTickAt100)
    }

    @Test
    fun `CD-HTR-05 no capacitor and 3,000 K give no demand, and 2,999 K gives demand`() {
        assertFalse(HeaterMath.hasDemand(emptyList()), "an unformed plant has no capacitor")
        assertFalse(HeaterMath.hasDemand(listOf(3_000.0)))
        assertFalse(HeaterMath.hasDemand(listOf(3_400.0)))
        assertTrue(HeaterMath.hasDemand(listOf(2_999.0)))
        assertTrue(HeaterMath.hasDemand(listOf(3_100.0, 300.0)), "a capacitor below 3,000 K takes heat")
    }

    @Test
    fun `CD-HTR-02 CD-HTR-03 only a block of the tag is a target, whatever heat it exposes`() {
        assertTrue(HeaterMath.isTarget(inTag = true) { true })
        assertFalse(HeaterMath.isTarget(inTag = true) { false })
        // A fission or fusion port, the Heat Generator and the Entangloporter expose mekanism:heat_handler, outside the tag.
        assertFalse(HeaterMath.isTarget(inTag = false) { true })
        assertFalse(HeaterMath.isTarget(inTag = false) { error("a block outside the tag needs no capability lookup") })
    }
}
