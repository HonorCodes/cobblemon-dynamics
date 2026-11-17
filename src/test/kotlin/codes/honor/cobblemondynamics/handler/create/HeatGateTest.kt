package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.role.BuiltInRoles
import codes.honor.cobblemondynamics.role.RoleSelection
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HeatGateTest {
    private val superheater = BuiltInRoles.parse("superheater")

    @Test
    fun `CD-SUP-01 CD-SUP-04 the default Superheater gives kindled at level 49 and seething at level 50`() {
        assertEquals("kindled", HeatGate.heatFor(superheater.params, 49))
        assertEquals("seething", HeatGate.heatFor(superheater.params, 50))
        assertEquals("kindled", HeatGate.heatFor(superheater.params, 1))
        assertEquals("seething", HeatGate.heatFor(superheater.params, 100))
    }

    @Test
    fun `CD-FIX-07 CD-BRN-03 a heat user of the tag above the burner gives demand, as a basin does, and a stone block gives none`() {
        assertTrue(HeatGate.hasConsumer(createRule = false, heatUserAbove = true), "a cooking pot above the burner gives no demand")
        assertTrue(HeatGate.hasConsumer(createRule = true, heatUserAbove = false), "a basin above the burner gives no demand")
        assertFalse(HeatGate.hasConsumer(createRule = false, heatUserAbove = false), "a stone block above the burner gives demand")
    }

    /** The ids of Create: Diesel Generators 1.21.1-1.3.15 and Create: New Age 1.2.0 come from the blockstates of their jars. */
    @Test
    fun `CD-FIX-07 the heat users tag holds Create's basin and tank and the heat users of other mods, each optional`() {
        val file =
            File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block/superheater/heat_users.json")
        val values = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("values").map { it.asJsonObject }
        val expected = setOf(
            "create:basin",
            "create:fluid_tank",
            "farmersdelight:cooking_pot",
            "farmersdelight:skillet",
            "createdieselgenerators:distillation_tank",
            "create_new_age:heat_pipe",
        )
        assertEquals(expected, values.map { it["id"].asString }.toSet())
        for (value in values) assertEquals(false, value["required"]?.asBoolean, "${value["id"]} is required")
    }

    @Test
    fun `CD-BRN-01 CD-BRN-05 the default Superheater gives kindled at every level below 50`() {
        for (level in listOf(1, 10, 25, 49)) assertEquals("kindled", HeatGate.heatFor(superheater.params, level), "level $level")
    }

    @Test
    fun `CD-SUP-02 a Fire-type Charmander may not superheat and a Dragon-type Dratini may`() {
        assertEquals(setOf("dragon"), superheater.types)
        assertFalse(RoleSelection.isEligible(superheater, listOf("fire")), "Charmander")
        assertTrue(RoleSelection.isEligible(superheater, listOf("dragon")), "Dratini")
    }

    @Test
    fun `a role with no min_level gives its heat at every level, and one with no heat below it gives none there`() {
        val always = JsonParser.parseString("""{ "heat": "seething" }""").asJsonObject
        assertEquals("seething", HeatGate.heatFor(always, 1))
        val gated = JsonParser.parseString("""{ "heat": "seething", "min_level": 30 }""").asJsonObject
        assertNull(HeatGate.heatFor(gated, 29))
        assertEquals("seething", HeatGate.heatFor(gated, 30))
        assertNull(HeatGate.heatFor(JsonParser.parseString("{}").asJsonObject, 50))
    }
}
