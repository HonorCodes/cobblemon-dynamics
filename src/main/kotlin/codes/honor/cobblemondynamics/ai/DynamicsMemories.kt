package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.Optional
import java.util.function.Supplier

object DynamicsMemories {
    val REGISTER: DeferredRegister<MemoryModuleType<*>> = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, MOD_ID)

    /** No codec, so a Brain never saves it. The engine writes it again on every tick (CD-AI-04). */
    val WORK_ORDER: DeferredHolder<MemoryModuleType<*>, MemoryModuleType<WorkOrder>> =
        REGISTER.register("work_order", Supplier { MemoryModuleType<WorkOrder>(Optional.empty()) })
}
