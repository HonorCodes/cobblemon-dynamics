package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/**
 * CD-HRV-06: the face of the crop that its drops come out of. Toward the worker's work spot, the drops land beside the crop, inside
 * the pasture's work area where a Magnet collects them, and outside a bush that damages items.
 */
object DropFace {
    /** The horizontal face of [crop] toward [spot], or the top face when [spot] is in the crop's column. */
    fun toward(crop: BlockPos, spot: BlockPos): Direction {
        val dx = spot.x - crop.x
        val dz = spot.z - crop.z
        if (dx == 0 && dz == 0) return Direction.UP
        return Direction.getNearest(dx.toDouble(), 0.0, dz.toDouble())
    }
}
