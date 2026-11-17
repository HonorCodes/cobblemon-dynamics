package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.farming.DropFace
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.rl
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR-0017, decisions 7 to 9: what a fell removes, in which order, which claim questions it asks, and what it drops. */
class FellPlanTest {
    private fun tree(grid: TreeGrid, base: BlockPos): Tree =
        TreeSearch.find(grid.read, base, MAX_LOGS, MAX_LEAVES) ?: error("no tree at $base")

    private fun plan(grid: TreeGrid, tree: Tree, shell: Set<BlockPos> = emptySet()): FellPlan =
        FellPlan.of(tree, shell) { grid.read(it).blockId }

    /** An oak whose trunk stands in the last column of chunk 0, so its canopy reaches into chunk 1, with a torch on its trunk. */
    private fun borderOak(): Triple<TreeGrid, Tree, BlockPos> {
        val grid = TreeGrid()
        val base = BlockPos(15, 64, 3)
        grid.oak(base)
        val torch = grid.block(base.above().west(), "minecraft:wall_torch")
        return Triple(grid, tree(grid, base), torch)
    }

    @Test
    fun `CD-LUM-12 the plan lists every log, leaf and shell block, and asks once for each chunk and block id`() {
        val (grid, tree, torch) = borderOak()
        val plan = plan(grid, tree, setOf(torch))
        assertEquals(tree.logs + tree.leaves + torch, plan.steps.map { it.pos }.toSet())
        assertEquals(plan.steps.size, plan.steps.map { it.pos }.toSet().size, "a block is listed twice")
        // The logs stand in chunk 0, the leaves in chunks 0 and 1, the torch in chunk 0.
        val keys = plan.questions.map { (it.x shr 4) to grid.read(it).blockId }
        val expected = setOf(0 to rl(OAK_LOG), 0 to rl(TreeGrid.OAK_LEAVES), 1 to rl(TreeGrid.OAK_LEAVES), 0 to rl("minecraft:wall_torch"))
        assertEquals(expected, keys.toSet())
        assertEquals(keys.size, keys.toSet().size, "a chunk and block id is asked twice")
        val changes = ArrayList<BlockPos>()
        val breaks = ArrayList<BlockPos>()
        assertTrue(
            FellPlan.allowed(plan, {
                changes += it
                true
            }, {
                breaks += it
                true
            }),
        )
        assertEquals(plan.questions, breaks)
        assertEquals(plan.questions + tree.base, changes, "the use questions are those of each chunk and block id and of the base cell")
    }

    @Test
    fun `CD-LUM-12 one refusal of either question refuses the whole fell`() {
        val (grid, tree, torch) = borderOak()
        val plan = plan(grid, tree, setOf(torch))
        for (refused in plan.questions) {
            assertFalse(FellPlan.allowed(plan, { it != refused }, { true }), "the use question of $refused")
            assertFalse(FellPlan.allowed(plan, { true }, { it != refused }), "the break question of $refused")
        }
        assertFalse(FellPlan.allowed(plan, { it != tree.base }, { true }), "the use question of the base cell")
    }

    /** The question of each chunk and block id goes to its first block in the order of the fell, which a GameTest refuses. */
    @Test
    fun `CD-LUM-12 the question of each chunk and block id names its first block in the order of the fell`() {
        val (grid, tree, torch) = borderOak()
        val plan = plan(grid, tree, setOf(torch))
        for (question in plan.questions) {
            val first = plan.steps.first { (it.pos.x shr 4) == (question.x shr 4) && it.blockId == grid.read(question).blockId }
            assertEquals(first.pos, question)
        }
    }

    @Test
    fun `CD-LUM-14 a jungle tree with cocoa goes as the cocoa, then the leaves, then the logs, each from the top`() {
        val grid = TreeGrid()
        val base = BlockPos(0, 64, 0)
        grid.jungle(base)
        val cocoa = grid.block(base.above().east(), "minecraft:cocoa")
        val tree = tree(grid, base)
        val plan = plan(grid, tree, setOf(cocoa))
        assertEquals(cocoa, plan.steps.first().pos)
        assertTrue(plan.steps.first().shell, "the cocoa goes without the shape update")
        val rest = plan.steps.drop(1)
        assertTrue(rest.none { it.shell }, "a leaf or a log goes without the shape update")
        assertEquals(tree.leaves, rest.take(tree.leaves.size).map { it.pos }.toSet())
        assertEquals(tree.logs, rest.drop(tree.leaves.size).map { it.pos }.toSet())
        for (part in listOf(rest.take(tree.leaves.size), rest.drop(tree.leaves.size))) {
            assertEquals(part.map { it.pos.y }.sortedDescending(), part.map { it.pos.y }, "a part does not go from the top")
        }
        assertEquals(base, plan.steps.last().pos)
    }

