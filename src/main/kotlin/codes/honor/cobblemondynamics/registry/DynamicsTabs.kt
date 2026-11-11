package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedTier
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** CD-UI-16: the mod's own creative tab. It lists every item of the mod, and no vanilla tab lists them. */
object DynamicsTabs {
    val REGISTER: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val MAIN: DeferredHolder<CreativeModeTab, CreativeModeTab> = REGISTER.register(
        "main",
        Supplier {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.cobblemon_dynamics.main"))
                .icon(Supplier { ItemStack(DynamicsItems.WORK_LEASH.get()) })
                .displayItems(CreativeModeTab.DisplayItemsGenerator { _, output -> items().forEach { output.accept(it) } })
                .build()
        },
    )

    /** Every item of the mod, in the tab's order: the Work Leash, the beds by tier and the Charge Cell. */
    fun items(): List<Item> = listOf(DynamicsItems.WORK_LEASH.get()) +
        BedTier.entries.map { DynamicsBlocks.BED_ITEMS.getValue(it).get() } +
        DynamicsBlocks.CHARGE_CELL_ITEM.get()
}
