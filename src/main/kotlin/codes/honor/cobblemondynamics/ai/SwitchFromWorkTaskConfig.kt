package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.engine.EngineTiming
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.config.task.SingleTaskConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.memory.MemoryStatus

/** `cobblemon_dynamics:switch_from_work`, in the work activity: with the work order gone, Cobblemon's activities come back. */
class SwitchFromWorkTaskConfig : SingleTaskConfig {
    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
        emptyList<MoLangConfigVariable>()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext,
    ): BehaviorControl<in LivingEntity> {
        behaviourConfigurationContext.addMemories(DynamicsMemories.WORK_ORDER.get())
        return SwitchFromWorkTask()
    }
}

class SwitchFromWorkTask : OneShot<LivingEntity>() {
    override fun trigger(level: ServerLevel, entity: LivingEntity, gameTime: Long): Boolean = EngineTiming.time {
        val brain = entity.brain
        if (!brain.checkMemory(DynamicsMemories.WORK_ORDER.get(), MemoryStatus.VALUE_ABSENT)) return@time false
        brain.updateActivityFromSchedule(level.dayTime, level.gameTime)
        // The schedule update runs at most once in 20 ticks. The default activity is Cobblemon's idle activity, at once (CD-AI-03).
        if (brain.isActive(DynamicsActivities.WORK.get())) brain.useDefaultActivity()
        (entity as? PokemonEntity)?.let(BrainBridge::wake)
        true
    }
}
