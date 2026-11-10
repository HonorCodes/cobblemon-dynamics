package codes.honor.cobblemondynamics.handler.capability

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PumpParamsTest {
    private val pump: PumpParams by lazy { PumpParams.of(BuiltInRoles.parse("pump").params) }

    @Test
    fun `CD-PMP-03 the default Pump offers 5 times its level in mB per tick`() {
        assertEquals(5, pump.mbPerTick(1))
        assertEquals(50, pump.mbPerTick(10))
        assertEquals(250, pump.mbPerTick(50))
        assertEquals(500, pump.mbPerTick(100))
    }

    @Test
    fun `CD-PMP-03 a role file without the rate takes the default, and a pack's rate applies as written`() {
        assertEquals(500, PumpParams.of(JsonObject()).mbPerTick(100))
        assertEquals(500, PumpParams.of(JsonParser.parseString("""{ "mb_per_level": "five" }""").asJsonObject).mbPerTick(100))
        assertEquals(1_000, PumpParams.of(JsonParser.parseString("""{ "mb_per_level": 10 }""").asJsonObject).mbPerTick(100))
        assertEquals(8, PumpParams.of(JsonParser.parseString("""{ "mb_per_level": 2.5 }""").asJsonObject).mbPerTick(3))
    }

    @Test
    fun `CD-CFG-13 the rate has no cap beyond the role file, and stays inside an int`() {
        assertEquals(750, pump.mbPerTick(150))
        assertEquals(0, PumpParams.of(JsonParser.parseString("""{ "mb_per_level": -5 }""").asJsonObject).mbPerTick(100))
        assertEquals(Int.MAX_VALUE, PumpParams.of(JsonParser.parseString("""{ "mb_per_level": 1e12 }""").asJsonObject).mbPerTick(100))
    }
}
