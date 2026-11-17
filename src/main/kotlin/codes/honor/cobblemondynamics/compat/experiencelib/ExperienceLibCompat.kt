package codes.honor.cobblemondynamics.compat.experiencelib

import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.xp.ExperienceCollectorBlockEntity
import es.degrassi.experiencelib.api.capability.ExperienceLibCapabilities
import es.degrassi.experiencelib.api.capability.IExperienceHandler
import net.minecraft.core.Direction
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

/**
 * CD-XPC-11: the back and the bottom of an Experience Collector offer experiencelib's XP capability, so an Applied Experienced import
 * bus on either face takes the XP into its ME network with no fluid step. The mod calls [register] only while experiencelib is loaded.
 */
object ExperienceLibCompat {
    const val ADAPTER = "collector_xp"

    fun register(modBus: IEventBus) {
        CompatLines.register("experiencelib", ADAPTER)
        modBus.addListener<RegisterCapabilitiesEvent> { event ->
            event.registerBlockEntity(
                ExperienceLibCapabilities.EXPERIENCE.block(),
                DynamicsBlocks.EXPERIENCE_COLLECTOR_TYPE.get(),
                ICapabilityProvider<ExperienceCollectorBlockEntity, Direction?, IExperienceHandler> { collector, side ->
                    if (collector.isOutput(side)) CollectorExperienceHandler(collector.store, collector::setChanged) else null
                },
            )
        }
    }
}
