package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.tree.CellFacts
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CD-ASG-25: the way out of a grown tree passes that tree only, never a wall of logs or of placed leaves. */
class TreeWayTest {
    private fun rl(id: String) = ResourceLocation.parse(id)

    private val oakLog = CellFacts(rl("minecraft:oak_log"), logTarget = true, anyLog = true)
    private val mangroveLog = CellFacts(rl("minecraft:mangrove_log"), logTarget = true, anyLog = true, never = true)
    private val root = CellFacts(rl("minecraft:mangrove_roots"))

    private fun leaf(id: String, placed: Boolean = false) = CellFacts(rl(id), leaves = true, persistent = placed)

    /** A plain grid: air above y = 63, stone at and below it, and the given cells. */
    private class Grid(val cells: MutableMap<BlockPos, CellFacts> = HashMap()) {
        fun at(pos: BlockPos): CellFacts = cells[pos] ?: if (pos.y <= 63) STONE else CellFacts.AIR

        companion object {
            val STONE = CellFacts(ResourceLocation.parse("minecraft:stone"))
        }
    }

    private val tree = BlockPos(0, 64, 0)

    /** A crown of natural leaves 2 out from the column at [top]. */
    private fun crown(grid: Grid, top: BlockPos, id: String) {
        for (dx in -2..2) {
            for (dz in -2..2) {
                for (dy in -1..1) if (dx != 0 || dz != 0 || dy == 1) grid.cells[top.offset(dx, dy, dz)] = leaf(id)
            }
        }
    }

    /** An oak trunk 5 logs high at [tree] with its crown, a cabin wall of oak logs at x = 3, and a hedge of placed leaves at z = -3. */
    private fun oakScene(): Grid {
        val grid = Grid()
        for (y in 0 until 5) grid.cells[tree.above(y)] = oakLog
        crown(grid, tree.above(4), "minecraft:oak_leaves")
        for (y in 64..66) for (z in -3..3) grid.cells[BlockPos(3, y, z)] = oakLog
        for (y in 64..65) for (x in -3..2) grid.cells[BlockPos(x, y, -3)] = leaf("minecraft:oak_leaves", placed = true)
        return grid
    }

    private fun passable(grid: Grid, trunk: Set<BlockPos>): (BlockPos) -> Boolean = { pos ->
        val cell = grid.at(pos)
        TreeWay.passes(cell.air, cell.leaves && !cell.persistent) { pos in trunk }
    }

    @Test
    fun `CD-ASG-25 the trunk is the logs of the tree search, and no log of a cabin beside it`() {
        val grid = oakScene()
        assertEquals((0 until 5).map { tree.above(it) }.toSet(), TreeWay.treeTrunk(grid::at, tree, tree))
        // A cell that holds no log, as the base after a fell, has none.
        assertEquals(emptySet<BlockPos>(), TreeWay.treeTrunk(grid::at, tree.east(), tree))
        assertNull(TreeWay.searchStart(tree.east(), grid::at))
    }

    @Test
    fun `CD-ASG-25 the fill passes the tree's own logs and natural leaves, and never a cabin wall or placed leaves`() {
        val grid = oakScene()
        val passable = passable(grid, TreeWay.treeTrunk(grid::at, tree, tree))
        assertTrue(passable(tree.above(1)), "the tree's own log")
        assertTrue(passable(tree.offset(1, 4, 1)), "a natural leaf")
        assertTrue(passable(tree.east()), "air")
        assertFalse(passable(BlockPos(3, 64, 0)), "the cabin wall")
        assertFalse(passable(BlockPos(0, 64, -3)), "a placed leaf")
        assertFalse(passable(tree.below()), "the floor")
        val reached = Unstick.reachableCells(tree, null, passable)
        assertTrue(reached.isNotEmpty())
        assertTrue(reached.none { it.x >= 3 }, "a cell in or behind the cabin wall: ${reached.filter { it.x >= 3 }}")
        assertTrue(reached.none { it.z <= -3 }, "a cell in or behind the hedge: ${reached.filter { it.z <= -3 }}")
    }

    /**
     * Review: a mangrove grows its trunk above its roots, and its logs are in the never tag of the fell. The search starts at the first log
     * within 3 blocks above a root, or above air over roots, and takes the roots joined to the trunk, but no clump of roots apart from it.
     */
    @Test
    fun `CD-ASG-25 a mangrove's trunk starts above its roots and takes the roots joined to it`() {
        val grid = Grid()
        for (dx in -1..1) for (dz in -1..1) grid.cells[tree.offset(dx, 0, dz)] = root
        grid.cells[tree.above()] = root
        for (y in 2 until 7) grid.cells[tree.above(y)] = mangroveLog
        crown(grid, tree.above(6), "minecraft:mangrove_leaves")
        val apart = BlockPos(3, 64, 3)
        grid.cells[apart] = root
        assertEquals(tree.above(2), TreeWay.searchStart(tree, grid::at), "from a root")
        val trunk = TreeWay.treeTrunk(grid::at, tree, tree)
        assertTrue((2 until 7).all { tree.above(it) in trunk }, "the logs: $trunk")
        assertTrue(tree.above() in trunk && tree in trunk && tree.offset(1, 0, 1) in trunk, "the roots joined to the trunk: $trunk")
        assertFalse(apart in trunk, "a clump of roots apart from the trunk")
        // Air over roots starts the same way.
        grid.cells[tree.above()] = CellFacts.AIR
        assertEquals(tree.above(2), TreeWay.searchStart(tree.above(), grid::at), "from air over a root")
        // A worker in the roots reaches out through them.
        val reached = Unstick.reachableCells(tree, null, passable(grid, TreeWay.treeTrunk(grid::at, tree, tree)))
        assertTrue(reached.any { it.x == 2 || it.z == 2 }, "the worker stays in the roots: $reached")
    }

    @Test
    fun `CD-ASG-25 the cap keeps the cells nearest the worker first`() {
        val logs = (0 until 10).map { tree.above(it) }.toSet()
        val near = tree.above(9)
        val trunk = TreeWay.trunk(logs.sortedBy { it.distSqr(near) }, { it in logs }, max = 3)
        assertEquals(setOf(tree.above(9), tree.above(8), tree.above(7)), trunk)
        assertEquals(emptySet<BlockPos>(), TreeWay.trunk(listOf(tree.east()), { it in logs }))
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
