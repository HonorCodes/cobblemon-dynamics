package codes.honor.cobblemondynamics.handler.farming

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SaplingRulesTest {
    /** The sapling at (0, 0) and the cells at the given x and z offsets hold the same sapling. */
    private fun saplings(vararg others: Pair<Int, Int>): (Int, Int) -> Boolean {
        val cells = others.toSet() + (0 to 0)
        return { dx, dz -> (dx to dz) in cells }
    }

    /** The values of the default tag that [SaplingRules.NEEDS_FOUR] names. */
    private fun needsFour(): List<String> {
        val path = SaplingRules.NEEDS_FOUR.location().path
        val file = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block/$path.json")
        return JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("values").map { it.asString }
    }

    @Test
    fun `CD-LUM-23 a dark oak sapling grows only in a complete 2 x 2 of its kind`() {
        // The Cultivator reads the Lumberjack's tag, so the 2 x 2 check and the replant take their saplings from one source.
        assertEquals(ResourceLocation.parse("cobblemon_dynamics:lumberjack/needs_four"), SaplingRules.NEEDS_FOUR.location())
        assertTrue("minecraft:dark_oak_sapling" in needsFour(), "the tag lacks the dark oak sapling")
        assertFalse("minecraft:spruce_sapling" in needsFour(), "a spruce also grows alone")
        assertFalse(SaplingRules.inFullSquare(saplings()), "a sapling alone")
        assertFalse(SaplingRules.inFullSquare(saplings(1 to 0, 0 to 1)), "three of four")
        assertFalse(SaplingRules.inFullSquare(saplings(1 to 0, 0 to 1, -1 to -1)), "three of four and a corner of another square")
        assertFalse(SaplingRules.inFullSquare(saplings(1 to 0, 2 to 0, 1 to 1, 2 to 1)), "a full square beside the sapling")
        // The sapling at each corner of its square.
        for ((x, z) in listOf(0 to 0, -1 to 0, 0 to -1, -1 to -1)) {
            val square = saplings(x to z, x + 1 to z, x to z + 1, x + 1 to z + 1)
            assertTrue(SaplingRules.inFullSquare(square), "the square from ($x, $z)")
        }
    }
}
