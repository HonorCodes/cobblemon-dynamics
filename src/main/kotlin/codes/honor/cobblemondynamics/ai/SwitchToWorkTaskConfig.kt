package codes.honor.cobblemondynamics.ai

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.config.task.SingleTaskConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.OneShot

/** `cobblemon_dynamics:switch_to_work`, in the core activity, so a work order also wins over Cobblemon's night sleep. */
class SwitchToWorkTaskConfig : SingleTaskConfig {
    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
        emptyList<MoLangConfigVariable>()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext,
    ): BehaviorControl<in LivingEntity> {
        behaviourConfigurationContext.addMemories(DynamicsMemories.WORK_ORDER.get())
        return SwitchToWorkTask()
    }
}

class SwitchToWorkTask : OneShot<LivingEntity>() {
    override fun trigger(level: ServerLevel, entity: LivingEntity, gameTime: Long): Boolean {
        val brain = entity.brain
        val work = DynamicsActivities.WORK.get()
        if (!brain.hasMemoryValue(DynamicsMemories.WORK_ORDER.get()) || brain.isActive(work)) return false
        if (entity is PokemonEntity) BrainBridge.enterWork(entity) else brain.setActiveActivityIfPossible(work)
        return true
    }
}
