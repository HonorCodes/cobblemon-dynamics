package codes.honor.cobblemondynamics.compat.ironfurnaces

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation

/** The Stoker on the furnaces of Iron Furnaces (CD-FUEL-07). The mod calls [register] only while Iron Furnaces is loaded. */
object IronFurnacesCompat {
    private val FURNACES: List<ResourceLocation> =
        listOf("copper", "iron", "silver", "gold", "diamond", "emerald", "obsidian", "crystal", "netherite", "million").map {
            ResourceLocation.fromNamespaceAndPath("ironfurnaces", "${it}_furnace")
        }

    fun register() {
        HandlerRegistry.register(IronFurnaceHandler)
        CompatLines.register("ironfurnaces", "iron_furnace", AdapterNeeds(blocks = FURNACES))
    }
}
