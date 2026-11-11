package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.leash.WorkLeashItem
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister

object DynamicsItems {
    val REGISTER: DeferredRegister.Items = DeferredRegister.createItems(MOD_ID)

    val WORK_LEASH: DeferredItem<WorkLeashItem> = REGISTER.registerItem("work_leash", { WorkLeashItem(it) }, Item.Properties().stacksTo(1))

    fun register(modBus: IEventBus) {
        REGISTER.register(modBus)
    }
}
