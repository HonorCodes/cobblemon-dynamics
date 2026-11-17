package codes.honor.cobblemondynamics.handler.water

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.Worker
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The Freezer's rules over plain facts (ADR-0019). */
class FreezeRulesTest {
    /** A still source of a pond under air, in the work area, in the dark, far from farmland and with nobody in it. */
    private val pond = WaterFacts(
        cell = WaterCell.SOURCE,
        above = WaterCell.AIR,
        feedsFlow = false,
        hydratesFarmland = false,
        blockLight = 0,
        inWorkArea = true,
        occupied = false,
    )

    private val cell = BlockPos(10, 64, 10)

    @Test
    fun `CD-FRZ-03 only a water source under air matches, and no other water does`() {
        val source = WaterCell.of(air = false, waterBlock = true, bubbleColumn = false, water = true, source = true)
        val flowing = WaterCell.of(air = false, waterBlock = true, bubbleColumn = false, water = true, source = false)
        val slab = WaterCell.of(air = false, waterBlock = false, bubbleColumn = false, water = true, source = true)
        val column = WaterCell.of(air = false, waterBlock = false, bubbleColumn = true, water = true, source = true)
        val air = WaterCell.of(air = true, waterBlock = false, bubbleColumn = false, water = false, source = false)
        assertEquals(
            listOf(WaterCell.SOURCE, WaterCell.FLOWING, WaterCell.WATERLOGGED, WaterCell.BUBBLE_COLUMN, WaterCell.AIR),
            listOf(source, flowing, slab, column, air),
        )
        assertTrue(FreezeRules.matches(source, air))
        assertFalse(FreezeRules.matches(source, source), "a source under a source")
        assertFalse(FreezeRules.matches(flowing, air), "a flowing water cell")
        assertFalse(FreezeRules.matches(slab, air), "a waterlogged slab")
        assertFalse(FreezeRules.matches(column, air), "a bubble column")
        assertTrue(FreezeRules.freezable(pond))
        assertFalse(FreezeRules.freezable(pond.copy(above = WaterCell.SOURCE)), "a source under a source")
        assertFalse(FreezeRules.freezable(pond.copy(inWorkArea = false)), "a source outside the work area")
    }

    @Test
    fun `CD-FRZ-07 the top block of a bubble column under air is not freezable`() {
        assertFalse(FreezeRules.freezable(pond.copy(cell = WaterCell.BUBBLE_COLUMN)))
    }

    @Test
    fun `CD-FRZ-08 a source beside a flowing cell, and a source over a flowing cell, stay`() {
        fun kindAt(flowingAt: BlockPos?): (BlockPos) -> WaterCell = { if (it == flowingAt) WaterCell.FLOWING else WaterCell.SOURCE }
        fun beside(flowingAt: BlockPos?): Boolean = FreezeRules.feedsFlow(cell, kindAt(flowingAt))
        for (side in listOf(cell.north(), cell.south(), cell.east(), cell.west())) assertTrue(beside(side), "flowing water at $side")
        assertTrue(beside(cell.below()), "a source over a flowing cell")
        assertFalse(beside(null), "a source among sources")
        assertFalse(beside(cell.above()), "the cell above is not a flow that the source feeds")
        assertFalse(beside(cell.north().east()), "a diagonal cell is not a neighbour")
        assertFalse(FreezeRules.freezable(pond.copy(feedsFlow = true)))
    }

    @Test
    fun `CD-FRZ-06 water within 4 blocks of farmland at its height or one block lower stays, and water 5 blocks away freezes`() {
        fun hydrates(farmland: BlockPos): Boolean = FreezeRules.hydratesFarmland(cell) { it == farmland }
        assertTrue(hydrates(cell.offset(4, 0, 0)), "4 blocks away at its height")
        assertTrue(hydrates(cell.offset(0, -1, -4)), "4 blocks away, one block lower")
        assertTrue(hydrates(cell.offset(-4, 0, 4)), "the far corner of the box")
        assertFalse(hydrates(cell.offset(5, 0, 0)), "5 blocks away")
        assertFalse(hydrates(cell.offset(0, -1, 5)), "5 blocks away, one block lower")
        assertFalse(hydrates(cell.offset(0, 1, 1)), "farmland one block higher, which this water never hydrates")
        assertFalse(hydrates(cell.offset(0, -2, 1)), "farmland two blocks lower")
        assertFalse(FreezeRules.freezable(pond.copy(hydratesFarmland = true)))
    }

    @Test
    fun `CD-FRZ-06 the farmland test reads the 9 by 9 by 2 box once`() {
        val asked = ArrayList<BlockPos>()
        FreezeRules.hydratesFarmland(cell) {
            asked += it
            false
        }
        assertEquals(162, asked.size)
        assertEquals(162, asked.toSet().size)
    }

    @Test
    fun `CD-FRZ-09 block light 9 freezes and 10 does not`() {
        assertTrue(FreezeRules.freezable(pond.copy(blockLight = 9)))
        assertFalse(FreezeRules.freezable(pond.copy(blockLight = 10)))
        assertFalse(FreezeRules.freezable(pond.copy(blockLight = 15)))
    }

