package codes.honor.cobblemondynamics.compat.createaddition

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
 * The Superheater on the Liquid Blaze Burner of Create Crafts & Additions, through the hook in `mixin/compat/createaddition`
 * (CD-MCH-01). The mod needs Create, so this package names Create's heat levels too. The mod calls [register] only while Create Crafts &
 * Additions is loaded.
 */
object CreateAdditionCompat {
    private const val BURNER = "com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlockEntity"
    private val BLOCK: ResourceLocation = ResourceLocation.fromNamespaceAndPath("createaddition", "liquid_blaze_burner")

    fun register(modBus: IEventBus) {
        HandlerRegistry.register(LiquidBurnerHandler)
        CompatLines.register(
            "createaddition",
            "liquid_burner",
            AdapterNeeds(blocks = listOf(BLOCK), mixins = listOf(MixinState.LIQUID_BLAZE_BURNER)),
        )
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { LiquidBurnerHeat.reset() }
        // Common setup comes before the role files load, which read whether the hook applied.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(BURNER) }) }
    }
}
