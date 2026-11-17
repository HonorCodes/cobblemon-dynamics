package codes.honor.cobblemondynamics.compat.enchantinginfuser

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

/**
 * Both infusers of Enchanting Infuser count their power in a private method of their menu, so the Muse adds its bookshelves through the
 * hook in `mixin/compat/enchantinginfuser` (CD-FIX-06). The mod calls [register] only while Enchanting Infuser is loaded.
 */
object EnchantingInfuserCompat {
    private const val MENU = "fuzs.enchantinginfuser.world.inventory.InfuserMenu"
    private val INFUSERS: List<ResourceLocation> = listOf("enchanting_infuser", "advanced_enchanting_infuser").map {
        ResourceLocation.fromNamespaceAndPath("enchantinginfuser", it)
    }

    fun register(modBus: IEventBus) {
        CompatLines.register(
            "enchantinginfuser",
            "muse_infusers",
            AdapterNeeds(blocks = INFUSERS, mixins = listOf(MixinState.INFUSER_MENU)),
        )
        // Common setup comes before the role files load, which read whether the hook applied.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(MENU) }) }
    }
}