    /** No player or Cobblemon is frozen in: a cell where a living entity is stays water while it is there. */
    @Test
    fun `a cell with a living entity in it is not freezable`() {
        assertFalse(FreezeRules.freezable(pond.copy(occupied = true)))
    }

    @Test
    fun `CD-FRZ-05 the freezes take the nearest cell in reach first, the claimed cell last, and never a cell beyond reach`() {
        val worker = Vec3.atCenterOf(BlockPos(0, 64, 0))
        val claimed = BlockPos(1, 64, 0)
        val cells = listOf(BlockPos(6, 64, 0), BlockPos(4, 64, 0), BlockPos(2, 64, 0), claimed)
        val frozen = ArrayList<BlockPos>()
        repeat(5) {
            FreezeRules.next(worker, claimed, cells, reach = 5.0) { it !in frozen }?.let(frozen::add)
        }
        assertEquals(listOf(BlockPos(2, 64, 0), BlockPos(4, 64, 0), claimed), frozen)
    }

    @Test
    fun `CD-FRZ-05 reach is measured between block centres, and the claimed cell comes last even beyond reach`() {
        val worker = Vec3.atCenterOf(BlockPos(0, 65, 0))
        // 3, 4 and 0 blocks apart: exactly 5 between the centres.
        val edge = BlockPos(3, 65, 4)
        val beyond = BlockPos(3, 65, 5)
        val claimed = BlockPos(5, 64, 3)
        assertEquals(edge, FreezeRules.next(worker, claimed, listOf(beyond, edge), reach = 5.0))
        assertEquals(claimed, FreezeRules.next(worker, claimed, listOf(beyond), reach = 5.0))
        assertNull(FreezeRules.next(worker, claimed, listOf(beyond), reach = 5.0) { it != claimed })
    }

    @Test
    fun `CD-FRZ-05 two cells at the same distance freeze in one fixed order`() {
        val worker = Vec3.atCenterOf(BlockPos.ZERO)
        val a = BlockPos(2, 0, 0)
        val b = BlockPos(-2, 0, 0)
        val claimed = BlockPos(0, 0, 1)
        assertEquals(FreezeRules.next(worker, claimed, listOf(a, b), 5.0), FreezeRules.next(worker, claimed, listOf(b, a), 5.0))
    }

    private val overworld = ResourceLocation.withDefaultNamespace("overworld")

    private fun key(pos: BlockPos): TargetKey = TargetKey.block(overworld, pos)

    /** The order of the handler's freezes, which pass over a cell that the worker's stalls hold, as `WorkContext.isStalled` reads them. */
    private fun next(w: Worker, now: Long, cells: List<BlockPos>, claimed: BlockPos): BlockPos? =
        FreezeRules.next(Vec3.atCenterOf(BlockPos(0, 64, 0)), claimed, cells, 5.0) { !w.isStalled(key(it), now) }

    /** The engine keeps a worker's stalls beyond its claim, so a refused cell leaves the order for that worker alone. */
    @Test
    fun `CD-FRZ-11 a refused cell leaves the order for 6,000 ticks and returns after them`() {
        val refused = BlockPos(2, 64, 0)
        val claimed = BlockPos(1, 64, 0)
        val cells = listOf(refused, BlockPos(4, 64, 0), claimed)
        val worker = Worker(UUID.randomUUID(), null)
        val other = Worker(UUID.randomUUID(), null)
        val refusedAt = 1_000L
        worker.stalledUntil[key(refused)] = refusedAt + FreezeRules.REFUSED_WAIT_TICKS
        assertEquals(6_000L, FreezeRules.REFUSED_WAIT_TICKS)
        assertEquals(BlockPos(4, 64, 0), next(worker, refusedAt, cells, claimed))
        assertEquals(BlockPos(4, 64, 0), next(worker, refusedAt + 5_999, cells, claimed))
        assertEquals(refused, next(worker, refusedAt + 6_000, cells, claimed))
        assertEquals(refused, next(other, refusedAt, cells, claimed), "another worker still takes the cell")
        assertTrue(worker.isSkipped(key(refused), refusedAt + 5_999), "the scan gives the worker a stalled cell")
        assertFalse(worker.isSkipped(key(refused), refusedAt + 6_000))
    }

    /** A cell that the worker skips as a target, after a travel timeout or with no work spot, still freezes from a spot nearby. */
    @Test
    fun `CD-FRZ-05 CD-FRZ-11 a cell that the worker skips as a target is no refusal`() {
        val skipped = BlockPos(2, 64, 0)
        val claimed = BlockPos(1, 64, 0)
        val worker = Worker(UUID.randomUUID(), null)
        worker.skipUntil[key(skipped)] = 7_000L
        assertTrue(worker.isSkipped(key(skipped), 1_000L))
        assertFalse(worker.isStalled(key(skipped), 1_000L))
        assertEquals(skipped, next(worker, 1_000L, listOf(skipped, claimed), claimed))
    }
}
