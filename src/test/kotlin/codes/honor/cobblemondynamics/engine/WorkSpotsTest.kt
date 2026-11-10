package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A flat floor at y = 0 with optional walls. Positions at y = 1 are standable unless a wall fills them. */
class GridFixture(private val walls: Set<BlockPos> = emptySet(), private val target: BlockPos) : SpotGrid {
    override fun isStandable(x: Int, y: Int, z: Int): Boolean =
        y == 1 && BlockPos(x, y, z) !in walls && BlockPos(x, y + 1, z) !in walls && BlockPos(x, y, z) != target
    override fun blocksSight(x: Int, y: Int, z: Int): Boolean = BlockPos(x, y, z) in walls || (y <= 0)
}

class WorkSpotsTest {
    private val target = BlockPos(0, 1, 0)
    private val center = Vec3.atCenterOf(target)
    private val area = WorkArea(BlockPos(0, 1, 0), 8, 6)

    @Test
    fun `CD-ASG-10 6_0 is in range for a ranged role and 6_1 is not`() {
        assertTrue(WorkSpots.inRange(center.add(6.0, 0.0, 0.0), center, 6.0))
        assertFalse(WorkSpots.inRange(center.add(6.1, 0.0, 0.0), center, 6.0))
    }

    @Test
    fun `CD-ASG-10 2_0 is in range for the Cranker and 2_1 is not`() {
        assertTrue(WorkSpots.inRange(center.add(0.0, 0.0, 2.0), center, 2.0))
        assertFalse(WorkSpots.inRange(center.add(0.0, 0.0, 2.1), center, 2.0))
    }

    @Test
    fun `CD-ASG-09 the chosen spot has a clear line to the target center`() {
        // The worker stands east of the target behind a wall at x = 2. The nearest spot behind the wall is refused.
        val walls = (0..3).flatMap { y -> (-2..2).map { z -> BlockPos(2, y, z) } }.toSet()
        val grid = GridFixture(walls, target)
        val from = Vec3(4.5, 1.0, 0.5)
        val spot = WorkSpots.find(grid, area, center, 6.0, from)
        assertNotNull(spot)
        assertTrue(area.contains(spot!!))
        assertTrue(grid.isStandable(spot.x, spot.y, spot.z))
        assertTrue(WorkSpots.hasLineOfSight(grid, Vec3.atCenterOf(spot), center))
        assertTrue(spot.x < 2, "spot $spot is behind the wall")
    }

    @Test
    fun `CD-ASG-09 a spot outside the work area is never chosen`() {
        val farTarget = BlockPos(8, 1, 0)
        val grid = GridFixture(emptySet(), farTarget)
        val spot = WorkSpots.find(grid, area, Vec3.atCenterOf(farTarget), 6.0, Vec3(14.5, 1.0, 0.5))!!
        assertTrue(spot.x <= 8)
    }

    @Test
    fun `the nearest spot to the worker wins`() {
        val grid = GridFixture(emptySet(), target)
        assertEquals(BlockPos(1, 1, 0), WorkSpots.find(grid, area, center, 6.0, Vec3(1.5, 1.0, 0.5)))
    }

    @Test
    fun `CD-ASG-09 a spot with no room for the worker's body is never chosen`() {
        val tight = BlockPos(1, 1, 0)
        val grid = object : SpotGrid by GridFixture(emptySet(), target) {
            override fun fitsBody(x: Int, y: Int, z: Int): Boolean = BlockPos(x, y, z) != tight
        }
        val spot = WorkSpots.find(grid, area, center, 6.0, Vec3(1.5, 1.0, 0.5))
        assertNotNull(spot)
        assertTrue(spot != tight, "the spot with no room for the body was chosen")
        assertTrue(grid.isStandable(spot!!.x, spot.y, spot.z) && grid.fitsBody(spot.x, spot.y, spot.z))
        assertTrue(WorkSpots.hasLineOfSight(grid, Vec3.atCenterOf(spot), center))
    }
}

class WorkerBodyTest {
    private val spot = BlockPos(6, 2, 10)

    private fun assertBox(expected: AABB, actual: AABB) {
        val pairs = listOf(
            expected.minX to actual.minX,
            expected.minY to actual.minY,
            expected.minZ to actual.minZ,
            expected.maxX to actual.maxX,
            expected.maxY to actual.maxY,
            expected.maxZ to actual.maxZ,
        )
        for ((e, a) in pairs) assertEquals(e, a, 0.01, "box $actual, expected $expected")
    }

    @Test
    fun `a worker narrower than a block and at most 2 high fills only its spot's two cells`() {
        assertTrue(WorkerBody.fitsInCell(0.6, 1.0))
        assertTrue(WorkerBody.fitsInCell(1.0, 2.0))
        val boxes = WorkerBody.boxes(spot, 0.6, 1.0)
        assertEquals(1, boxes.size)
        assertBox(AABB(6.2, 2.0, 10.2, 6.8, 3.0, 10.8), boxes.single())
    }

    @Test
    fun `a worker taller than 2 blocks needs more room than the spot's two cells`() {
        assertFalse(WorkerBody.fitsInCell(0.9, 2.4))
    }

    /** The engine snaps a worker to its spot's center, and navigation stops a Dragonair at the spot's corner (Arrival). */
    @Test
    fun `a Dragonair 1_1 blocks wide fills its spot's center and the corner where navigation stops it`() {
        assertFalse(WorkerBody.fitsInCell(1.1, 2.5))
        val (center, stop) = WorkerBody.boxes(spot, 1.1, 2.5)
        assertBox(AABB(5.95, 2.0, 9.95, 7.05, 4.5, 11.05), center)
        assertBox(AABB(6.45, 2.0, 10.45, 7.55, 4.5, 11.55), stop)
    }

    @Test
    fun `a Camerupt 2_4 blocks wide stops 1_5 blocks from the spot's corner`() {
        val (center, stop) = WorkerBody.boxes(spot, 2.4, 1.8)
        assertBox(AABB(5.3, 2.0, 9.3, 7.7, 3.8, 11.7), center)
        assertBox(AABB(6.3, 2.0, 10.3, 8.7, 3.8, 12.7), stop)
    }
}
