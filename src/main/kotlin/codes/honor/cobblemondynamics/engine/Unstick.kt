package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth

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

    /** CD-ASG-25: how far, on x and z, a worker whose body a grown tree filled may move to a free cell. */
    const val FREE_RADIUS = 3

    /**
     * CD-ASG-25: where a worker in cell [at] may go when a tree grows into its body, nearest first: the cells within [FREE_RADIUS] on x
     * and z and 1 on y, and its work [spot] wherever it is. A tie goes to the spot, then to the lowest `BlockPos.asLong()`. The engine
     * takes the first cell with a floor inside the work area where the body fits.
     */
    fun freeCells(at: BlockPos, spot: BlockPos?): List<BlockPos> {
        val cells = LinkedHashSet<BlockPos>()
        spot?.let(cells::add)
        for (dx in -FREE_RADIUS..FREE_RADIUS) {
            for (dy in -1..1) for (dz in -FREE_RADIUS..FREE_RADIUS) cells += at.offset(dx, dy, dz)
        }
        return cells.sortedWith(compareBy<BlockPos>({ it.distSqr(at) }, { it != spot }, { it.asLong() }))
    }
}

/**
 * Whether a Traveling worker's travel has stalled: it stayed within a quarter block of one point for 100 ticks, the interval of
 * vanilla's own stuck check. That check compares the progress with a distance that scales with the mob's speed, and Cobblemon's move
 * control sets that speed to 0 in flight and in water (`PokemonMoveControl`), so the navigation never reports a stuck worker there.
 */
class TravelStall {
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0
    private var since = 0L
    private var last = Long.MIN_VALUE

    /** Takes each Traveling tick. True once per 100 ticks while the worker stays near one point. A tick without a call starts anew. */
    fun stalled(now: Long, x: Double, y: Double, z: Double): Boolean {
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        if (now != last + 1 || dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) {
            this.x = x
            this.y = y
            this.z = z
            since = now
        }
        last = now
        if (now - since < TICKS) return false
        since = now
        return true
    }

    private companion object {
        const val TICKS = 100L
        const val RADIUS = 0.25
    }
}
