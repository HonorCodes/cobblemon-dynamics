package codes.honor.cobblemondynamics.compat.mekanism

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import mekanism.api.heat.IHeatHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.capabilities.BlockCapability

/**
 * The Mekanism integration: the Stoker on the Fuelwood Heater and the Heater on the valves of the Thermal Evaporation Plant and the
 * Thermoelectric Boiler (ADR-0020). The mod calls [register] only while Mekanism is loaded.
 */
object MekanismCompat {
    private const val MOD = "mekanism"

    /**
     * Mekanism's `mekanism:heat_handler`. NeoForge gives back the one capability of a name and a type, so this is Mekanism's own
     * entry, with no class of `mekanism.common` loaded.
     */
    val HEAT: BlockCapability<IHeatHandler, Direction?> =
        BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MOD, "heat_handler"), IHeatHandler::class.java)

    fun register() {
        CompatLines.register(MOD, "fuelwood_heater", AdapterNeeds(blocks = listOf(block("fuelwood_heater"))))
        CompatLines.register(MOD, "mekanism_heat", AdapterNeeds(blocks = listOf(block("thermal_evaporation_valve"), block("boiler_valve"))))
        HandlerRegistry.register(FuelwoodHeaterHandler)
        HandlerRegistry.register(MekanismHeatHandler)
    }

    /** The heat handler on [side] of [pos], when it has a capacitor. A position in an unloaded chunk has none. */
    fun heatAt(level: ServerLevel, pos: BlockPos, side: Direction): IHeatHandler? =
        if (level.isLoaded(pos)) level.getCapability(HEAT, pos, side)?.takeIf { it.heatCapacitorCount > 0 } else null

    private fun block(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD, path)
}
