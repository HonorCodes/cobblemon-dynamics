package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

/** The block view that work spot selection needs. Unit tests use a fixed grid. */
interface SpotGrid {
    /** A floor below, and room for the body at [y] and `y + 1`. */
    fun isStandable(x: Int, y: Int, z: Int): Boolean

    /** The caller excludes the target's own blocks. */
    fun blocksSight(x: Int, y: Int, z: Int): Boolean

    /** Room for the worker's whole body at the spot ([WorkerBody]). A body within the spot's two cells needs only [isStandable]. */
    fun fitsBody(x: Int, y: Int, z: Int): Boolean = true
}

/**
 * Where a worker's body is when it works a spot. The engine snaps a worker to the spot's center, and navigation stops a mob 1 block
 * wide or wider short of the center, where [Arrival] measures from. A spot is standable for a worker only when both places have room
 * for its body, so a worker wider than a block never gets a spot in a gap that it cannot enter (CD-ASG-09).
 */
object WorkerBody {
    /** A box this much smaller than the body, so a body that only touches a block does not count as inside it. */
    private const val TOUCH = 1e-3

    /** True when the body stays inside the spot's column of two cells, which [SpotGrid.isStandable] checks already. */
    fun fitsInCell(width: Double, height: Double): Boolean = width <= 1.0 && height <= 2.0

    /** The body on the spot's center, then, when it differs, the body where navigation stops it. */
    fun boxes(spot: BlockPos, width: Double, height: Double): List<AABB> {
        val half = width / 2.0
        fun at(x: Double, z: Double) = AABB(x - half, spot.y.toDouble(), z - half, x + half, spot.y + height, z + half).deflate(TOUCH)
        val center = at(spot.x + 0.5, spot.z + 0.5)
        val stop = Arrival.stopOffset(width)
        return if (stop == 0.5) listOf(center) else listOf(center, at(spot.x + stop, spot.z + stop))
    }
}

/**
 * The blocks of a target, as the work spot preference sees them (CD-ASG-09): its base height, the lowest y of its blocks, and its
 * footprint, the columns (x, z) that its blocks fill.
 */
class TargetShape(blocks: Collection<BlockPos>) {
    private val cells: Set<Long> = blocks.mapTo(HashSet()) { it.asLong() }
    private val columns: Set<Long> = blocks.mapTo(HashSet()) { column(it.x, it.z) }

    val baseY: Int = blocks.minOf { it.y }

    /** A spot stands on the target when the block under it is one of the target's blocks. */
    fun standsOn(x: Int, y: Int, z: Int): Boolean = BlockPos.asLong(x, y - 1, z) in cells

    /** A spot is beside the target at the base height, in a column that touches the footprint on one of its four sides. */
    fun isBeside(x: Int, y: Int, z: Int): Boolean =
        y == baseY && column(x, z) !in columns && SIDES.any { (dx, dz) -> column(x + dx, z + dz) in columns }

    companion object {
        private val SIDES = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        private fun column(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

        fun of(pos: BlockPos): TargetShape = TargetShape(listOf(pos))

        /** The blocks of the target at [canonical] within one block of it, which holds a Create large water wheel. */
        fun around(canonical: BlockPos, isPart: (BlockPos) -> Boolean): TargetShape {
            val near = BlockPos.betweenClosed(canonical.offset(-1, -1, -1), canonical.offset(1, 1, 1)).map(BlockPos::immutable)
            return TargetShape(near.filter(isPart) + canonical)
        }
    }
}

object WorkSpots {
    private const val EPSILON = 1e-9

    /** The preference of a spot (CD-ASG-09): beside the target at its base height, else at that height, else at another height. */
    private enum class Place { BESIDE, LEVEL, OTHER_HEIGHT }

    private data class Rank(val place: Place, val toTarget: Double, val toWorker: Double, val pos: Long) : Comparable<Rank> {
        override fun compareTo(other: Rank): Int = compareValuesBy(this, other, { it.place }, { it.toTarget }, { it.toWorker }, { it.pos })
    }

    fun inRange(spotCenter: Vec3, targetCenter: Vec3, range: Double): Boolean =
        spotCenter.distanceToSqr(targetCenter) <= range * range + EPSILON

