package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import kotlin.math.abs

/**
 * Where a stuck Traveling worker goes: one that Cobblemon's navigation reports stuck after 100 ticks with no progress, or one whose
 * travel stalled ([TravelStall]). Near a block that fills only part of its cell, such as the pasture block, three dead ends hold a
 * worker narrower than a block for good:
 * - Cobblemon starts each path in the cell at the lowest corner of the mob's box (`OmniPathNodeMaker.getStart`). Pressed against such
 *   a block on its north or west side, that corner lies in the block's cell, which the worker never enters, so every path fails.
 * - A worker just under a block wide that stands a hair off the middle of its cell, beside the corner of a block, hits that corner on
 *   its way to the next cell. The sideways step that would clear it is below the speed that vanilla rounds to 0 on each tick.
 * - The screen of the pasture block's top half sits 1/16 back from its frame, in a recess 0.5 high. The pathfinder sees the top half
 *   as a whole block with a floor on top, so a path can climb over it from the screen's side. A worker under 0.5 high that flies there
 *   at the screen's height pushes into the recess, where the frame's top bar stops it from rising and the screen from going on.
 * From the middle of the cell that holds its center, the first two end, and its next path starts in that cell. In the third, that middle
 * lies inside the screen, so the worker goes on top of the block in its cell, where its path goes on.
 *
 * A worker 1 block wide or wider has no cell that holds its body, so it steps sideways instead ([sideShifts], CD-ASG-27). Cobblemon's
 * pathfinder checks at most 2 cells on x and z for a mob (`OmniPathNodeMaker.getPathTypeOfMob`), but navigation steers a mob 2 to 3
 * blocks wide to `node + 1.5` (`Path.getEntityPosAtNode`), so its body reaches 0.625 into a third row of cells that no path checks. A
 * path that runs beside the pasture block can then take the body straight into the block's face, where no sideways part of the move
 * lets it slide past, and every new path from there is the same.
 */
object Unstick {
    private const val EPSILON = 1e-6

    /** The middle of the cell that holds ([x], [z]) for a worker [bbWidth] wide, or null when it stays there or no cell holds it. */
    fun middle(x: Double, z: Double, bbWidth: Double): Pair<Double, Double>? {
        if (bbWidth >= 1.0) return null
        val middleX = Mth.floor(x) + 0.5
        val middleZ = Mth.floor(z) + 0.5
        return if (middleX == x && middleZ == z) null else middleX to middleZ
    }

    /**
     * The height on top of the block in a cell whose floor is at [cellY], for a worker whose feet are at [feetY]. The block's collision
     * shape reaches [shapeTop] above that floor, which is negative infinity for an empty shape. Null when the cell holds no block, when
     * the worker stands on the block or above it, and when the block reaches beyond its cell, as a fence does.
     */
    fun onTop(feetY: Double, cellY: Int, shapeTop: Double): Double? {
        if (!(shapeTop > 0.0 && shapeTop <= 1.0)) return null
        val top = cellY + shapeTop
        return if (top > feetY + EPSILON) top else null
    }

    /** CD-ASG-27: how far beyond the blocks a sideways step takes the body. */
    const val SIDESTEP_GAP = 0.01

    /**
     * CD-ASG-27: the sideways shifts on one axis that take a body from [bodyMin] to [bodyMax] clear of blocks that span [blockMin] to
     * [blockMax] on that axis, the shorter first, each at most [maxShift] long. None when the blocks do not overlap the body on that axis.
     */
    fun sideShifts(bodyMin: Double, bodyMax: Double, blockMin: Double, blockMax: Double, maxShift: Double): List<Double> {
        if (blockMin >= bodyMax || blockMax <= bodyMin) return emptyList()
        val down = blockMin - bodyMax - SIDESTEP_GAP
        val up = blockMax - bodyMin + SIDESTEP_GAP
        return listOf(down, up).filter { abs(it) <= maxShift }.sortedBy { abs(it) }
    }

    /** CD-ASG-25: how far, on x and z, a worker whose body a grown tree filled may move to a free cell. */
    const val FREE_RADIUS = 3

    /**
     * CD-ASG-25: the cells that a worker in cell [at] reaches from there, nearest first: a flood fill over the six faces, within
     * [FREE_RADIUS] on x and z and 1 on y, through the cells that [passable] lets it pass, such as air and the grown tree's own logs,
     * leaves and roots. [at] comes first. The work [spot] counts only when the fill reaches it, and wins a tie of distance, then the
     * lowest `BlockPos.asLong()` does.
     */
    fun reachableCells(at: BlockPos, spot: BlockPos?, passable: (BlockPos) -> Boolean): List<BlockPos> {
        val seen = LinkedHashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        seen += at
        queue += at
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (next in listOf(cell.east(), cell.west(), cell.south(), cell.north(), cell.above(), cell.below())) {
                if (abs(next.x - at.x) > FREE_RADIUS || abs(next.z - at.z) > FREE_RADIUS || abs(next.y - at.y) > 1) continue
                if (next in seen || !passable(next)) continue
                seen += next
                queue += next
            }
        }
        return seen.sortedWith(compareBy<BlockPos>({ it.distSqr(at) }, { it != spot }, { it.asLong() }))
    }
}

/**
 * Whether a Traveling worker's travel has stalled: by default, it stayed within a quarter block of one point for 100 ticks, the
 * interval of vanilla's own stuck check. That check compares the progress with a distance that scales with the mob's speed, and
 * Cobblemon's move control sets that speed to 0 in flight and in water (`PokemonMoveControl`), so the navigation never reports a stuck
 * worker there. The short watch of CD-ASG-27 takes [PRESS_TICKS] and [PRESS_RADIUS].
 */
class TravelStall(private val ticks: Long = TICKS, private val radius: Double = RADIUS) {
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0
    private var since = 0L
    private var last = Long.MIN_VALUE

    /** Takes each Traveling tick. True once per [ticks] while the worker stays within [radius] of one point. A tick without a call starts anew. */
    fun stalled(now: Long, x: Double, y: Double, z: Double): Boolean {
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        if (now != last + 1 || dx * dx + dy * dy + dz * dz > radius * radius) {
            this.x = x
            this.y = y
            this.z = z
            since = now
        }
        last = now
        if (now - since < ticks) return false
        since = now
        return true
    }

    companion object {
        private const val TICKS = 100L
        private const val RADIUS = 0.25

        /** CD-ASG-27: a pushing worker 1 block wide or wider steps sideways after 20 ticks within 0.1 blocks of one point. */
        const val PRESS_TICKS = 20L
        const val PRESS_RADIUS = 0.1
    }
}
