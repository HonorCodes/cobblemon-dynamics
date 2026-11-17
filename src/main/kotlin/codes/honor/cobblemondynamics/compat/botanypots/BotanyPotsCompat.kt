package codes.honor.cobblemondynamics.compat.botanypots

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation

/**
 * The Botany Pots integration: the Cultivator and the Harvester on every pot (ADR-0020). Only [PotAdapter] names Botany Pots' classes.
 * The mod calls [register] only while Botany Pots is loaded.
 */
object BotanyPotsCompat {
    private const val MOD = "botanypots"
    private val POT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD, "terracotta_botany_pot")

    fun register() {
        CompatLines.register(MOD, "pot_cultivator", AdapterNeeds(blocks = listOf(POT)))
        CompatLines.register(MOD, "pot_harvester", AdapterNeeds(blocks = listOf(POT)))
        HandlerRegistry.register(PotCultivatorHandler)
        HandlerRegistry.register(PotHarvesterHandler)
    }
}
