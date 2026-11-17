package codes.honor.cobblemondynamics.compat.mekanism

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HeaterTagFilesTest {
    private val tag = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block/heater_targets.json")

    /** Each entry by id, with its `required` flag. */
    private fun entries(): Map<String, Boolean?> = JsonParser.parseString(tag.readText()).asJsonObject.getAsJsonArray("values")
        .associate { value ->
            if (value.isJsonPrimitive) value.asString to null else value.asJsonObject.let { it["id"].asString to it["required"]?.asBoolean }
        }

    @Test
    fun `CD-HTR-02 the Heater's tag holds the Thermal Evaporation valve and the boiler valve as optional entries`() {
        val entries = entries()
        assertEquals(setOf("mekanism:thermal_evaporation_valve", "mekanism:boiler_valve"), entries.keys)
        for ((id, required) in entries) assertEquals(false, required, "$id is required")
    }

    @Test
    fun `CD-HTR-03 the tag holds no reactor port, no Heat Generator and no Entangloporter`() {
        val refused = listOf(
            "mekanismgenerators:fission_reactor_port",
            "mekanismgenerators:fusion_reactor_port",
            "mekanismgenerators:heat_generator",
            "mekanism:quantum_entangloporter",
        )
        val entries = entries().keys
        for (id in refused) assertTrue(id !in entries && "#$id" !in entries, "the tag holds $id")
        assertTrue(entries.none { it.startsWith("#") }, "a tag inside the Heater's tag could bring any block: $entries")
    }
}
