package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class UnstickTest {
    /** A Dragonair 0.95 wide pressed against the pasture block's south face, with the corner of its box inside the block's cell. */
    @Test
    fun `a stuck worker narrower than a block goes to the middle of the cell that holds its center`() {
        assertEquals(7.5 to 10.5, Unstick.middle(7.5, 10.1, 0.95))
        assertEquals(8.5 to 9.5, Unstick.middle(8.45, 9.9, 0.97))
        assertEquals(-3.5 to 4.5, Unstick.middle(-3.02, 4.9, 0.6))
    }

    @Test
    fun `a stuck worker in the middle of its cell stays`() {
        assertNull(Unstick.middle(6.5, 6.5, 0.99))
    }

    @Test
    fun `a stuck worker 1 block wide or wider stays, because no cell holds its body`() {
        assertNull(Unstick.middle(7.05, 7.05, 1.0))
        assertNull(Unstick.middle(7.05, 7.05, 1.04))
    }

    /** A Pidgey 0.49 high caught in the recess of the pasture block's screen, whose top half ends 0.75 above its cell's floor. */
    @Test
    fun `a stuck worker inside a block that fills part of its cell goes on top of that block`() {
        assertEquals(2.75, Unstick.onTop(2.1975, 2, 0.75))
        assertEquals(-4.25, Unstick.onTop(-4.9, -5, 0.75))
    }

    @Test
    fun `a worker that stands on the block or above it, or in a cell with no block, is not lifted`() {
        assertNull(Unstick.onTop(2.75, 2, 0.75))
        assertNull(Unstick.onTop(3.0, 2, 0.75))
        assertNull(Unstick.onTop(2.0, 2, Double.NEGATIVE_INFINITY))
    }

    /** A fence or a wall reaches 1.5 blocks up, and on top of it a worker would stand out of every path. */
    @Test
    fun `a worker is never lifted beyond its cell`() {
        assertNull(Unstick.onTop(2.0, 2, 1.5))
    }

    @Test
    fun `a traveler that stays near one point for 100 ticks has stalled, once per 100 ticks`() {
        val stall = TravelStall()
        for (now in 0L..99L) assertFalse(stall.stalled(now, 7.5, 2.2, 9.74), "stalled at tick $now")
        assertTrue(stall.stalled(100, 7.5 + 0.1, 2.2, 9.74 - 0.1))
        for (now in 101L..199L) assertFalse(stall.stalled(now, 7.5, 2.2, 9.74), "stalled again at tick $now")
        assertTrue(stall.stalled(200, 7.5, 2.2, 9.74))
    }

    @Test
    fun `a move of more than a quarter block starts the count again`() {
        val stall = TravelStall()
        for (now in 0L..60L) stall.stalled(now, 0.0, 0.0, 0.0)
        assertFalse(stall.stalled(61, 0.3, 0.0, 0.0))
        for (now in 62L..160L) assertFalse(stall.stalled(now, 0.3, 0.0, 0.0), "stalled at tick $now")
        assertTrue(stall.stalled(161, 0.3, 0.0, 0.0))
    }

    /** A worker that was not Traveling on the tick before, for example one that waited in Standby, starts a new count. */
    @Test
    fun `a tick with no travel starts the count again`() {
        val stall = TravelStall()
        for (now in 0L..60L) stall.stalled(now, 0.0, 0.0, 0.0)
        for (now in 80L..179L) assertFalse(stall.stalled(now, 0.0, 0.0, 0.0), "stalled at tick $now")
        assertTrue(stall.stalled(180, 0.0, 0.0, 0.0))
    }

    @Test
    fun `CD-ASG-25 a worker in a grown tree reaches the open cells nearest first, within 3 blocks`() {
        val at = BlockPos(10, 64, 10)
        val cells = Unstick.reachableCells(at, null) { true }
        assertEquals(at, cells.first())
        assertEquals(7 * 7 * 3, cells.size)
        assertTrue(cells.zipWithNext().all { (a, b) -> a.distSqr(at) <= b.distSqr(at) }, "not nearest first")
        val near = { c: BlockPos -> abs(c.x - 10) <= 3 && abs(c.y - 64) <= 1 && abs(c.z - 10) <= 3 }
        assertTrue(cells.all(near))
        assertTrue(cells.indexOf(at.east()) < cells.indexOf(at.east(2)))
    }

    /** A wall at x = 12 from y 63 to 65 shuts the cells east of it off, though they are within 3 blocks. */
    @Test
    fun `CD-ASG-25 a cell behind a wall is out of reach, and so is a far work spot`() {
        val at = BlockPos(10, 64, 10)
        val wall = { c: BlockPos -> c.x == 12 }
        val spot = BlockPos(13, 64, 10)
        val cells = Unstick.reachableCells(at, spot) { !wall(it) }
        assertTrue(cells.none { it.x >= 12 }, "a cell behind the wall: ${cells.filter { it.x >= 12 }}")
        assertFalse(spot in cells)
        assertFalse(BlockPos(30, 64, 10) in Unstick.reachableCells(at, BlockPos(30, 64, 10)) { true })
    }

    @Test
    fun `CD-ASG-25 the fill passes through the tree's own blocks, and a reachable work spot wins a tie`() {
        val at = BlockPos(0, 64, 0)
        // Only the cell itself and the cells south of it pass, as a log and the air beyond it.
        val cells = Unstick.reachableCells(at, at.south()) { it.x == 0 && it.y == 64 && it.z in 0..3 }
        assertEquals(listOf(at, at.south(), at.south(2), at.south(3)), cells)
        val open = Unstick.reachableCells(at, at.south()) { true }
        assertEquals(at.south(), open[1])
    }
}
