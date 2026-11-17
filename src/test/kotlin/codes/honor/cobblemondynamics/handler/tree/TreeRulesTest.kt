package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.DARK_OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.SPRUCE_SAPLING
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Lumberjack's choices over plain facts of the cells (ADR-0017, decisions 2, 5 and 11). */
class TreeRulesTest {
    private val base = BlockPos(0, 64, 0)

    @Test
    fun `CD-LUM-03 a click on a branch of an acacia resolves to its lowest log`() {
        val grid = TreeGrid()
        grid.acacia(base)
        for (branch in listOf(base.offset(2, 4, 0), base.offset(-2, 4, -2), base.offset(1, 3, 0))) {
            assertEquals(base, TreeRules.canonical(grid.read, branch, MAX_LOGS), "a click at $branch")
        }
    }

    @Test
    fun `CD-LUM-03 a click on any log of a 2 x 2 dark oak resolves to its corner with the least coordinates`() {
        val grid = TreeGrid()
        val corner = BlockPos(-3, 64, 5)
        val darkOak = grid.darkOak(corner)
        for (log in darkOak.logs) assertEquals(corner, TreeRules.canonical(grid.read, log, MAX_LOGS), "a click at $log")
    }

    @Test
    fun `CD-LUM-03 a clicked sapling resolves to itself, or to the corner of its 2 x 2 of saplings`() {
        val grid = TreeGrid()
        grid.sapling(base)
        assertEquals(base, TreeRules.canonical(grid.read, base, MAX_LOGS))
        val corner = BlockPos(10, 64, 10)
        val square = listOf(corner, corner.east(), corner.south(), corner.east().south())
        for (cell in square) grid.sapling(cell, DARK_OAK_SAPLING)
        for (cell in square) assertEquals(corner, TreeRules.canonical(grid.read, cell, MAX_LOGS), "a click at $cell")
        // Saplings of two kinds make no 2 x 2.
        val mixed = BlockPos(20, 64, 20)
        grid.sapling(mixed, SPRUCE_SAPLING)
        for (cell in listOf(mixed.east(), mixed.south(), mixed.east().south())) grid.sapling(cell, DARK_OAK_SAPLING)
        assertEquals(mixed.east(), TreeRules.canonical(grid.read, mixed.east(), MAX_LOGS))
    }

    @Test
    fun `CD-LUM-03 a click on logs above the cap resolves to the clicked cell`() {
        val grid = TreeGrid()
        val logs = grid.trunk(base, MAX_LOGS + 1)
        assertEquals(logs[10], TreeRules.canonical(grid.read, logs[10], MAX_LOGS))
    }
}
