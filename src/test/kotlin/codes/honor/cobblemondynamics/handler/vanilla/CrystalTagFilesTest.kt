package codes.honor.cobblemondynamics.handler.vanilla

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The Crystal Tender's tags (CD-MCH-02, CD-MCH-03, CD-MCH-04). [codes.honor.cobblemondynamics.build.CompatTagFilesTest] reads every tag. */
class CrystalTagFilesTest {
    private val dir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block")

    /** Each entry of [name] by its id or `#tag`, with whether it loads without its mod. */
    private fun entries(name: String): Map<String, Boolean> =
        JsonParser.parseString(File(dir, "$name.json").readText()).asJsonObject.getAsJsonArray("values").associate { value ->
            if (value.isJsonPrimitive) {
                value.asString to false
            } else {
                value.asJsonObject["id"].asString to (value.asJsonObject["required"]?.asBoolean == false)
            }
        }

    /** Quark 4.1-488 grows its unwaxed corundum in `CorundumBlock.randomTick`, and wax stops it, so no waxed block is a target. */
    private val corundum = listOf("red", "orange", "yellow", "green", "blue", "indigo", "violet", "white", "black").map {
        "quark:${it}_corundum"
    }

    @Test
    fun `CD-MCH-02 the target tag holds the budding blocks, the tumblestones, the citrine and Quark's unwaxed corundum`() {
        val targets = entries("crystal_tender_targets")
        for (tag in listOf("#c:budding_blocks", "#cobblemon_dynamics:crystal/self_growing", "#cobblemon_dynamics:crystal/scheduled_tick")) {
            assertTrue(tag in targets, "crystal_tender_targets lacks $tag")
        }
        for (id in corundum) assertEquals(true, targets[id], "$id is missing or required")
        assertTrue(targets.keys.none { it.startsWith("quark:waxed_") }, "a waxed corundum never grows")
        assertEquals(3 + corundum.size, targets.size, "crystal_tender_targets holds $targets")
    }

    /** Cobblemon 1.8.4 puts these nine in `#c:buds` too, and each grows into its next stage beside lava or a magma block. */
    @Test
    fun `CD-MCH-03 the self-growing tag holds Cobblemon's nine budding tumblestones`() {
        val expected = listOf("", "sky_", "black_").flatMap { kind ->
            listOf("small", "medium", "large").map { size -> "cobblemon:${size}_budding_${kind}tumblestone" }
        }
        assertEquals(expected.toSet(), entries("crystal/self_growing").keys)
    }

    @Test
    fun `CD-MCH-04 Unusual End's budding citrine gets the scheduled tick, and its two buds count as buds, each one optional`() {
        assertEquals(mapOf("unusualend:budding_citrine" to true), entries("crystal/scheduled_tick"))
        assertEquals(mapOf("unusualend:citrine_bud" to true, "unusualend:large_citrine_bud" to true), entries("crystal/buds"))
    }
}
