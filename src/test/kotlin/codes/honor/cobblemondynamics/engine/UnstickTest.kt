package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.tree.CellFacts
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.pathfinder.PathType
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

    /** The Torterra of the scene below creeps along the pasture block at about 0.002 blocks a tick after it meets its face. */
    @Test
    fun `CD-ASG-27 the short watch reports a worker that creeps less than a tenth of a block in 20 ticks`() {
        val press = TravelStall(TravelStall.PRESS_TICKS, TravelStall.PRESS_RADIUS)
        for (now in 0L..19L) assertFalse(press.stalled(now, 10.21 + now * 0.002, 2.0, 9.94), "pressed at tick $now")
        assertTrue(press.stalled(20, 10.25, 2.0, 9.94))
    }

    /** The slowest walk of the GameTests, a Torterra at about half its size, covers 0.012 blocks a tick. */
    @Test
    fun `CD-ASG-27 the short watch never reports a worker that walks`() {
        val press = TravelStall(TravelStall.PRESS_TICKS, TravelStall.PRESS_RADIUS)
        for (now in 0L..200L) assertFalse(press.stalled(now, now * 0.012, 2.0, 0.0), "pressed at tick $now")
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

    /**
     * A Torterra 2.25 wide walks west along z = 10.51 and presses its west face against the pasture block's east face. Its body spans
     * z 9.385 to 11.635, and the block's top half spans z 11.0625 to 11.625.
     */
    @Test
    fun `CD-ASG-27 a worker pressed head-on against a block steps sideways by the least distance that clears it`() {
        val shifts = Unstick.sideShifts(9.385, 11.635, 11.0625, 11.625, 1.125)
        assertEquals(1, shifts.size)
        assertEquals(11.0625 - 11.635 - Unstick.SIDESTEP_GAP, shifts[0], 1e-9)
    }

    @Test
    fun `CD-ASG-27 both ways clear a small block, the shorter first`() {
        val shifts = Unstick.sideShifts(4.0, 6.25, 5.5, 5.75, 2.0)
        assertEquals(listOf(5.75 - 4.0 + Unstick.SIDESTEP_GAP, 5.5 - 6.25 - Unstick.SIDESTEP_GAP).sortedBy { abs(it) }, shifts)
        assertTrue(abs(shifts[0]) <= abs(shifts[1]))
    }

    @Test
    fun `CD-ASG-27 no step clears a wall, and a block beside the body needs none`() {
        assertTrue(Unstick.sideShifts(9.385, 11.635, -20.0, 40.0, 1.125).isEmpty())
        assertTrue(Unstick.sideShifts(9.385, 11.635, 11.635, 12.0, 1.125).isEmpty())
        assertTrue(Unstick.sideShifts(9.385, 11.635, 8.0, 9.385, 1.125).isEmpty())
    }

    @Test
    fun `CD-ASG-27 a step never takes the center beyond half the body's width`() {
        assertTrue(Unstick.sideShifts(0.0, 1.0, 0.1, 0.9, 0.5).isEmpty())
        assertEquals(listOf(-0.5 - Unstick.SIDESTEP_GAP), Unstick.sideShifts(0.0, 1.0, 0.5, 2.0, 0.6))
    }

    /** The Torterra of the east scene walks west, 0.01 blocks south of its path's row. */
    @Test
    fun `CD-ASG-27 only a face that leads a tenth of the way or more toward the goal counts, most directly first`() {
        assertEquals(listOf(Direction.WEST), Unstick.leadingFaces(-1.5, 0.01))
        assertEquals(listOf(Direction.SOUTH, Direction.EAST), Unstick.leadingFaces(0.4, 1.56))
        assertEquals(setOf(Direction.NORTH, Direction.WEST), Unstick.leadingFaces(-1.0, -1.0).toSet())
        assertTrue(Unstick.leadingFaces(0.0, 0.0).isEmpty())
    }

    @Test
    fun `CD-ASG-27 a worker checks for a sideways step at most once in 20 ticks`() {
        assertTrue(Unstick.sidestepDue(5, Long.MIN_VALUE))
        assertFalse(Unstick.sidestepDue(119, 100))
        assertTrue(Unstick.sidestepDue(120, 100))
    }

    /** A body 2.25 wide with its center at (12.5, 9.94) stands over 3 cells on x and 4 on z at its feet. */
    @Test
    fun `CD-ASG-27 the cells under a body at its feet`() {
        val expected = (11..13).flatMap { x -> (8..11).map { z -> BlockPos(x, 2, z) } }
        assertEquals(expected, Unstick.footCells(11.376, 8.816, 13.624, 11.064, 2))
        assertEquals(listOf(BlockPos(0, 5, 0)), Unstick.footCells(0.2, 0.2, 0.8, 0.8, 5))
    }

    @Test
    fun `CD-ASG-25 CD-ASG-27 a free cell or a stepped body's center lies only in open air, a walkable cell or land beside water`() {
        val open = setOf(PathType.WALKABLE, PathType.OPEN, PathType.WATER_BORDER)
        for (type in open) assertTrue(Unstick.isOpenFoot(type), "$type")
        for (type in PathType.entries - open) assertFalse(Unstick.isOpenFoot(type), "a center lies in $type")
    }

    /** A farm's row beside its water channel reads WATER_BORDER, and the water itself is a fluid with no collision. */
    @Test
    fun `CD-ASG-27 a step may land on the land beside water but never in open water`() {
        assertTrue(Unstick.isOpenFoot(PathType.WATER_BORDER))
        assertTrue(Unstick.isDryFoot(hasFluid = false, hasCollision = false))
        assertFalse(Unstick.isDryFoot(hasFluid = true, hasCollision = false))
        assertTrue(Unstick.isDryFoot(hasFluid = true, hasCollision = true), "the water inside a waterlogged pasture block")
    }

    @Test
    fun `CD-ASG-27 no cell under a stepped body holds fire, a berry bush, honey, powder snow, lava or water`() {
        for (type in listOf(PathType.WALKABLE, PathType.OPEN, PathType.WATER_BORDER, PathType.BLOCKED)) {
            assertTrue(Unstick.isSafeFoot(type), "$type")
        }
        val hazards = listOf(
            PathType.DAMAGE_FIRE, PathType.DANGER_FIRE, PathType.DAMAGE_OTHER, PathType.DANGER_OTHER, PathType.DAMAGE_CAUTIOUS,
            PathType.STICKY_HONEY, PathType.POWDER_SNOW, PathType.DANGER_POWDER_SNOW, PathType.LAVA, PathType.WATER,
            PathType.DANGER_TRAPDOOR,
        )
        for (type in hazards) assertFalse(Unstick.isSafeFoot(type), "a step lands on $type")
    }

    @Test
    fun `CD-ASG-27 the short watch counts only ticks on which the worker pushes against a block`() {
        val press = TravelStall(TravelStall.PRESS_TICKS, TravelStall.PRESS_RADIUS)
        for (now in 0L..9L) assertFalse(press.stalled(now, 10.0, 2.0, 9.94, pushing = true))
        assertFalse(press.stalled(10, 10.0, 2.0, 9.94, pushing = false))
        for (now in 11L..29L) assertFalse(press.stalled(now, 10.0, 2.0, 9.94, pushing = true), "pressed at tick $now")
        assertTrue(press.stalled(30, 10.0, 2.0, 9.94, pushing = true))
    }

    @Test
    fun `CD-ASG-25 free landing excludes changed trunk, nearby tagged logs, roots and natural leaves`() {
        val stone = CellFacts(ResourceLocation.withDefaultNamespace("stone"))
        val placedLeaf = CellFacts(ResourceLocation.withDefaultNamespace("oak_leaves"), leaves = true, persistent = true)
        val naturalLeaf = CellFacts(ResourceLocation.withDefaultNamespace("oak_leaves"), leaves = true, persistent = false)
        val nearbyTargetLog = CellFacts(ResourceLocation.withDefaultNamespace("oak_log"), logTarget = true, anyLog = true)
        val customTargetLog = CellFacts(ResourceLocation.parse("example:magic_log"), logTarget = true, anyLog = false)
        val strippedLog = CellFacts(ResourceLocation.withDefaultNamespace("stripped_oak_log"), logTarget = false, anyLog = true)
        val mangroveRoot = CellFacts(ResourceLocation.withDefaultNamespace("mangrove_roots"))

        assertTrue(TreeWay.canLandAbove(stone, inChangedTrunk = false))
        assertTrue(TreeWay.canLandAbove(placedLeaf, inChangedTrunk = false))
        assertFalse(TreeWay.canLandAbove(stone, inChangedTrunk = true))
        assertFalse(TreeWay.canLandAbove(nearbyTargetLog, inChangedTrunk = false))
        assertFalse(TreeWay.canLandAbove(customTargetLog, inChangedTrunk = false))
        assertFalse(TreeWay.canLandAbove(strippedLog, inChangedTrunk = false))
        assertFalse(TreeWay.canLandAbove(mangroveRoot, inChangedTrunk = false))
        assertFalse(TreeWay.canLandAbove(naturalLeaf, inChangedTrunk = false))
    }
}