    @Test
    fun `CD-LUM-14 the fell goes in steps of at most 128 blocks`() {
        val grid = TreeGrid()
        val darkOak = grid.darkOak(BlockPos(0, 64, 0))
        val plan = plan(grid, tree(grid, BlockPos(0, 64, 0)))
        val blocks = darkOak.logs.size + darkOak.leaves.size
        assertEquals(blocks, plan.steps.size)
        val batches = plan.batches(128)
        assertEquals(listOf(128, blocks - 128), batches.map { it.size })
        assertEquals(plan.steps, batches.flatten())
        assertTrue(plan.twoByTwo, "the dark oak has no 2 x 2 trunk")
        val oakGrid = TreeGrid().apply { oak(BlockPos.ZERO) }
        assertFalse(plan(oakGrid, tree(oakGrid, BlockPos.ZERO)).twoByTwo, "an oak has a 2 x 2 trunk")
    }

    @Test
    fun `CD-LUM-16 5 oak logs and 3 saplings pop as one stack of 5 logs and one of 2 saplings after the replant, on the east face`() {
        val log = rl(OAK_LOG)
        val sapling = rl(OAK_SAPLING)
        val loot: List<Pair<ResourceLocation, Int>> = List(5) { log to 1 } + List(3) { sapling to 1 } + listOf(rl("minecraft:stick") to 0)
        val pops = FellPlan.merged(loot, taken = listOf(sapling to 1))
        assertEquals(listOf(log to 5, sapling to 2), pops)
        val base = BlockPos(0, 64, 0)
        assertEquals(Direction.EAST, DropFace.toward(base, base.east(2)))
        assertEquals(listOf(log to 5, sapling to 3), FellPlan.merged(loot), "with no replant every item drops")
    }

    /** Between the plan and a later tick of the run, a player can break a natural leaf and place a leaf of the same kind. */
    @Test
    fun `CD-LUM-06 a step whose leaf is persistent when it comes stays, and so does a cell whose block changed`() {
        val leaf = FellStep(BlockPos.ZERO, rl(TreeGrid.OAK_LEAVES), shell = false)
        assertTrue(FellPlan.stillHolds(leaf, CellFacts(rl(TreeGrid.OAK_LEAVES), leaves = true)))
        assertFalse(FellPlan.stillHolds(leaf, CellFacts(rl(TreeGrid.OAK_LEAVES), leaves = true, persistent = true)))
        assertFalse(FellPlan.stillHolds(leaf, CellFacts(rl("minecraft:stone"))))
        val torch = FellStep(BlockPos.ZERO, rl("minecraft:wall_torch"), shell = true)
        assertTrue(FellPlan.stillHolds(torch, CellFacts(rl("minecraft:wall_torch"))))
    }

    @Test
    fun `CD-LUM-27 the plan holds each chunk of its blocks and of the cells beside its base`() {
        val (grid, tree, torch) = borderOak()
        assertEquals(setOf(FellPlan.chunkKey(0, 0), FellPlan.chunkKey(1, 0)), plan(grid, tree, setOf(torch)).chunks)
        val inner = TreeGrid()
        inner.oak(BlockPos(8, 64, 8))
        assertEquals(setOf(FellPlan.chunkKey(0, 0)), plan(inner, tree(inner, BlockPos(8, 64, 8))).chunks)
        assertEquals(-1L shl 32 or 0xFFFFFFFFL, FellPlan.chunkKey(-1, -1), "a chunk key packs x and z as ChunkPos.asLong does")
    }

    @Test
    fun `CD-LUM-30 the plan asks about the cell of each hanging entity once for each chunk and entity type`() {
        val (grid, tree, torch) = borderOak()
        val frame = rl("minecraft:item_frame")
        val hanging = listOf(BlockPos(14, 66, 3) to frame, BlockPos(14, 67, 3) to frame, BlockPos(16, 66, 3) to rl("minecraft:painting"))
        val plan = FellPlan.of(tree, setOf(torch), hanging) { grid.read(it).blockId }
        assertTrue(BlockPos(14, 66, 3) in plan.questions, "the frame of chunk 0 is not asked about")
        assertFalse(BlockPos(14, 67, 3) in plan.questions, "a second frame of chunk 0 is asked about")
        assertTrue(BlockPos(16, 66, 3) in plan.questions, "the painting of chunk 1 is not asked about")
        assertEquals(plan(grid, tree, setOf(torch)).questions.size + 2, plan.questions.size)
    }
}
