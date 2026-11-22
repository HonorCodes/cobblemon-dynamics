package codes.honor.cobblemondynamics.handler.farming

import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
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

    @Test
    fun `CD-CUL-13 each sapling of a 2 x 2 resolves to its least corner, and a lone sapling to itself`() {
        val corner = BlockPos(4, 64, 7)
        val square = SaplingRules.square(corner).toSet()
        assertEquals(setOf(corner, corner.east(), corner.south(), corner.east().south()), square)
        for (cell in square) assertEquals(corner, SaplingRules.corner(cell) { it in square }, "the spruce sapling at $cell")
        val lone = BlockPos(10, 64, 10)
        assertEquals(lone, SaplingRules.corner(lone) { it == lone })
        // Three of four make no square.
        val three = square - corner.east().south()
        for (cell in three) assertEquals(cell, SaplingRules.corner(cell) { it in three })
    }

    /**
     * CD-CUL-13, CD-LSH-36: in two overlapping squares, as in a 3 x 2 of saplings, a middle sapling lies in both and names itself, and an
     * end sapling names the corner of its one square.
     */
    @Test
    fun `CD-CUL-13 a sapling in two squares resolves to itself, and one in exactly one square to that square's corner`() {
        val start = BlockPos(0, 64, 0)
        val cells = (0..2).flatMap { x -> (0..1).map { z -> start.offset(x, 0, z) } }.toSet()
        assertEquals(start, SaplingRules.corner(start) { it in cells })
        assertEquals(start, SaplingRules.corner(start.offset(0, 0, 1)) { it in cells })
        assertEquals(start.offset(1, 0, 0), SaplingRules.corner(start.offset(1, 0, 0)) { it in cells })
        assertEquals(start.offset(1, 0, 1), SaplingRules.corner(start.offset(1, 0, 1)) { it in cells })
        assertEquals(start.offset(1, 0, 0), SaplingRules.corner(start.offset(2, 0, 0)) { it in cells })
        assertEquals(start.offset(1, 0, 0), SaplingRules.corner(start.offset(2, 0, 1)) { it in cells })
    }

    /**
     * CD-LSH-36: the corner is a fixed point over rows 2 wide, rows 1 wide, fields and scattered squares. A binding moved to the corner
     * of its cell is at its own corner, so it never walks on along a row of saplings.
     */
    @Test
    fun `CD-LSH-36 the corner of every sapling of a row or a field resolves to itself`() {
        val origin = BlockPos(-8, 70, 13)
        fun field(width: Int, depth: Int) = (0 until width).flatMap { x -> (0 until depth).map { z -> origin.offset(x, 0, z) } }.toSet()
        val layouts = mapOf(
            "a row 2 wide" to field(9, 2),
            "a row 2 deep" to field(2, 9),
            "a row 1 wide" to field(9, 1),
            "a field 5 x 5" to field(5, 5),
            "a field 3 x 4" to field(3, 4),
            "an L" to field(4, 2) + field(2, 4),
            "a checkerboard of squares" to (field(6, 6).filter { (it.x - origin.x) / 2 % 2 == (it.z - origin.z) / 2 % 2 }).toSet(),
        )
        for ((name, cells) in layouts) {
            val same = { pos: BlockPos -> pos in cells }
            for (cell in cells) {
                val corner = SaplingRules.corner(cell, same)
                assertTrue(corner in cells, "$name: $cell resolves to $corner, which holds no sapling")
                assertEquals(corner, SaplingRules.corner(corner, same), "$name: the corner $corner of $cell moves on")
            }
        }
        // In a row 2 wide, only the two cells at each end move, and the two ends name different corners.
        val row = field(9, 2)
        val moved = row.filter { SaplingRules.corner(it) { pos -> pos in row } != it }.toSet()
        assertEquals(setOf(origin.offset(0, 0, 1), origin.offset(8, 0, 0), origin.offset(8, 0, 1)), moved)
    }

    @Test
    fun `CD-ASG-24 a lone sapling's tree fills its cell, and a square's tree fills the four cells of the square`() {
        val lone = BlockPos(0, 64, 0)
        assertEquals(listOf(lone), SaplingRules.growthCells(lone) { it == lone })
        val corner = BlockPos(5, 64, 5)
        val square = SaplingRules.square(corner)
        assertEquals(square, SaplingRules.growthCells(corner) { it in square })
        // A cell of the square with another block is no growth cell.
        assertEquals(listOf(corner, corner.east()), SaplingRules.growthCells(corner) { it == corner.east() })
    }
}
