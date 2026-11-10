package codes.honor.cobblemondynamics.ai

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

/**
 * `cobblemon_dynamics:work`: the move task and the look task of the work activity. Cobblemon's own look task runs only in the
 * idle activity, which the work activity replaces (CD-AI-09).
 */
class WorkTaskConfig : TaskConfig {
    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
        emptyList<MoLangConfigVariable>()

    override fun createTasks(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext,
    ): List<BehaviorControl<in LivingEntity>> {
        behaviourConfigurationContext.addMemories(
            DynamicsMemories.WORK_ORDER.get(),
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            CobblemonMemories.PATH_COOLDOWN,
        )
        return listOf(WorkMoveTask(), WorkLookTask())
    }
}

/** Runs while the Brain holds a work order, and follows the order on every tick. The order ends the task, not a duration. */
abstract class WorkOrderTask :
    Behavior<LivingEntity>(mapOf<MemoryModuleType<*>, MemoryStatus>(DynamicsMemories.WORK_ORDER.get() to MemoryStatus.VALUE_PRESENT)) {
    override fun canStillUse(level: ServerLevel, entity: LivingEntity, gameTime: Long): Boolean =
        entity.brain.hasMemoryValue(DynamicsMemories.WORK_ORDER.get())

    override fun timedOut(gameTime: Long): Boolean = false

    override fun start(level: ServerLevel, entity: LivingEntity, gameTime: Long) = tick(level, entity, gameTime)

    override fun tick(level: ServerLevel, entity: LivingEntity, gameTime: Long) {
        entity.brain.getMemory(DynamicsMemories.WORK_ORDER.get()).ifPresent { follow(entity, it) }
    }

    protected abstract fun follow(entity: LivingEntity, order: WorkOrder)
}

/** Walks to the work spot through Cobblemon's core `follow_walk_target`, then holds the spot (CD-AI-02). */
class WorkMoveTask : WorkOrderTask() {
    private var walkingTo: BlockPos? = null

    override fun follow(entity: LivingEntity, order: WorkOrder) {
        val brain = entity.brain
        if (!order.hold) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(order.spot, WALK_SPEED, 0))
            walkingTo = BlockPos.containing(order.spot)
            return
        }
        // Cobblemon's wander task needs both memories absent, so it stays paused while the worker holds its spot.
        brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        brain.setMemoryWithExpiry(CobblemonMemories.PATH_COOLDOWN, true, HOLD_COOLDOWN_TICKS)
        (entity as? Mob)?.navigation?.stop()
        walkingTo = null
    }

    override fun stop(level: ServerLevel, entity: LivingEntity, gameTime: Long) {
        val spot = walkingTo ?: return
        walkingTo = null
        val brain = entity.brain
        if (brain.getMemory(MemoryModuleType.WALK_TARGET).map { it.target.currentBlockPosition() == spot }.orElse(false)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        }
    }

    private companion object {
        const val WALK_SPEED = 0.4f
        const val HOLD_COOLDOWN_TICKS = 40L
    }
}

/** Looks at the target center, and turns the body to it while the worker holds its spot (CD-AI-05, CD-AI-09). */
class WorkLookTask : WorkOrderTask() {
    private var lookingAt: Vec3? = null

    override fun follow(entity: LivingEntity, order: WorkOrder) {
        val target = order.lookAt ?: return
        entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(target))
        lookingAt = target
        // A walking Pokémon moves along its yaw, so a forced yaw while it walks would push it off its path.
        if (order.hold) BrainBridge.face(entity, target)
    }

    override fun stop(level: ServerLevel, entity: LivingEntity, gameTime: Long) {
        val target = lookingAt ?: return
        lookingAt = null
        val brain = entity.brain
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET).map { it.currentPosition() == target }.orElse(false)) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET)
        }
    }
}