    /** 3D DDA through block cells; the start and end cells never block. */
    fun hasLineOfSight(grid: SpotGrid, from: Vec3, to: Vec3): Boolean {
        var x = floor(from.x).toInt()
        var y = floor(from.y).toInt()
        var z = floor(from.z).toInt()
        val endX = floor(to.x).toInt()
        val endY = floor(to.y).toInt()
        val endZ = floor(to.z).toInt()
        val d = to.subtract(from)
        val stepX = Mth.sign(d.x)
        val stepY = Mth.sign(d.y)
        val stepZ = Mth.sign(d.z)
        var tMaxX = firstCrossing(from.x, d.x, stepX)
        var tMaxY = firstCrossing(from.y, d.y, stepY)
        var tMaxZ = firstCrossing(from.z, d.z, stepZ)
        val tDeltaX = if (stepX == 0) Double.MAX_VALUE else stepX / d.x
        val tDeltaY = if (stepY == 0) Double.MAX_VALUE else stepY / d.y
        val tDeltaZ = if (stepZ == 0) Double.MAX_VALUE else stepZ / d.z
        while (!(x == endX && y == endY && z == endZ)) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX
                tMaxX += tDeltaX
            } else if (tMaxY <= tMaxZ) {
                y += stepY
                tMaxY += tDeltaY
            } else {
                z += stepZ
                tMaxZ += tDeltaZ
            }
            if (x == endX && y == endY && z == endZ) break
            if (grid.blocksSight(x, y, z)) return false
            if (minOf(tMaxX, tMaxY, tMaxZ) > 1.0 + EPSILON) break
        }
        return true
    }

    /**
     * The work spot of a worker at [from]: a standable spot in [area], within [range] of [targetCenter] and with line of sight to it,
     * that does not stand on the [target]. A spot beside the target at its base height comes first, then another spot at that height,
     * then a spot at another height, so a worker stands beside its target on the floor that the target stands on (the approver's
     * play-test ruling, 2026-11-10). Within each, the spot nearest to the target wins, then the one nearest to the worker, then the
     * lowest `BlockPos.asLong()`. A spot is standable only with room for the worker's body ([SpotGrid.fitsBody]), which the search asks
     * only of a spot that would be the best so far.
     */
    fun find(grid: SpotGrid, area: WorkArea, target: TargetShape, targetCenter: Vec3, range: Double, from: Vec3): BlockPos? {
        val r = ceil(range).toInt()
        val cx = floor(targetCenter.x).toInt()
        val cy = floor(targetCenter.y).toInt()
        val cz = floor(targetCenter.z).toInt()
        var best: BlockPos? = null
        var bestRank: Rank? = null
        for (x in cx - r..cx + r) {
            for (y in cy - r..cy + r) {
                for (z in cz - r..cz + r) {
                    val pos = BlockPos(x, y, z)
                    if (!area.contains(pos) || target.standsOn(x, y, z) || !grid.isStandable(x, y, z)) continue
                    val c = Vec3.atCenterOf(pos)
                    if (!inRange(c, targetCenter, range) || !hasLineOfSight(grid, c, targetCenter)) continue
                    val place = when {
                        target.isBeside(x, y, z) -> Place.BESIDE
                        y == target.baseY -> Place.LEVEL
                        else -> Place.OTHER_HEIGHT
                    }
                    val rank = Rank(place, c.distanceToSqr(targetCenter), c.distanceToSqr(from), pos.asLong())
                    if ((bestRank == null || rank < bestRank) && grid.fitsBody(x, y, z)) {
                        best = pos
                        bestRank = rank
                    }
                }
            }
        }
        return best
    }

    /** The work spot at a target of one block, the cell of [targetCenter]. */
    fun find(grid: SpotGrid, area: WorkArea, targetCenter: Vec3, range: Double, from: Vec3): BlockPos? =
        find(grid, area, TargetShape.of(BlockPos.containing(targetCenter)), targetCenter, range, from)

    /** The ray parameter at which [p] first crosses a cell boundary in direction [step]. */
    private fun firstCrossing(p: Double, dp: Double, step: Int): Double = when {
        step == 0 -> Double.MAX_VALUE
        step > 0 -> (floor(p) + 1 - p) / dp
        else -> (floor(p) - p) / dp
    }
}
