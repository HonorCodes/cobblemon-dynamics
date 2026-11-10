package codes.honor.cobblemondynamics.bed

import codes.honor.cobblemondynamics.claim.TargetKey
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max

/** A bed that a Tired worker can claim: its claim key, the point where a sleeper lies, and its tier, 1 to 4. */
data class BedCandidate(val key: TargetKey, val center: Vec3, val tier: Int) {
    companion object {
        /** A sleeper lies on the top of the bed, at the center of its block. */
        fun sleepPoint(pos: BlockPos): Vec3 = Vec3(pos.x + 0.5, pos.y + BedBlock.HEIGHT, pos.z + 0.5)
    }
}

object BedSelector {
    /** A Pokémon this close to the sleep point lies on its bed. */
    private const val REACH = 0.5

    /** The highest that a sleeper may stand above or below the sleep point and still reach it. */
    private const val REACH_HEIGHT = 1.5

    /**
     * The nearest free bed (CD-BED-05). On a distance tie the higher tier wins (CD-BED-06), then the lowest position, so the choice
     * is stable. The caller passes only the beds inside the pasture's work area (CD-BED-07).
     */
    fun pick(from: Vec3, beds: List<BedCandidate>, isFree: (TargetKey) -> Boolean): BedCandidate? = beds.asSequence()
        .filter { isFree(it.key) }
        .minWithOrNull(compareBy<BedCandidate> { it.center.distanceToSqr(from) }.thenByDescending { it.tier }.thenBy(KEY_ORDER) { it.key })

    /**
     * True when a Pokémon at [pos], [width] blocks wide, has reached the sleep point [center]: within 0.5 blocks, or within half its
     * width when it is wider. Navigation stops a wide Pokémon that far from its last path node, as it does at a work spot, so a bed of
     * any size of Pokémon is reachable (CD-BED-09).
     */
    fun isAt(pos: Vec3, width: Double, center: Vec3): Boolean {
        val dx = pos.x - center.x
        val dz = pos.z - center.z
        val reach = max(REACH, width / 2.0)
        return dx * dx + dz * dz <= reach * reach && abs(pos.y - center.y) < REACH_HEIGHT
    }

    /** Block keys by `BlockPos.asLong()`, before entity keys by UUID. */
    private val KEY_ORDER: Comparator<TargetKey> = Comparator { a, b ->
        when {
            a is TargetKey.Block && b is TargetKey.Block -> a.pos.compareTo(b.pos)
            a is TargetKey.Entity && b is TargetKey.Entity -> a.uuid.compareTo(b.uuid)
            a is TargetKey.Block -> -1
            else -> 1
        }
    }
}
