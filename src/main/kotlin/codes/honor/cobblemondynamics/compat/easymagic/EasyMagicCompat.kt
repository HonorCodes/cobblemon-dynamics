package codes.honor.cobblemondynamics.compat.easymagic

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

/**
 * Easy Magic's table, `easymagic:enchanting_table`, counts its power in its own menu, so the Muse adds its bookshelves through the
 * hook in `mixin/compat/easymagic` (CD-FIX-05). The mod calls [register] only while Easy Magic is loaded.
 */
object EasyMagicCompat {
    private const val MENU = "fuzs.easymagic.world.inventory.ModEnchantmentMenu"
    private val TABLE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("easymagic", "enchanting_table")

    fun register(modBus: IEventBus) {
        CompatLines.register("easymagic", "muse_table", AdapterNeeds(blocks = listOf(TABLE), mixins = listOf(MixinState.EASY_MAGIC_MENU)))
        // Common setup comes before the role files load, which read whether the hook applied.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(MENU) }) }
    }
}
