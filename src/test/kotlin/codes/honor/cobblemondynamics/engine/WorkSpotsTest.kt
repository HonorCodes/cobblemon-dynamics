package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    /** A worker east of the target, on the floor. */
    private val from = Vec3(1.5, 1.0, 0.5)

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

    /** An open floor everywhere with nothing in the way of sight, where only the [standable] cells are free for the worker. */
    private class OpenGrid(private val standable: Set<BlockPos>) : SpotGrid {
        override fun isStandable(x: Int, y: Int, z: Int): Boolean = BlockPos(x, y, z) in standable

        override fun canStand(x: Int, y: Int, z: Int): Boolean = true

        override fun blocksSight(x: Int, y: Int, z: Int): Boolean = false
    }

    /** The approver's play-test ruling (2026-11-10): workers stand beside their target, on the floor that it stands on. */
    @Test
    fun `CD-ASG-09 a spot beside the target at its base height wins over the spots nearer to the worker`() {
        val grid = GridFixture(emptySet(), target)
        assertEquals(BlockPos(1, 1, 0), WorkSpots.find(grid, area, center, 6.0, Vec3(5.5, 1.0, 0.5)))
        assertEquals(BlockPos(0, 1, -1), WorkSpots.find(grid, area, center, 6.0, Vec3(0.5, 1.0, -4.5)))
    }

    @Test
    fun `CD-ASG-09 a spot that stands on the target is never chosen`() {
        val onTop = BlockPos(0, 2, 0)
        val worker = Vec3(0.5, 2.0, 0.5)
        assertEquals(BlockPos(3, 1, 3), WorkSpots.find(OpenGrid(setOf(onTop, BlockPos(3, 1, 3))), area, center, 6.0, worker))
        assertNull(WorkSpots.find(OpenGrid(setOf(onTop)), area, center, 6.0, worker))
    }

    @Test
    fun `CD-ASG-09 a spot at the target's base height wins over a nearer spot at another height`() {
        val grid = OpenGrid(setOf(BlockPos(1, 2, 0), BlockPos(3, 1, 0)))
        assertEquals(BlockPos(3, 1, 0), WorkSpots.find(grid, area, center, 6.0, Vec3(1.5, 2.0, 0.5)))
    }

    @Test
    fun `CD-ASG-09 among the other spots at the base height, the one nearest to the target wins`() {
        val grid = OpenGrid(setOf(BlockPos(2, 1, 0), BlockPos(4, 1, 0), BlockPos(0, 2, 2)))
        assertEquals(BlockPos(2, 1, 0), WorkSpots.find(grid, area, center, 6.0, Vec3(4.5, 1.0, 0.5)))
    }

    /**
     * A Create large water wheel: a ring of 3 × 3 blocks upright on the floor, here on x = 0. A spot beside any of its lowest blocks is
     * beside the target, and a spot on top of the ring stands on it.
     */
    @Test
    fun `CD-ASG-09 a spot beside the footprint of a multi-block target is beside the target`() {
        val wheel = (1..3).flatMap { y -> (-1..1).map { z -> BlockPos(0, y, z) } }
        val shape = TargetShape(wheel)
        val wheelCenter = Vec3(0.5, 2.5, 0.5)
        val grid = object : SpotGrid by GridFixture(emptySet(), BlockPos(0, 2, 0)) {
            override fun isStandable(x: Int, y: Int, z: Int): Boolean = (y == 1 && BlockPos(x, 1, z) !in wheel) || (x == 0 && y == 4)
        }
        val spot = WorkSpots.find(grid, area, shape, wheelCenter, 6.0, Vec3(5.5, 1.0, 5.5))
        assertEquals(BlockPos(1, 1, 0), spot)
        assertTrue(shape.isBeside(1, 1, -1) && shape.isBeside(-1, 1, 1) && shape.isBeside(0, 1, 2) && shape.isBeside(0, 1, -2))
        assertFalse(shape.isBeside(1, 1, 2), "a corner cell only touches the wheel at an edge")
        assertTrue(shape.standsOn(0, 4, 1))
        // A worker on top of the wheel still gets the spot beside it, and never one on the wheel.
        assertEquals(BlockPos(1, 1, 0), WorkSpots.find(grid, area, shape, wheelCenter, 6.0, Vec3(0.6, 4.0, 0.5)))
    }

    @Test
    fun `a target's shape holds its blocks within one block of its canonical position, and its base height`() {
        val ring = (1..3).flatMap { y -> (-1..1).map { z -> BlockPos(0, y, z) } }.toSet()
        val shape = TargetShape.around(BlockPos(0, 2, 0)) { it in ring }
        assertEquals(1, shape.baseY)
        assertTrue(shape.isBeside(1, 1, 0))
        assertFalse(shape.isBeside(1, 2, 0), "beside the ring, but above its base height")
        val single = TargetShape.of(target)
        assertEquals(1, single.baseY)
        val sides = listOf(BlockPos(1, 1, 0), BlockPos(-1, 1, 0), BlockPos(0, 1, 1), BlockPos(0, 1, -1))
        assertTrue(sides.all { single.isBeside(it.x, it.y, it.z) })
        assertFalse(single.isBeside(1, 1, 1), "a diagonal cell")
        assertFalse(single.isBeside(0, 1, 0), "the target's own cell")
        assertTrue(single.standsOn(0, 2, 0))
        assertFalse(single.standsOn(1, 2, 0))
    }

    /**
     * The only free cell beside the target is a pit: walls on its three other sides, at the floor's height. A worker cannot walk into it
     * on the level, and the play-test GameTests showed that a Squirtle cannot reach such a pit beside a water wheel, so every open spot
     * comes first. The other cells beside the target have a wall above them.
     */
    @Test
    fun `CD-ASG-09 a spot that a worker cannot walk into on the level, such as a pit beside the target, comes after every open spot`() {
        val pit = BlockPos(1, 1, 0)
        val pitWalls = listOf(BlockPos(2, 1, 0), BlockPos(1, 1, 1), BlockPos(1, 1, -1))
        val walls = (pitWalls + listOf(BlockPos(-1, 2, 0), BlockPos(0, 2, 1), BlockPos(0, 2, -1))).toSet()
        val grid = GridFixture(walls, target)
        assertTrue(grid.isStandable(pit.x, pit.y, pit.z), "the pit is not standable")
        assertEquals(BlockPos(-1, 1, 1), WorkSpots.find(grid, area, center, 6.0, Vec3(-3.5, 1.0, 2.5)))
        // With no open spot in reach, the pit is still a spot.
        val closedIn = object : SpotGrid by grid {
            override fun isStandable(x: Int, y: Int, z: Int): Boolean = BlockPos(x, y, z) == pit
        }
        assertEquals(pit, WorkSpots.find(closedIn, area, center, 6.0, Vec3(-3.5, 1.0, 2.5)))
    }

    /** Another worker's spot is still floor: a spot at the end of a corridor whose next cell another worker took stays open. */
    @Test
    fun `CD-ASG-09 a spot stays open when the cell beside it is another worker's spot`() {
        val corridor = (1..4).flatMap { x -> listOf(BlockPos(x, 1, 1), BlockPos(x, 1, -1)) }
        val walls = (corridor + listOf(BlockPos(-1, 2, 0), BlockPos(0, 2, 1), BlockPos(0, 2, -1))).toSet()
        val fixture = GridFixture(walls, target)
        val taken = BlockPos(2, 1, 0)
        val grid = object : SpotGrid by fixture {
            override fun isStandable(x: Int, y: Int, z: Int): Boolean = BlockPos(x, y, z) != taken && fixture.isStandable(x, y, z)

            override fun canStand(x: Int, y: Int, z: Int): Boolean = fixture.isStandable(x, y, z)
        }
        assertEquals(BlockPos(1, 1, 0), WorkSpots.find(grid, area, center, 6.0, Vec3(5.5, 1.0, 0.5)))
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

    @Test
    fun `CD-ASG-09 a best spot with no path gives way to the next best spot`() {
        val grid = GridFixture(emptySet(), target)
        val shape = TargetShape.of(target)
        val best = WorkSpots.find(grid, area, shape, center, 6.0, from)!!
        val spot = WorkSpots.findReachable(grid, area, shape, center, 6.0, from, reachable = { it != best })
        assertNotNull(spot)
        assertNotEquals(best, spot)
        assertTrue(shape.isBeside(spot!!.x, spot.y, spot.z), "the next spot $spot is not beside the target")
    }

    @Test
    fun `CD-ASG-14 a target whose three best spots have no path gets no spot`() {
        val checked = mutableListOf<BlockPos>()
        val spot = WorkSpots.findReachable(
            GridFixture(emptySet(), target),
            area,
            TargetShape.of(target),
            center,
            6.0,
            from,
            reachable = {
                checked += it
                false
            },
        )
        assertNull(spot)
        assertEquals(WorkSpots.MAX_PATH_CHECKS, checked.size)
        assertEquals(checked.size, checked.toSet().size, "a spot was checked twice: $checked")
    }

    @Test
    fun `CD-FAN-03 a handler that allows one spot gets that spot or none`() {
        val front = BlockPos(1, 1, 0)
        val shape = TargetShape.of(target)
        val far = Vec3(-3.5, 1.0, 0.5)
        assertEquals(front, WorkSpots.find(GridFixture(emptySet(), target), area, shape, center, 6.0, far, allowed = { it == front }))
        assertNull(WorkSpots.find(GridFixture(setOf(front), target), area, shape, center, 6.0, far, allowed = { it == front }))
    }

    @Test
    fun `CD-ASG-09 a reachable best spot costs one path check`() {
        val checked = mutableListOf<BlockPos>()
        val grid = GridFixture(emptySet(), target)
        val spot = WorkSpots.findReachable(grid, area, TargetShape.of(target), center, 6.0, from, reachable = {
            checked += it
            true
        })
        assertEquals(WorkSpots.find(grid, area, TargetShape.of(target), center, 6.0, from), spot)
        assertEquals(1, checked.size)
    }

    /** A sapling at the target: a narrow worker never stands in its column, and a wide one never reaches into it. */
    @Test
    fun `CD-ASG-24 no work spot puts the body in the column of a sapling that grows into a tree`() {
        val sapling = BlockPos(0, 1, 0)
        val columns = GrowthColumns(listOf(sapling))
        // A Nincada 0.4 wide: its own cell and the cells above it are out, the cells beside it are in.
        assertFalse(columns.allows(sapling, 0.4, 0.3))
        assertFalse(columns.allows(sapling.above(3), 0.4, 0.3))
        assertTrue(columns.allows(sapling.east(), 0.4, 0.3))
        assertTrue(columns.allows(sapling.below(2), 0.4, 0.3))
        // A Torterra 2.25 wide reaches 1.125 from its spot's center, and navigation stops it 1.5 from the spot's lowest corner.
        assertFalse(columns.allows(sapling.east(), 2.25, 2.4))
        assertFalse(columns.allows(sapling.west(2), 2.25, 2.4))
        assertTrue(columns.allows(sapling.east(2), 2.25, 2.4))
        assertTrue(columns.allows(sapling.west(3), 2.25, 2.4))
        // A target that grows no tree keeps every spot.
        assertTrue(GrowthColumns(emptyList()).allows(sapling, 0.4, 0.3))
    }

    @Test
    fun `CD-ASG-24 the spot at a sapling is beside it, never in its cell, also when no cell beside it has room`() {
        val sapling = BlockPos(0, 1, 0)
        val columns = GrowthColumns(listOf(sapling))
        // The four cells beside the sapling have leaves of another tree over them, so only the sapling's cell and the diagonals stand.
        val standable = setOf(sapling, BlockPos(1, 1, 1), BlockPos(-1, 1, 1), BlockPos(1, 1, -1), BlockPos(-1, 1, -1))
        val grid = OpenGrid(standable)
        val before = WorkSpots.find(grid, area, TargetShape.of(sapling), center, 6.0, from)
        assertEquals(sapling, before, "without the columns, the sapling's own cell is the nearest spot")
        val spot = WorkSpots.find(grid, area, TargetShape.of(sapling), center, 6.0, from, allowed = { columns.allows(it, 0.4, 0.3) })
        assertNotNull(spot)
        assertNotEquals(sapling, spot)
        assertTrue(spot!! in standable)
    }

    @Test
    fun `CD-ASG-24 a 2 x 2 keeps a worker out of all four columns`() {
        val corner = BlockPos(0, 1, 0)
        val columns = GrowthColumns(listOf(corner, corner.east(), corner.south(), corner.east().south()))
        for (cell in listOf(corner, corner.east(), corner.south(), corner.east().south())) assertFalse(columns.allows(cell, 0.4, 0.3))
        assertTrue(columns.allows(corner.east(2), 0.4, 0.3))
        assertTrue(columns.allows(corner.west(), 0.9, 1.4))
    }

    /** A row of saplings: the spot for one may not stand in the cell of the next one, which grows a tree of its own. */
    @Test
    fun `CD-ASG-24 no body lies in the column of a neighbouring sapling`() {
        val saplings = setOf(BlockPos(0, 1, 0), BlockPos(1, 1, 0))
        val isSapling = { x: Int, y: Int, z: Int -> BlockPos(x, y, z) in saplings }
        assertFalse(GrowthColumns.clearOfSaplings(BlockPos(1, 1, 0), 0.4, 0.3, isSapling))
        assertTrue(GrowthColumns.clearOfSaplings(BlockPos(0, 1, 1), 0.4, 0.3, isSapling))
        // On top of a block over the sapling's cell at the body's height, and one below the floor height.
        assertFalse(GrowthColumns.clearOfSaplings(BlockPos(1, 2, 0), 0.4, 1.5, isSapling))
        assertTrue(GrowthColumns.clearOfSaplings(BlockPos(1, 3, 0), 0.4, 1.5, isSapling))
        // A body 1.2 wide beside the row reaches into a sapling's column from the cell south of the row.
        assertFalse(GrowthColumns.clearOfSaplings(BlockPos(2, 1, 1), 1.2, 0.9, isSapling))
        assertTrue(GrowthColumns.clearOfSaplings(BlockPos(3, 1, 2), 1.2, 0.9, isSapling))
    }

    /** A body 1 block wide or wider is checked where navigation stops it, too, and that place alone may reach the column. */
    @Test
    fun `CD-ASG-24 the stop box of a wide body counts`() {
        val sapling = BlockPos(0, 1, 0)
        val columns = GrowthColumns(listOf(sapling))
        // A Torterra 2.25 wide two cells west: its center box ends at x = -0.375, and its stop box, centered 1.5 blocks from the
        // spot's lowest corner, reaches x = 0.625 over the sapling.
        val (center, stop) = WorkerBody.boxes(sapling.west(2), 2.25, 2.4)
        assertTrue(center.maxX < 0.0)
        assertTrue(stop.maxX > 0.0)
        assertFalse(columns.allows(sapling.west(2), 2.25, 2.4))
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
