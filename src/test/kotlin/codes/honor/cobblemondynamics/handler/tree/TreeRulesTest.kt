package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.farming.SaplingRules
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.DARK_OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.DT_OAK_BRANCH
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.SPRUCE_SAPLING
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    /** The Lumberjack and the Cultivator name one cell for a 2 x 2 of saplings, also where two squares overlap. */
    @Test
    fun `CD-CUL-13 the Lumberjack's sapling corner is the Cultivator's`() {
        val grid = TreeGrid()
        val start = BlockPos(20, 64, 20)
        val cells = (0..2).flatMap { x -> (0..1).map { z -> start.offset(x, 0, z) } }
        for (cell in cells) grid.sapling(cell, SPRUCE_SAPLING)
        val lone = grid.sapling(BlockPos(30, 64, 30), SPRUCE_SAPLING)
        for (cell in cells + lone) {
            val same: (BlockPos) -> Boolean = { grid.read(it).sapling && grid.read(it).blockId == grid.read(cell).blockId }
            assertEquals(SaplingRules.corner(cell, same), TreeRules.canonical(grid.read, cell, MAX_LOGS), "the sapling at $cell")
        }
        assertEquals(start, TreeRules.canonical(grid.read, start.offset(1, 0, 1), MAX_LOGS))
        assertEquals(lone, TreeRules.canonical(grid.read, lone, MAX_LOGS))
    }

    /** Review: a log over a log of its kind is no base, which the switch check learns with no search. */
    @Test
    fun `CD-LSH-33 only the base log resolves to itself, and a log over a log answers with no search`() {
        val grid = TreeGrid()
        val base = BlockPos(0, 64, 0)
        grid.trunk(base, 5)
        var reads = 0
        val counted: (BlockPos) -> CellFacts = { pos ->
            reads++
            grid.read(pos)
        }
        assertFalse(TreeRules.resolvesToItself(counted, base.above(2), MAX_LOGS))
        assertEquals(2, reads, "a log over a log read more than its cell and the cell below")
        assertTrue(TreeRules.resolvesToItself(grid.read, base, MAX_LOGS))
        val sapling = grid.sapling(BlockPos(10, 64, 10))
        assertTrue(TreeRules.resolvesToItself(grid.read, sapling, MAX_LOGS))
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

    /** R82: the grown tree of such a sapling is always in the never tag, so the bind would wait forever. */
    @Test
    fun `CD-LUM-20 a sapling of the never-sapling tag refuses a bind, and an oak sapling does not`() {
        val propagule = TreeGrid().apply { sapling(base, "minecraft:mangrove_propagule", never = true) }
        assertTrue(TreeRules.refusesBind(propagule.read, base, MAX_LOGS, MAX_LEAVES))
        assertFalse(TreeRules.refusesBind(TreeGrid().apply { sapling(base) }.read, base, MAX_LOGS, MAX_LEAVES))
    }

    /**
     * R80, RES12: a branch of Dynamic Trees that loses its block with no entity drops nothing, and Dynamic Trees owns the drops and the
     * regrowth of its trees. So the handler hands such a tree to Dynamic Trees' own fell, and replants nothing.
     */
    @Test
    fun `CD-LUM-25 a base in the branch tag picks the Dynamic Trees fell`() {
        val grid = TreeGrid().apply { dynamicOak(base) }
        assertEquals(Feller.DYNAMIC_TREES, TreeRules.feller(grid.read(base), dynamicTrees = true))
        assertTrue(TreeRules.hasDemand(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = true), "the tree has no demand")
        assertFalse(TreeRules.refusesBind(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = true), "a bind on it is refused")
        val oak = TreeGrid().apply { oak(base) }
        for (hook in listOf(true, false)) assertEquals(Feller.OWN, TreeRules.feller(oak.read(base), hook), "an oak with the hook: $hook")
    }

    /** A removal by the handler would leave Dynamic Trees to destroy the rest of the tree with no drops, so only its own fell may run. */
    @Test
    fun `CD-LUM-25 with no hook the handler never fells a tree of Dynamic Trees itself`() {
        val grid = TreeGrid().apply { dynamicOak(base) }
        assertNull(TreeRules.feller(grid.read(base), dynamicTrees = false))
        assertNull(TreeRules.treeOn(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = false))
        assertFalse(TreeRules.hasDemand(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = false))
        assertTrue(TreeRules.refusesBind(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = false))
    }

    /** The rules of the tree search hold for a tree of Dynamic Trees too: no placed leaves, and no tree beside a block of the never tag. */
    @Test
    fun `CD-LUM-25 a tree of Dynamic Trees keeps the rules of the tree search`() {
        val nested = TreeGrid().apply {
            dynamicOak(base)
            block(base.above().east(), "minecraft:bee_nest", never = true)
        }
        assertNull(TreeRules.treeOn(nested.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = true), "a tree with a bee nest")
        val placed = TreeGrid().apply {
            for (log in trunk(base, 5, DT_OAK_BRANCH)) branch(log)
            for (leaf in layer(base.above(5), 1)) leaf(leaf, persistent = true)
        }
        assertNull(TreeRules.treeOn(placed.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = true), "branches with placed leaves only")
    }

    /**
     * R89: after a rename of the branch tag, a removal by the handler would still leave Dynamic Trees to destroy the rest of the tree
     * with no drops. So a base of the namespace `dynamictrees` picks Dynamic Trees' own fell, whatever its tags.
     */
    @Test
    fun `CD-LUM-25 a base of the namespace dynamictrees picks the Dynamic Trees fell with no branch tag`() {
        val grid = TreeGrid().apply { dynamicOak(base, dynamicBranch = false) }
        assertEquals(Feller.DYNAMIC_TREES, TreeRules.feller(grid.read(base), dynamicTrees = true))
        assertTrue(TreeRules.hasDemand(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = true), "the tree has no demand")
        assertFalse(TreeRules.refusesBind(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = true), "a bind on it is refused")
    }

    /** R89: with no hook, such a tree is no tree, as a tree in the branch tag is, so the handler never fells it itself. */
    @Test
    fun `CD-LUM-25 with no hook a base of the namespace dynamictrees with no branch tag is no tree`() {
        val grid = TreeGrid().apply { dynamicOak(base, dynamicBranch = false) }
        assertNull(TreeRules.feller(grid.read(base), dynamicTrees = false))
        assertNull(TreeRules.treeOn(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = false))
        assertFalse(TreeRules.hasDemand(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = false))
        assertTrue(TreeRules.refusesBind(grid.read, base, MAX_LOGS, MAX_LEAVES, dynamicTrees = false))
    }

    /**
     * R89: the rule compares the whole namespace. An add-on of Dynamic Trees with a namespace of its own counts through the branch tag
     * or the hook's `BranchBlock` test, which these cells lack.
     */
    @Test
    fun `CD-LUM-25 only the namespace dynamictrees itself picks the Dynamic Trees fell`() {
        for (id in listOf(OAK_LOG, "example:dynamictrees_log", "dynamictreesplus:example_branch")) {
            val grid = TreeGrid().apply { oak(base, logId = id) }
            for (hook in listOf(true, false)) assertEquals(Feller.OWN, TreeRules.feller(grid.read(base), hook), "$id with the hook: $hook")
        }
    }

    @Test
    fun `CD-ASG-24 the Lumberjack's growth cells are the trunk base of its tree, grown or as saplings`() {
        val grid = TreeGrid()
        grid.oak(base)
        assertEquals(listOf(base), TreeRules.growthCells(grid.read, base))
        val corner = BlockPos(-3, 64, 5)
        grid.darkOak(corner)
        val square = TreeRules.square(corner)
        assertEquals(square, TreeRules.growthCells(grid.read, corner))
        val saplings = BlockPos(10, 64, 10)
        for (cell in TreeRules.square(saplings)) grid.sapling(cell, DARK_OAK_SAPLING)
        assertEquals(TreeRules.square(saplings), TreeRules.growthCells(grid.read, saplings))
        // An empty base, between the fell and the replant, keeps its own cell.
        val empty = BlockPos(30, 64, 30)
        assertEquals(listOf(empty), TreeRules.growthCells(grid.read, empty))
    }
}
