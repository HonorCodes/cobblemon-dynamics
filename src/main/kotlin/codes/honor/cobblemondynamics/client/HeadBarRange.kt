package codes.honor.cobblemondynamics.client

import net.minecraft.world.phys.Vec3

/** Which workers show a head bar, and its parts in name-tag pixels around the point above the head. */
object HeadBarRange {
    /** The state icon, then the bar to its right, the pair centred on the point. */
    val ICON = Rect(-20, -4, 8, 8)
    val BAR = Rect(-10, -2, 30, 4)

    /** CD-UI-06: within [range] blocks of the viewer, measured in three dimensions, so 16.0 shows and 16.1 does not. */
    fun inRange(viewer: Vec3, worker: Vec3, range: Int): Boolean = viewer.distanceToSqr(worker) <= range.toDouble() * range
}
