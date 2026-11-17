package codes.honor.cobblemondynamics.handler.water

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** The Freezer's tag of the blocks that need the water beside them (R84). */
class FreezerTagFilesTest {
    private val file = File(
        System.getProperty("cd.projectDir"),
        "src/main/resources/data/cobblemon_dynamics/tags/block/needs_adjacent_water.json",
    )

    /** Each entry with its `required` flag. A plain string entry is required. */
    private fun entries(): Map<String, Boolean> = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("values").associate {
        if (it.isJsonPrimitive) {
            it.asString to true
        } else {
            it.asJsonObject.get("id").asString to (it.asJsonObject.get("required")?.asBoolean ?: true)
        }
    }

    @Test
    fun `CD-FRZ-15 the tag holds sugar cane, the corals, the conduit and Create's encased fan as an optional entry`() {
        val expected = mapOf(
            "minecraft:sugar_cane" to true,
            "#minecraft:coral_blocks" to true,
            "#minecraft:corals" to true,
            "#minecraft:wall_corals" to true,
            "minecraft:conduit" to true,
            "create:encased_fan" to false,
        )
        assertEquals(expected, entries())
    }
}
