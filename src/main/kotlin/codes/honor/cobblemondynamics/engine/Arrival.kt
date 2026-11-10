package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.max

/**
 * When a walking worker has reached its work spot (CD-ASG-11). Navigation steers a mob to `node + (int)(bbWidth + 1) / 2` on x
 * and z (`Path.getEntityPosAtNode`), and takes a node as reached within `bbWidth / 2`, at least 0.75 − `bbWidth / 2`, on each
 * axis (`maxDistanceToWaypoint`, the same in Cobblemon's `OmniPathNavigation`). That point is the spot's center for a mob
 * narrower than a block, and the spot's corner for one 1 to 2 blocks wide, so the check centres there too.
 */
object Arrival {
    private const val MIN_REACH = 0.75
    private const val MAX_DY = 1.5

    /** How far from the spot's lowest corner, on x and z, navigation stops a mob [bbWidth] blocks wide. */
    fun stopOffset(bbWidth: Double): Double = (bbWidth + 1.0).toInt() * 0.5

    fun isAt(x: Double, y: Double, z: Double, bbWidth: Double, spot: BlockPos): Boolean {
        val stop = stopOffset(bbWidth)
        val reach = max(MIN_REACH, bbWidth / 2.0)
        return abs(x - (spot.x + stop)) <= reach && abs(z - (spot.z + stop)) <= reach && abs(y - spot.y) < MAX_DY
    }
}
