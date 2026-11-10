package codes.honor.cobblemondynamics.heal

import codes.honor.cobblemondynamics.config.EngineSettings
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** What a healer knows of one Pokémon of its pasture: its id, position, Fairy type, Tired flag and Work Stamina from 0 to 1. */
data class AllyView(val id: UUID, val pos: Vec3, val hasFairyType: Boolean, val tired: Boolean, val fraction: Double)

/** Who heals whom. A healer is a Fairy-type with no machine binding (CD-HEAL-01), so its recovery rule is the healer rule. */
object HealTargets {
    private const val EPSILON = 1e-9

    /** A heal costs `healCost`, so a healer starts one only with that much Work Stamina (CD-HEAL-02), and never while Tired (CD-HEAL-03). */
    fun canStart(healerFraction: Double, healerTired: Boolean, s: EngineSettings): Boolean =
        !healerTired && healerFraction >= s.healCost - EPSILON

    /**
     * An ally needs a heal while it is in the healer's pasture, has no Fairy type, and is Tired or under `lowStaminaThreshold`. A null
     * ally has left the pasture. A target that stops qualifying cancels its heal (CD-HEAL-11).
     */
    fun qualifies(ally: AllyView?, s: EngineSettings): Boolean =
        ally != null && !ally.hasFairyType && (ally.tired || ally.fraction < s.lowStaminaThreshold)

    /**
     * The ally that a healer goes to, from the Pokémon of its own pasture: never the healer itself or a Fairy-type (CD-HEAL-06), and
     * never one that another healer claimed (CD-HEAL-09). The nearest Tired ally comes first (CD-HEAL-07), else the ally with the
     * lowest Work Stamina under the threshold (CD-HEAL-08). Ties go to the nearer ally, then to the lower id.
     */
    fun pick(healerId: UUID, healerPos: Vec3, allies: List<AllyView>, isFree: (UUID) -> Boolean, s: EngineSettings): AllyView? {
        val open = allies.filter { it.id != healerId && qualifies(it, s) && isFree(it.id) }
        val nearest = compareBy<AllyView> { it.pos.distanceToSqr(healerPos) }.thenBy { it.id }
        return open.filter { it.tired }.minWithOrNull(nearest) ?: open.minWithOrNull(compareBy<AllyView> { it.fraction }.then(nearest))
    }
}
