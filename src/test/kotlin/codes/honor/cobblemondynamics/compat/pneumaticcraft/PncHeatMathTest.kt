package codes.honor.cobblemondynamics.compat.pneumaticcraft

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** R87: the PneumaticCraft Heater's formula, apart from the Mekanism Heater's `HeaterMathTest`. */
class PncHeatMathTest {
    private val lava = PncHeaterParams.of(JsonObject())

    private fun heat(temperature: Double, level: Int): Double = PncHeatMath.heatPerTick(lava, temperature, level)

    @Test
    fun `CD-HTR-07 the heat of one lava block, scaled by level`() {
        assertEquals(10.0, heat(temperature = 300.0, level = 100), 1e-9)
        assertEquals(2.5, heat(temperature = 800.0, level = 50), 1e-9)
        assertEquals(0.0, heat(temperature = 1_300.0, level = 100), 1e-9)
        assertEquals(0.1, heat(temperature = 300.0, level = 1), 1e-9)
    }

    /** A Heater never cools a machine hotter than the lava block that it copies. */
    @Test
    fun `CD-HTR-07 a machine at or above 1,300 K gets no heat and has no demand`() {
        assertEquals(0.0, heat(temperature = 1_800.0, level = 100), 1e-9)
        assertFalse(PncHeatMath.hasDemand(lava, temperature = 1_300.0, level = 100))
        assertTrue(PncHeatMath.hasDemand(lava, temperature = 1_299.0, level = 100))
    }

    @Test
    fun `CD-HTR-07 levels above 100 count as 100`() {
        assertEquals(heat(temperature = 300.0, level = 100), heat(temperature = 300.0, level = 250), 1e-9)
    }

    @Test
    fun `CD-HTR-07 the defaults are one lava block, and a key of the wrong type takes its default`() {
        assertEquals(PncHeaterParams(1_300.0, 100.0), lava)
        val params = JsonParser.parseString("""{"source_temperature": "hot", "thermal_resistance": 50}""").asJsonObject
        assertEquals(PncHeaterParams(1_300.0, 50.0), PncHeaterParams.of(params))
    }

    /** A resistance of 0 or less would divide by 0 or reverse the flow, so it gives no heat. */
    @Test
    fun `CD-HTR-07 a resistance of 0 gives no heat`() {
        assertEquals(0.0, PncHeatMath.heatPerTick(PncHeaterParams(1_300.0, 0.0), temperature = 300.0, level = 100), 1e-9)
    }
}
