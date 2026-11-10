package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object DynamicsEntities {
    val REGISTER: DeferredRegister<EntityType<*>> = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID)

    /** `noSave` keeps every focus entity out of the chunk data (CD-FX-06). */
    val WORK_FOCUS: DeferredHolder<EntityType<*>, EntityType<WorkFocusEntity>> = REGISTER.register("work_focus", Supplier { workFocus() })

    private fun workFocus(): EntityType<WorkFocusEntity> = EntityType.Builder.of(::WorkFocusEntity, MobCategory.MISC)
        .sized(0.1f, 0.1f)
        .noSave()
        .noSummon()
        .fireImmune()
        .clientTrackingRange(8)
        .build("work_focus")
}
