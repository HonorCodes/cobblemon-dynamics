package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CD-ASG-25: the way out of a grown tree passes that tree only, never a wall of logs or of placed leaves. */
class TreeWayTest {
    private enum class Cell { AIR, TRUNK, CABIN_LOG, NATURAL_LEAF, PLACED_LEAF, STONE }

    /** A plain grid: air above y = 63, stone at and below it, and the given cells. */
    private class Grid(val cells: Map<BlockPos, Cell>) {
        fun at(pos: BlockPos): Cell = cells[pos] ?: if (pos.y <= 63) Cell.STONE else Cell.AIR
    }

    private val tree = BlockPos(0, 64, 0)

    /** An oak trunk 5 logs high at [tree], natural leaves around its top, and a log cabin wall with its room east of the tree. */
    private fun scene(): Grid {
        val cells = HashMap<BlockPos, Cell>()
        for (y in 0 until 5) cells[tree.above(y)] = Cell.TRUNK
        for (dx in -2..2) {
            for (dz in -2..2) if (dx != 0 || dz != 0) cells[tree.offset(dx, 3, dz)] = Cell.NATURAL_LEAF
        }
        // A cabin wall at x = 3, a log of the same kind, which touches no log of the tree.
        for (y in 64..66) for (z in -3..3) cells[BlockPos(3, y, z)] = Cell.CABIN_LOG
        // A hedge of placed leaves at z = -3, west of the cabin.
        for (y in 64..65) for (x in -3..2) cells[BlockPos(x, y, -3)] = Cell.PLACED_LEAF
        return Grid(cells)
    }

    private fun trunkOf(grid: Grid): Set<BlockPos> = TreeWay.trunk(tree, { grid.at(it) == Cell.TRUNK || grid.at(it) == Cell.CABIN_LOG })

    private fun passable(grid: Grid, trunk: Set<BlockPos>): (BlockPos) -> Boolean = { pos ->
        val cell = grid.at(pos)
        TreeWay.passes(cell == Cell.AIR, cell == Cell.NATURAL_LEAF) { pos in trunk }
    }

    @Test
    fun `CD-ASG-25 the trunk is the logs joined to the changed target, and no log of a cabin beside it`() {
        val grid = scene()
        assertEquals((0 until 5).map { tree.above(it) }.toSet(), trunkOf(grid))
        // A cell that holds no trunk, as the base after a fell, has none.
        assertEquals(emptySet<BlockPos>(), TreeWay.trunk(tree.east(), { grid.at(it) == Cell.TRUNK }))
        // The search stops at its cap.
        assertEquals(3, TreeWay.trunk(tree, { grid.at(it) == Cell.TRUNK }, max = 3).size)
    }

    @Test
    fun `CD-ASG-25 the fill passes the tree's own logs and natural leaves, and never a cabin wall or placed leaves`() {
        val grid = scene()
        val passable = passable(grid, trunkOf(grid))
        assertTrue(passable(tree.above(1)), "the tree's own log")
        assertTrue(passable(tree.offset(1, 3, 1)), "a natural leaf")
        assertTrue(passable(tree.east()), "air")
        assertFalse(passable(BlockPos(3, 64, 0)), "the cabin wall")
        assertFalse(passable(BlockPos(0, 64, -3)), "a placed leaf")
        assertFalse(passable(tree.below()), "the floor")
        // A worker in the trunk at y = 64 reaches no cell inside the cabin (x 4 and up) nor behind the hedge (z -4 and down).
        val reached = Unstick.reachableCells(tree, null, passable)
        assertTrue(reached.isNotEmpty())
        assertTrue(reached.none { it.x >= 3 }, "a cell in or behind the cabin wall: ${reached.filter { it.x >= 3 }}")
        assertTrue(reached.none { it.z <= -3 }, "a cell in or behind the hedge: ${reached.filter { it.z <= -3 }}")
    }

    @Test
    fun `CD-ASG-25 the trunk is asked last, so open cells cost no search`() {
        var asked = 0
        assertTrue(TreeWay.passes(empty = true, naturalLeaf = false) { asked++ > 0 })
        assertTrue(TreeWay.passes(empty = false, naturalLeaf = true) { asked++ > 0 })
        assertEquals(0, asked)
        assertFalse(TreeWay.passes(empty = false, naturalLeaf = false) { asked++ > 0 })
        assertEquals(1, asked)
    }
}
