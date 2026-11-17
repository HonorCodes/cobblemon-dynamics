package codes.honor.cobblemondynamics.compat.forbidden_arcanus

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
 * The Stoker on the Clibano of Forbidden & Arcanus, through the hook in `mixin/compat/forbidden_arcanus` (CD-FUEL-10). The mod calls
 * [register] only while Forbidden & Arcanus is loaded.
 */
object ForbiddenArcanusCompat {
    private const val CLIBANO = "com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity"
    private val MAIN_PART: ResourceLocation = ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "clibano_main_part")

    fun register(modBus: IEventBus) {
        HandlerRegistry.register(ClibanoHandler)
        CompatLines.register("forbidden_arcanus", "clibano", AdapterNeeds(blocks = listOf(MAIN_PART), mixins = listOf(MixinState.CLIBANO)))
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { ClibanoStoking.reset() }
        // Common setup comes before the role files load, which read whether the hook applied.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(CLIBANO) }) }
    }
}
