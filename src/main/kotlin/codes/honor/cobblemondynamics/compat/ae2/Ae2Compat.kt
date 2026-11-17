package codes.honor.cobblemondynamics.compat.ae2

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation

/**
 * The Cranker on the AE2 Crank (CD-KIN-06). This package and `mixin/compat/ae2` are the only main sources that name AE2, through its
 * `ICrankable` API, and the Crystal Tender stays tag-only (CD-CRY-05, R83). The mod calls [register] only while AE2 is loaded.
 */
object Ae2Compat {
    private val CRANK: ResourceLocation = ResourceLocation.fromNamespaceAndPath("ae2", "crank")

    fun register() {
        HandlerRegistry.register(Ae2CrankHandler)
        CompatLines.register("ae2", "ae2_crank", AdapterNeeds(blocks = listOf(CRANK)))
    }
}
