package codes.honor.cobblemondynamics.compat.ars_nouveau

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation

/** The Stoker on the Volcanic Sourcelink of Ars Nouveau (CD-FUEL-12). The mod calls [register] only while Ars Nouveau is loaded. */
object ArsNouveauCompat {
    private val SOURCELINK: ResourceLocation = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "volcanic_sourcelink")

    fun register() {
        HandlerRegistry.register(VolcanicSourcelinkHandler)
        CompatLines.register("ars_nouveau", "volcanic_sourcelink", AdapterNeeds(blocks = listOf(SOURCELINK)))
    }
}
