package codes.honor.cobblemondynamics.role

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation

/**
 * The work effect of a role. [loopTicks] is the interval of a worker whose model has none of the [animations]. Any other worker plays
 * its animation to the end and then idles for [idleTicks] before the next play (CD-FX-01).
 */
data class EffectDefinition(
    val loopTicks: Int,
    val idleTicks: Int,
    val animations: List<String>,
    val actorParticles: List<ResourceLocation>,
    val targetParticles: List<ResourceLocation>,
)

data class RoleDefinition(
    val id: ResourceLocation,
    val handler: ResourceLocation,
    val types: Set<String>,
    val autoAssign: Boolean,
    val priority: Int,
    val range: Double,
    val params: JsonObject,
    val effect: EffectDefinition,
)
