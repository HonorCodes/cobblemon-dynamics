package codes.honor.cobblemondynamics.compat.forbidden_arcanus

import net.minecraft.core.BlockPos
import kotlin.math.abs

/** A Clibano's private `burnTime` and `burnDuration`, whose ratio its flame gauge shows. */
data class ClibanoBurn(val time: Int, val duration: Int)

/** The pure rules of the Stoker on the Clibano of Forbidden & Arcanus 2.6.1 (CD-FUEL-10, CD-FUEL-13, CD-FUEL-14). */
object ClibanoRule {
    /**
     * A lit Clibano's own tick takes 1 burn tick, and one that is not lit burns a fuel item while it can smelt. The mixin keeps the burn
     * time at the start of that tick, so 2 burn ticks keep the fuel slot untouched, and the Clibano burns its own fuel again 2 ticks
     * after the work stops.
     */
    const val HEAT_TICKS = 2

    /**
     * CD-FUEL-10: the burn at the start of a worked tick. The worker gives the burn of normal fire only: it never changes the fire type,
     * so soul fire and enchanted fire still take the player's souls. The Clibano's own fuel keeps its own values.
     */
    fun keep(burn: ClibanoBurn): ClibanoBurn = if (burn.time >= HEAT_TICKS) burn else ClibanoBurn(HEAT_TICKS, HEAT_TICKS)

    /** The main block sits in the middle of the 3 by 3 by 3 Clibano, so every block of the cube is part of the target. */
    fun isPart(canonical: BlockPos, pos: BlockPos): Boolean =
        abs(pos.x - canonical.x) <= 1 && abs(pos.y - canonical.y) <= 1 && abs(pos.z - canonical.z) <= 1
}
