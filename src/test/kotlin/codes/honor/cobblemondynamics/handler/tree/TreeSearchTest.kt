package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.STRIPPED_OAK_LOG
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ADR-0017, decision 3: the tree search over grids of plain facts. */
class TreeSearchTest {
    private val base = BlockPos(0, 64, 0)

    private fun find(grid: TreeGrid, start: BlockPos = base): Tree? = TreeSearch.find(grid.read, start, MAX_LOGS, MAX_LEAVES)

    @Test
    fun `CD-LUM-04 a fancy oak gives all its logs, through corners and edges, and a stripped log beside it stays out`() {
        val grid = TreeGrid()
        val oak = grid.fancyOak(base)
        val stripped = grid.log(base.east(), STRIPPED_OAK_LOG)
        val tree = find(grid, base.above(3)) ?: error("the fancy oak gives no tree")
        assertEquals(oak.logs.toSet(), tree.logs)
        assertEquals(base, tree.base)
        assertFalse(stripped in tree.logs, "the stripped log joined the tree")
        assertEquals(oak.leaves.toSet(), tree.leaves)
    }

    @Test
    fun `CD-LUM-04 a tree of 257 logs gives no tree, and one of 256 logs gives a tree`() {
        for ((height, found) in listOf(MAX_LOGS to true, MAX_LOGS + 1 to false)) {
            val grid = TreeGrid()
            grid.trunk(base, height)
            grid.layer(base.above(height), 1)
            val tree = find(grid)
            assertEquals(found, tree != null, "a column of $height logs")
            if (tree != null) assertEquals(height, tree.logs.size)
        }
    }

    @Test
    fun `CD-LUM-07 a column of oak logs with 2 natural leaves gives no tree, and with 3 gives a tree`() {
        for ((count, found) in listOf(2 to false, 3 to true)) {
            val grid = TreeGrid()
            val top = grid.trunk(base, 4).last()
            listOf(top.above(), top.north(), top.south()).take(count).forEach { grid.leaf(it) }
            assertEquals(found, find(grid) != null, "a column with $count natural leaves")
        }
    }

    @Test
    fun `CD-LUM-07 placed leaves do not count toward the 3 leaves of a tree`() {
        val grid = TreeGrid()
        val top = grid.trunk(base, 4).last()
        grid.leaf(top.above())
        grid.leaf(top.north())
        for (pos in listOf(top.south(), top.east(), top.west())) grid.leaf(pos, persistent = true)
        assertNull(find(grid))
    }

    @Test
    fun `CD-LUM-08 an oak with a Pam's apple below its leaves gives no tree`() {
        val grid = TreeGrid()
        val oak = grid.oak(base)
        val lowest = oak.leaves.minBy { it.y }
        grid.block(lowest.below(), "pamhc2trees:pamapple", never = true)
        assertNull(find(grid))
    }

    @Test
    fun `CD-LUM-08 an apricorn tree gives no tree`() {
        val grid = TreeGrid()
        val tree = grid.oak(base, logId = "cobblemon:apricorn_log", leafId = "cobblemon:apricorn_leaves", never = true)
        grid.block(tree.leaves.minBy { it.y }.below(), "cobblemon:red_apricorn", never = true)
        assertNull(find(grid))
    }

    @Test
    fun `CD-LUM-08 an oak with a bee nest gives no tree`() {
        val grid = TreeGrid()
        grid.oak(base)
        grid.block(base.above(1).west(), "minecraft:bee_nest", never = true)
        assertNull(find(grid))
        assertNotNull(find(TreeGrid().apply { oak(base) }), "the same oak with no bee nest")
    }

    @Test
    fun `CD-LUM-08 a crimson fungus gives no tree`() {
        val grid = TreeGrid()
        grid.trunk(base, 6, "minecraft:crimson_stem", never = true)
        for (pos in listOf(base.above(4), base.above(5), base.above(6))) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val cell = pos.offset(dx, 0, dz)
                    if (grid.read(cell).air) grid.block(cell, "minecraft:nether_wart_block")
                }
            }
        }
        grid.block(base.above(3).east(), "minecraft:shroomlight")
        assertNull(find(grid))
    }

    @Test
    fun `CD-LUM-08 a mangrove gives no tree`() {
        val grid = TreeGrid()
        for (pos in listOf(base.east(), base.west(), base.north(), base.south())) grid.block(pos.below(), "minecraft:mangrove_roots")
        grid.oak(base.above(), logId = "minecraft:mangrove_log", leafId = "minecraft:mangrove_leaves", never = false)
        for (log in grid.all("minecraft:mangrove_log")) grid.log(log, "minecraft:mangrove_log", never = true)
        assertNull(find(grid, base.above()))
    }

    @Test
    fun `CD-LUM-03 CD-LUM-04 a search from any log of the tree finds the same tree`() {
        val grid = TreeGrid()
        val oak = grid.oak(base)
        val fromBase = find(grid) ?: error("the oak gives no tree")
        for (log in oak.logs) assertEquals(fromBase, find(grid, log), "from $log")
        assertNull(find(grid, oak.leaves.first()), "a leaf is no log of the target tag")
    }
}
