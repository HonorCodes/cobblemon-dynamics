package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.heal.HealTargets

/**
 * Whether an unbound Cobblemon with a Fairy type takes non-Fairy auto-assign roles or preempts an auto work claim to heal
 * (CD-TEAM-09, CD-TEAM-10, ADR-0021).
 */
object DualFairyRule {
    const val FAIRY_TYPE = "fairy"

    fun autoAssignTypes(
        types: Set<String>,
        hasFairyType: Boolean,
        leashed: Boolean,
        tired: Boolean,
        staminaFraction: Double,
        allyNeedsHeal: Boolean,
        settings: EngineSettings,
    ): Set<String> {
        if (leashed || tired) return emptySet()
        if (!hasFairyType) return types
        if (allyNeedsHeal && HealTargets.canStart(staminaFraction, healerTired = false, settings)) return emptySet()
        val nonFairy = LinkedHashSet<String>(types.size)
        for (type in types) if (type != FAIRY_TYPE) nonFairy += type
        return nonFairy
    }

    fun shouldPreemptForHeal(
        hasFairyType: Boolean,
        leashed: Boolean,
        holdsWorkClaim: Boolean,
        tired: Boolean,
        staminaFraction: Double,
        allyNeedsHeal: Boolean,
        settings: EngineSettings,
    ): Boolean = hasFairyType &&
        !leashed &&
        holdsWorkClaim &&
        allyNeedsHeal &&
        HealTargets.canStart(staminaFraction, tired, settings)
}
