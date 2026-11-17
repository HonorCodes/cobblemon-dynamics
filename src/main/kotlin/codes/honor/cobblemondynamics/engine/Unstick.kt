package codes.honor.cobblemondynamics.engine

import net.minecraft.util.Mth

/**
 * Where a Traveling worker goes when Cobblemon's navigation reports it stuck, after 100 ticks with no progress. Near a block that fills
 * only part of its cell, such as the pasture block, two dead ends hold a worker narrower than a block for good:
 * - Cobblemon starts each path in the cell at the lowest corner of the mob's box (`OmniPathNodeMaker.getStart`). Pressed against such
 *   a block on its north or west side, that corner lies in the block's cell, which the worker never enters, so every path fails.
 * - A worker just under a block wide that stands a hair off the middle of its cell, beside the corner of a block, hits that corner on
 *   its way to the next cell. The sideways step that would clear it is below the speed that vanilla rounds to 0 on each tick.
 * From the middle of the cell that holds its center, neither happens, and its next path starts in that cell.
 */
object Unstick {
    /** The middle of the cell that holds ([x], [z]) for a worker [bbWidth] wide, or null when it stays there or no cell holds it. */
    fun middle(x: Double, z: Double, bbWidth: Double): Pair<Double, Double>? {
        if (bbWidth >= 1.0) return null
        val middleX = Mth.floor(x) + 0.5
        val middleZ = Mth.floor(z) + 0.5
        return if (middleX == x && middleZ == z) null else middleX to middleZ
    }
}
