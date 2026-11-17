package codes.honor.cobblemondynamics.compat.mekanismgenerators

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/**
 * The Mekanism Generators integration: Tailwind on the Wind Generator (M1 of ADR-0020). The mod calls [register] only while
 * Mekanism Generators is loaded, which needs Mekanism.
 */
object MekanismGeneratorsCompat {
    private const val MOD = "mekanismgenerators"
    private const val WIND_GENERATOR = "mekanism.generators.common.tile.TileEntityWindGenerator"

    private val ATTACHMENTS: DeferredRegister<AttachmentType<*>> = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID)

    /** On a Wind Generator: the grant of its Tailwind worker. Only the server reads it, and it is never saved. */
    val WIND_GRANT: DeferredHolder<AttachmentType<*>, AttachmentType<WindGrant>> =
        ATTACHMENTS.register("wind_grant", Supplier { AttachmentType.builder(Supplier { WindGrant() }).build() })

    fun register(modBus: IEventBus) {
        ATTACHMENTS.register(modBus)
        val generator = ResourceLocation.fromNamespaceAndPath(MOD, "wind_generator")
        CompatLines.register(MOD, "wind_generator", AdapterNeeds(blocks = listOf(generator), mixins = listOf(MixinState.WIND_GENERATOR)))
        // Common setup comes before the role files load, which read whether the hook applied (CD-OPT-09).
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(WIND_GENERATOR) }) }
        HandlerRegistry.register(WindGeneratorHandler)
    }
}
