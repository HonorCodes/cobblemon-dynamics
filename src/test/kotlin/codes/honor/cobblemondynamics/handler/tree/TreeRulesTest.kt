package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.DARK_OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.SPRUCE_SAPLING
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `CD-LUM-02 an oak log and an oak sapling match, and stone does not`() {
        val grid = TreeGrid()
        val log = grid.log(base)
        val sapling = grid.sapling(base.east())
        val stone = grid.block(base.west(), "minecraft:stone")
        assertTrue(TreeRules.matches(grid.read(log)))
        assertTrue(TreeRules.matches(grid.read(sapling)))
        assertFalse(TreeRules.matches(grid.read(stone)))
    }

    @Test
    fun `CD-LUM-10 a grown oak on the base gives demand, and a sapling, air above grass and a tree of the never tag give none`() {
        val oak = TreeGrid().apply { oak(base) }
        assertTrue(TreeRules.hasDemand(oak.read, base, MAX_LOGS, MAX_LEAVES))
        val sapling = TreeGrid().apply { sapling(base) }
        assertFalse(TreeRules.hasDemand(sapling.read, base, MAX_LOGS, MAX_LEAVES))
        val empty = TreeGrid().apply { block(base.below(), "minecraft:grass_block") }
        assertFalse(TreeRules.hasDemand(empty.read, base, MAX_LOGS, MAX_LEAVES))
        val nested = TreeGrid().apply {
            oak(base)
            block(base.above().east(), "minecraft:bee_nest", never = true)
        }
        assertFalse(TreeRules.hasDemand(nested.read, base, MAX_LOGS, MAX_LEAVES))
    }

    /** A log that a player puts below the base makes another cell the base, so the bound cell no longer holds the tree's base. */
    @Test
    fun `CD-LUM-10 a tree whose base moved off the bound cell gives no demand there`() {
        val grid = TreeGrid().apply { oak(base) }
        grid.log(base.below())
        assertFalse(TreeRules.hasDemand(grid.read, base, MAX_LOGS, MAX_LEAVES))
        assertTrue(TreeRules.hasDemand(grid.read, base.below(), MAX_LOGS, MAX_LEAVES))
    }

    @Test
    fun `CD-LUM-19 a log, a sapling and air above dirt keep the binding, and stone in the cell or air below it clears it`() {
        val dirt = CellFacts(TreeGrid.rl("minecraft:dirt"))
        val log = CellFacts(TreeGrid.rl(TreeGrid.OAK_LOG), logTarget = true, anyLog = true)
        val sapling = CellFacts(TreeGrid.rl(TreeGrid.OAK_SAPLING), sapling = true)
        val water = CellFacts(TreeGrid.rl("minecraft:water"), fluid = true)
        val stone = CellFacts(TreeGrid.rl("minecraft:stone"))
        assertTrue(TreeRules.bindingHolds(log, dirt))
        assertTrue(TreeRules.bindingHolds(log, CellFacts.AIR), "a log keeps the binding over any block")
        assertTrue(TreeRules.bindingHolds(sapling, dirt))
        assertTrue(TreeRules.bindingHolds(CellFacts.AIR, dirt))
        assertTrue(TreeRules.bindingHolds(water, dirt))
        assertFalse(TreeRules.bindingHolds(stone, dirt))
        assertFalse(TreeRules.bindingHolds(CellFacts.AIR, CellFacts.AIR))
    }

    @Test
    fun `CD-LUM-20 a mangrove and a tree of 257 logs refuse a bind, and an oak and a sapling do not`() {
        val mangrove = TreeGrid().apply {
            oak(base, logId = "minecraft:mangrove_log", leafId = "minecraft:mangrove_leaves")
            for (log in all("minecraft:mangrove_log")) log(log, "minecraft:mangrove_log", never = true)
        }
        assertTrue(TreeRules.refusesBind(mangrove.read, base, MAX_LOGS, MAX_LEAVES))
        val tall = TreeGrid().apply {
            trunk(base, MAX_LOGS + 1)
            layer(base.above(MAX_LOGS + 1), 1)
        }
        assertTrue(TreeRules.refusesBind(tall.read, TreeRules.canonical(tall.read, base.above(5), MAX_LOGS), MAX_LOGS, MAX_LEAVES))
        assertFalse(TreeRules.refusesBind(TreeGrid().apply { oak(base) }.read, base, MAX_LOGS, MAX_LEAVES))
        assertFalse(TreeRules.refusesBind(TreeGrid().apply { sapling(base) }.read, base, MAX_LOGS, MAX_LEAVES))
    }
}
