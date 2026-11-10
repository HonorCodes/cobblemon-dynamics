package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.schedule.Activity
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object DynamicsActivities {
    val REGISTER: DeferredRegister<Activity> = DeferredRegister.create(Registries.ACTIVITY, MOD_ID)

    /**
     * `cobblemon_dynamics:work`. Two activities with the same name are equal, and vanilla's villagers already have `work`, so
     * the name is the full id.
     */
    val WORK: DeferredHolder<Activity, Activity> = REGISTER.register("work", Supplier { Activity("$MOD_ID:work") })
}
