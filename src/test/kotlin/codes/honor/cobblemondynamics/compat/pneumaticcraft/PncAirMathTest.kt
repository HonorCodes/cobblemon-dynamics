package codes.honor.cobblemondynamics.compat.pneumaticcraft

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PncAirMathTest {
    private val atLevel100 = PncAirParams.of(JsonObject()).airPerTickAt100

    @Test
    fun `CD-AIR-07 Tailwind adds ceil of 50 times L over 100 mL per tick`() {
        assertEquals(1, PncAirMath.airPerTick(atLevel100, level = 1))
        assertEquals(25, PncAirMath.airPerTick(atLevel100, level = 50))
        assertEquals(50, PncAirMath.airPerTick(atLevel100, level = 100))
        assertEquals(50, PncAirMath.airPerTick(atLevel100, level = 150))
    }

    /** PneumaticCraft's pressure is the air over the volume, in bar. A tier 1 machine has a danger pressure of 5 bar. */
    @Test
    fun `CD-AIR-08 a machine a tenth of a bar below its danger pressure gets only the air that keeps it below`() {
        assertEquals(50, PncAirMath.allowed(air = 50, stored = 4_900, volume = 1_000, dangerPressure = 5f))
        assertEquals(9, PncAirMath.allowed(air = 50, stored = 490, volume = 100, dangerPressure = 5f))
        assertEquals(0, PncAirMath.allowed(air = 50, stored = 500, volume = 100, dangerPressure = 5f))
        assertEquals(0, PncAirMath.allowed(air = 50, stored = 700, volume = 100, dangerPressure = 5f))
    }

    @Test
    fun `CD-AIR-08 worked tick after tick, a machine never reaches its danger pressure`() {
        for (volume in listOf(100, 1_000, 3_333, 10_000)) {
            var stored = 0
            repeat(5_000) {
                stored += PncAirMath.allowed(air = 50, stored = stored, volume = volume, dangerPressure = 5f)
                assertTrue(stored.toFloat() / volume.toFloat() < 5f, "volume $volume reached ${stored.toFloat() / volume} bar")
            }
            assertEquals(0, PncAirMath.allowed(air = 50, stored = stored, volume = volume, dangerPressure = 5f), "volume $volume")
        }
    }

    @Test
    fun `CD-AIR-08 a machine in vacuum fills toward its danger pressure, and a volume of 0 gets no air`() {
        assertEquals(50, PncAirMath.allowed(air = 50, stored = -1_000, volume = 1_000, dangerPressure = 5f))
        assertEquals(0, PncAirMath.allowed(air = 50, stored = 0, volume = 0, dangerPressure = 5f))
    }
}
