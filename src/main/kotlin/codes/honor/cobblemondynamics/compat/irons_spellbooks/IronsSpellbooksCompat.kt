package codes.honor.cobblemondynamics.compat.irons_spellbooks

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

/**
 * The Brewer on the Alchemist Cauldron of Iron's Spells 'n Spellbooks, through the hook in `mixin/compat/irons_spellbooks` (CD-MCH-07).
 * The mod calls [register] only while Iron's Spells 'n Spellbooks is loaded.
 */
object IronsSpellbooksCompat {
    private const val MOD = "irons_spellbooks"
    private const val CAULDRON = "io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile"

    fun register(modBus: IEventBus) {
        val block = ResourceLocation.fromNamespaceAndPath(MOD, "alchemist_cauldron")
        CompatLines.register(
            MOD,
            "alchemist_cauldron",
            AdapterNeeds(blocks = listOf(block), mixins = listOf(MixinState.ALCHEMIST_CAULDRON)),
        )
        HandlerRegistry.register(AlchemistCauldronHandler)
        // Common setup comes before the role files load, which read whether the hook applied.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(CAULDRON) }) }
    }
}
