package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaplingRulesTest {
    /** The sapling at (0, 0) and the cells at the given x and z offsets hold the same sapling. */
    private fun saplings(vararg others: Pair<Int, Int>): (Int, Int) -> Boolean {
        val cells = others.toSet() + (0 to 0)
        return { dx, dz -> (dx to dz) in cells }
    }

    @Test
    fun `CD-LUM-23 a dark oak sapling grows only in a complete 2 x 2 of its kind`() {
        assertTrue(ResourceLocation.parse("minecraft:dark_oak_sapling") in SaplingRules.NEEDS_FOUR)
        assertFalse(ResourceLocation.parse("minecraft:spruce_sapling") in SaplingRules.NEEDS_FOUR, "a spruce also grows alone")
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
