package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation

/**
 * The Railcraft Reborn integration: the Stoker on its blast furnace and on the blocks of its solid-fueled firebox (M4 and M5 of
 * ADR-0020). The mod calls [register] only while Railcraft Reborn is loaded.
 */
object RailcraftCompat {
    private const val MOD = "railcraft"

    fun register() {
        val blocks = listOf("blast_furnace_bricks", "solid_fueled_firebox").map { ResourceLocation.fromNamespaceAndPath(MOD, it) }
        CompatLines.register(MOD, "railcraft_fuel", AdapterNeeds(blocks = blocks))
        HandlerRegistry.register(RailcraftFuelHandler)
    }
}
