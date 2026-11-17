package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR-0017, decision 8: a fell runs over a few ticks, and a fell that has started completes. */
class FellRunTest {
    /** A dark oak of 24 logs and 107 leaves: 131 steps, so the run takes 2 ticks of 128 blocks. */
    private fun darkOakPlan(): FellPlan {
        val grid = TreeGrid()
        grid.darkOak(BlockPos.ZERO)
        val tree = TreeSearch.find(grid.read, BlockPos.ZERO, MAX_LOGS, MAX_LEAVES) ?: error("no dark oak")
        return FellPlan.of(tree, emptySet()) { grid.read(it).blockId }
    }

    @Test
    fun `CD-LUM-14 each tick removes at most blocks_per_tick steps, in the order of the plan`() {
        val plan = darkOakPlan()
        val run = FellRun(plan, 128)
        val removed = ArrayList<FellStep>()
        assertFalse(run.step { removed += it }, "the first tick ended the run")
        assertEquals(plan.steps.take(128), removed)
        assertTrue(run.step { removed += it }, "the second tick did not end the run")
        assertEquals(plan.steps, removed)
        assertTrue(run.done)
        assertFalse(run.step { removed += it }, "a run that is done removed a block")
        assertEquals(plan.steps.size, removed.size)
    }

    @Test
    fun `CD-LUM-15 an unclaim after the first step removes every block that the plan still holds`() {
        val plan = darkOakPlan()
        val run = FellRun(plan, 128)
        val removed = ArrayList<FellStep>()
        run.step { removed += it }
        assertTrue(run.finish { removed += it }, "the unclaim did not end the run")
        assertEquals(plan.steps, removed, "the unclaim left a block of the plan, or removed one twice")
        assertFalse(run.finish { removed += it })
        assertEquals(plan.steps.size, removed.size)
    }

    @Test
    fun `CD-LUM-15 a run that ends in its first tick reports its end once`() {
        val grid = TreeGrid()
        grid.oak(BlockPos.ZERO)
        val tree = TreeSearch.find(grid.read, BlockPos.ZERO, MAX_LOGS, MAX_LEAVES) ?: error("no oak")
        val run = FellRun(FellPlan.of(tree, emptySet()) { grid.read(it).blockId }, 128)
        val removed = ArrayList<FellStep>()
        assertTrue(run.step { removed += it })
        assertEquals(58, removed.size)
        assertFalse(run.finish { removed += it })
        assertEquals(58, removed.size)
    }

    /** No chunk of a running fell unloads, so no block of the plan stays and the drops land in a loaded chunk. */
    @Test
    fun `CD-LUM-27 a run holds a ticket on each chunk of its plan from its start to its end, and gives each back once`() {
        val plan = darkOakPlan()
        val held = ArrayList<Long>()
        val freed = ArrayList<Long>()
        val run = FellRun(plan, 128, FellRun.Tickets({ held += it }, { freed += it }))
        assertEquals(plan.chunks.sorted(), held.sorted())
        run.step { }
        run.finish { }
        assertTrue(freed.isEmpty(), "a ticket ended before the end of the fell")
        run.release()
        run.release()
        assertEquals(held.sorted(), freed.sorted())
    }
}
