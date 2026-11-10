package codes.honor.cobblemondynamics.heal

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.stamina.Stamina
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** One heal: the healer pays and the ally is full, both in the same tick or neither. */
object HealTransaction {
    /** A heal completes only this close to the ally (CD-HEAL-10). */
    const val RANGE = 2.0

    private const val EPSILON = 1e-9

    fun inRange(healerPos: Vec3, allyPos: Vec3): Boolean = healerPos.distanceToSqr(allyPos) <= RANGE * RANGE + EPSILON

    /**
     * The point of the ally's body nearest to [from]. The engine measures the range to the body, because a healer stops at the body
     * of a wide ally, whose center can be far beyond 2 blocks.
     */
    fun nearestPoint(allyBox: AABB, from: Vec3): Vec3 = Vec3(
        from.x.coerceIn(allyBox.minX, allyBox.maxX),
        from.y.coerceIn(allyBox.minY, allyBox.maxY),
        from.z.coerceIn(allyBox.minZ, allyBox.maxZ),
    )

    /** The healer loses `healCost`, 25 points, and is Tired at 0% (CD-HEAL-04). The ally is full, whatever the levels (CD-HEAL-05). */
    fun complete(healer: Stamina, ally: Stamina, s: EngineSettings) {
        healer.spend(s.healCost)
        ally.fill()
    }
}
