package codes.honor.cobblemondynamics.compat.cobblemon_incubator

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStoppedEvent

/**
 * The Stoker on the Egg Incubator of Cobblemon Incubator, through the hook in `mixin/compat/cobblemon_incubator` (CD-FUEL-11). The
 * integration names the incubator's classes only, and the mod never bundles the jar of the incubator or of Cobbreeding (ADR-0016). The
 * mod calls [register] only while Cobblemon Incubator is loaded.
 */
object CobblemonIncubatorCompat {
    private const val INCUBATOR = "com.nbp.cobblemon_incubator.blockentity.EggIncubatorBlockEntity"
    private val BLOCK: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_incubator", "egg_incubator")

    fun register(modBus: IEventBus) {
        HandlerRegistry.register(EggIncubatorHandler)
        CompatLines.register(
            "cobblemon_incubator",
            "egg_incubator",
            AdapterNeeds(blocks = listOf(BLOCK), mixins = listOf(MixinState.EGG_INCUBATOR)),
        )
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { IncubatorWarmth.reset() }
        // Common setup comes before the role files load, which read whether the hook applied.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(INCUBATOR) }) }
    }
}
