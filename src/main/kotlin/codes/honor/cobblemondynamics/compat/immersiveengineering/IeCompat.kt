package codes.honor.cobblemondynamics.compat.immersiveengineering

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

/**
 * The Immersive Engineering integration: the Stoker on its furnaces, the Wheel Driver on its watermill and Tailwind on its windmill
 * (ADR-0020). The mod calls [register] only while Immersive Engineering is loaded.
 */
object IeCompat {
    private const val MOD = "immersiveengineering"
    private const val WATERMILL = "blusunrize.immersiveengineering.common.blocks.wooden.WatermillBlockEntity"
    private const val WINDMILL = "blusunrize.immersiveengineering.common.blocks.wooden.WindmillBlockEntity"

    fun register(modBus: IEventBus) {
        IeAttachments.register(modBus)
        val furnaces = listOf("alloy_smelter", "blast_furnace", "advanced_blast_furnace").map(::block)
        CompatLines.register(MOD, "ie_furnace", AdapterNeeds(blocks = furnaces))
        CompatLines.register(
            MOD,
            "ie_watermill",
            AdapterNeeds(blocks = listOf(block("watermill")), mixins = listOf(MixinState.IE_WATERMILL)),
        )
        CompatLines.register(MOD, "ie_windmill", AdapterNeeds(blocks = listOf(block("windmill")), mixins = listOf(MixinState.IE_WINDMILL)))
        // Common setup comes before the role files load, which read whether the mill hooks applied (CD-OPT-09).
        modBus.addListener<FMLCommonSetupEvent> {
            it.enqueueWork(
                Runnable {
                    CompatClasses.load(WATERMILL)
                    CompatClasses.load(WINDMILL)
                },
            )
        }
        HandlerRegistry.register(IeFurnaceHandler)
        HandlerRegistry.register(IeWatermillHandler)
        HandlerRegistry.register(IeWindmillHandler)
    }

    private fun block(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD, path)
}
