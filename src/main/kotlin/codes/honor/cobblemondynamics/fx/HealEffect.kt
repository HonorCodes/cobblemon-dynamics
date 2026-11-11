package codes.honor.cobblemondynamics.fx

import net.minecraft.resources.ResourceLocation

/** Cobblemon's action effects as the heal effect sees them: the timeline of an id, or null. */
fun interface ActionEffectLookup {
    fun find(id: ResourceLocation): Any?
}

/** What a completed heal shows. No Cobblemon heal move has a move effect in 1.8.4, so the fallback is what plays today (CD-HEAL-15). */
object HealEffect {
    val FLORAL_HEALING: ResourceLocation = ResourceLocation.parse("cobblemon:floralhealing")

    /** The healer's animation, when its model has one with no sound. Never its cry, because work is silent (CD-FX-09). */
    val FALLBACK_ANIMATIONS: List<String> = listOf("special")

    /**
     * On the ally. Not `cobblemon:heal_sparkles`: its file calls `math.random()` with no arguments, which Cobblemon 1.8.4's MoLang
     * rejects, and the client crashes while it ticks the particle.
     */
    val FALLBACK_PARTICLES: List<ResourceLocation> = listOf(ResourceLocation.parse("cobblemon:heal_circles"))

    val ALLY_LOCATORS: List<String> = listOf("middle")

    sealed interface Choice {
        /** Cobblemon's server data holds the Floral Healing timeline [effect] (CD-HEAL-16). */
        data class FloralHealing(val effect: Any) : Choice

        data object Fallback : Choice
    }

    fun choose(lookup: ActionEffectLookup): Choice = lookup.find(FLORAL_HEALING)?.let { Choice.FloralHealing(it) } ?: Choice.Fallback
}
