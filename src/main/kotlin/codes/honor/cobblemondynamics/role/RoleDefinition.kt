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

/**
 * One role file. [quickWork] is null when the file has no `quick_work` key: an auto-assign role then takes Quick Work orders, and a
 * role whose [autoAssign] is false does not. True lets a role whose [autoAssign] is false take orders, which wait for their target's
 * demand (CD-QWK-36), as the Muse's file does. False keeps an auto-assign role out of Quick Work (CD-FRZ-02).
 */
data class RoleDefinition(
    val id: ResourceLocation,
    val handler: ResourceLocation,
    val types: Set<String>,
    val autoAssign: Boolean,
    val priority: Int,
    val range: Double,
    val params: JsonObject,
    val effect: EffectDefinition,
    val quickWork: Boolean? = null,
)
