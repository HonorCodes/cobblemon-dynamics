package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation

/**
 * The Railcraft Reborn integration: the Stoker on its blast furnace, and on the blocks of its solid-fueled firebox through the Work
 * Leash (M4 and M5 of ADR-0020, R85). The mod calls [register] only while Railcraft Reborn is loaded.
 */
object RailcraftCompat {
    private const val MOD = "railcraft"

    fun register() {
        CompatLines.register(MOD, "railcraft_fuel", AdapterNeeds(blocks = listOf(block("blast_furnace_bricks"))))
        CompatLines.register(MOD, "railcraft_firebox", AdapterNeeds(blocks = listOf(block("solid_fueled_firebox"))))
        HandlerRegistry.register(RailcraftFuelHandler)
        HandlerRegistry.register(RailcraftFireboxHandler)
    }

    private fun block(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD, path)
}
