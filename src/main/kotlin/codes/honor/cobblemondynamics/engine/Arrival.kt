package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.max

/**
 * When a walking worker has reached its work spot (CD-ASG-11, CD-NAV-02).
 */
object Arrival {
    const val SURFACE_OFFSET = 0.001
    private const val FOOT_TOLERANCE = 1e-4
    private const val MIN_REACH = 0.75
    private const val MAX_DY = 1.5

    fun snapY(supportTopY: Double): Double = supportTopY + SURFACE_OFFSET

    /** How far from the spot's lowest corner, on x and z, navigation stops a mob [bbWidth] blocks wide. */
    fun stopOffset(bbWidth: Double): Double = (bbWidth + 1.0).toInt() * 0.5

    fun isAt(x: Double, y: Double, z: Double, bbWidth: Double, spot: BlockPos, supportTopY: Double = spot.y.toDouble()): Boolean {
        if (Mth.floor(x) == spot.x && Mth.floor(z) == spot.z && y >= supportTopY - FOOT_TOLERANCE && y < supportTopY + 1.0) return true
        val stop = stopOffset(bbWidth)
        val reach = max(MIN_REACH, bbWidth / 2.0)
        return abs(x - (spot.x + stop)) <= reach && abs(z - (spot.z + stop)) <= reach && abs(y - supportTopY) < MAX_DY
    }
}
