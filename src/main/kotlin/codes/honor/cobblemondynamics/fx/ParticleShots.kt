package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.EffectDefinition
import net.minecraft.resources.ResourceLocation

/** Where one particle effect of a work loop plays. */
sealed interface ParticleShot {
    val id: ResourceLocation

    /** Streams from the worker toward the focus entity, as actor particles do in Cobblemon's move timelines. */
    data class FromWorker(override val id: ResourceLocation) : ParticleShot

    /** Bursts at the focus entity's position. Cobblemon's entity-sourced packet spawns a particle on its source, the worker. */
    data class AtFocus(override val id: ResourceLocation) : ParticleShot
}

object ParticleShots {
    /** Actor particles first, then target particles (CD-FX-04). [known] drops the ids that the loaded Cobblemon lacks. */
    fun of(effect: EffectDefinition, known: (List<ResourceLocation>) -> List<ResourceLocation>): List<ParticleShot> =
        known(effect.actorParticles).map(ParticleShot::FromWorker) + known(effect.targetParticles).map(ParticleShot::AtFocus)
}
