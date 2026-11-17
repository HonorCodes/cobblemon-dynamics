package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.xp.CollectorMenu
import net.minecraft.core.registries.Registries
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** The mod's menu types: the Experience Collector's (CD-XPC-04). Its button uses vanilla's container button packet (CD-XPC-05). */
object DynamicsMenus {
    val REGISTER: DeferredRegister<MenuType<*>> = DeferredRegister.create(Registries.MENU, MOD_ID)

    val EXPERIENCE_COLLECTOR: DeferredHolder<MenuType<*>, MenuType<CollectorMenu>> =
        REGISTER.register("experience_collector", Supplier { collectorType() })

    private fun collectorType(): MenuType<CollectorMenu> =
        MenuType({ containerId, inventory -> CollectorMenu(containerId, inventory) }, FeatureFlags.DEFAULT_FLAGS)
}
