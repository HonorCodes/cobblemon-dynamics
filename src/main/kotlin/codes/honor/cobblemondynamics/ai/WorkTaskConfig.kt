package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.engine.Arrival
import codes.honor.cobblemondynamics.engine.EngineTiming
import codes.honor.cobblemondynamics.engine.Reach
import codes.honor.cobblemondynamics.engine.SurfaceGrid
import codes.honor.cobblemondynamics.engine.WorkerBody
import codes.honor.cobblemondynamics.engine.ZoneTether
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3
import kotlin.math.max

/**
 * `cobblemon_dynamics:work`: the move task and the look task of the work activity.
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

/** Runs while the Brain holds a work order, and follows the order on every tick. */
abstract class WorkOrderTask :
    Behavior<LivingEntity>(mapOf<MemoryModuleType<*>, MemoryStatus>(DynamicsMemories.WORK_ORDER.get() to MemoryStatus.VALUE_PRESENT)) {
    override fun canStillUse(level: ServerLevel, entity: LivingEntity, gameTime: Long): Boolean =
        EngineTiming.time { entity.brain.hasMemoryValue(DynamicsMemories.WORK_ORDER.get()) }

    override fun timedOut(gameTime: Long): Boolean = false

    override fun start(level: ServerLevel, entity: LivingEntity, gameTime: Long) = tick(level, entity, gameTime)

    override fun tick(level: ServerLevel, entity: LivingEntity, gameTime: Long) {
        EngineTiming.time { entity.brain.getMemory(DynamicsMemories.WORK_ORDER.get()).ifPresent { follow(entity, it) } }
    }

    protected abstract fun follow(entity: LivingEntity, order: WorkOrder)
}

/**
 * Walks to the work spot, bed, or return point through Cobblemon's core `follow_walk_target`, stepping intermediate waypoints when
 * the goal exceeds `FOLLOW_RANGE` (CD-NAV-07), then holds the spot (CD-AI-02).
 */
class WorkMoveTask : WorkOrderTask() {
    private var walkingTo: BlockPos? = null
    private var finalGoal: BlockPos? = null
    private var currentStep: ZoneTether.Waypoint? = null
    private var stepAttempt: Int = 0
    private var stepSetTick: Long = -1L
    private var stepAnchor: Vec3 = Vec3.ZERO

    override fun follow(entity: LivingEntity, order: WorkOrder) {
        val brain = entity.brain
        val pokemon = entity as? PokemonEntity
        if (!order.sleep) pokemon?.let(BrainBridge::wake)
        if (!order.hold) {
            if (pokemon != null && pokemon.isFlying() && !pokemon.onGround() && pokemon.horizontalCollision) {
                stepHoveringOverThinSurface(pokemon, order.maxY)
            }
            val step = resolveStep(entity, order.spot, order.spotCell, entity.level().gameTime)
            if (step == null) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                (entity as? Mob)?.navigation?.stop()
                walkingTo = null
                return
            }
            brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(step.cell, WALK_SPEED, 0))
            walkingTo = step.cell
            return
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        brain.setMemoryWithExpiry(CobblemonMemories.PATH_COOLDOWN, true, HOLD_COOLDOWN_TICKS)
        (entity as? Mob)?.navigation?.stop()
        resetStepState()
        if (order.sleep && pokemon != null) {
            BrainBridge.lieAt(pokemon, order.spot)
            BrainBridge.pose(pokemon)
        }
    }

    private fun resolveStep(entity: LivingEntity, goal: Vec3, goalCell: BlockPos, gameTime: Long): ZoneTether.Waypoint? {
        val followRange = if (entity.attributes.hasAttribute(Attributes.FOLLOW_RANGE)) {
            entity.getAttributeValue(Attributes.FOLLOW_RANGE)
        } else {
            ZoneTether.DEFAULT_FOLLOW_RANGE
        }
        if (finalGoal != goalCell) {
            finalGoal = goalCell
            currentStep = null
            stepAttempt = 0
            stepSetTick = -1L
        }
        val here = entity.position()
        if (here.distanceTo(goal) <= followRange) {
            currentStep = null
            stepAttempt = 0
            stepSetTick = -1L
            return ZoneTether.Waypoint(goal, goalCell)
        }
        val latched = currentStep
        if (latched != null) {
            if (here.distanceTo(latched.pos) <= WAYPOINT_ADVANCE_DIST) {
                stepAttempt = 0
            } else {
                if (here.distanceToSqr(stepAnchor) >= PROGRESS_RADIUS_SQR) {
                    stepAnchor = here
                    stepSetTick = gameTime
                }
                val stuck = !entity.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET) ||
                    (entity as? Mob)?.navigation?.isStuck == true ||
                    (stepSetTick >= 0L && gameTime - stepSetTick >= WAYPOINT_STALL_TICKS)
                if (!stuck && here.distanceTo(latched.pos) <= followRange) return latched
                if (stuck) stepAttempt++
            }
        }
        val level = entity.level()
        val width = entity.bbWidth.toDouble()
        val height = entity.bbHeight.toDouble()
        val headroom = max(SurfaceGrid.DEFAULT_HEADROOM, height)
        val reach = (entity as? PokemonEntity)?.let(Reach::of)
        val next = ZoneTether.selectWaypoint(
            from = here,
            goal = goal,
            goalCell = goalCell,
            followRange = followRange,
            attemptOffset = stepAttempt,
            standable = { x, y, z -> SurfaceGrid.supportTopY(level, x, y, z, headroom) },
            fitsBody = { cell, topY -> WorkerBody.boxes(cell, width, height, topY).all { level.noBlockCollision(entity, it) } },
            reachable = { cell -> reach?.invoke(cell) ?: true },
        )
        if (next == null) {
            currentStep = null
            stepSetTick = -1L
            stepAttempt++
            return null
        }
        currentStep = next
        stepSetTick = gameTime
        stepAnchor = here
        return next
    }

    private fun stepHoveringOverThinSurface(entity: PokemonEntity, maxY: Double) {
        val level = entity.level()
        val headroom = max(SurfaceGrid.DEFAULT_HEADROOM, entity.bbHeight.toDouble())
        val box = entity.boundingBox.inflate(HOVER_STEP_PROBE, 0.0, HOVER_STEP_PROBE)
        val cellY = Mth.floor(entity.y)
        var maxTopY = Double.NEGATIVE_INFINITY
        for (x in Mth.floor(box.minX)..Mth.floor(box.maxX)) {
            for (z in Mth.floor(box.minZ)..Mth.floor(box.maxZ)) {
                val topY = SurfaceGrid.supportTopY(level, x, cellY, z, headroom) ?: continue
                if (topY > entity.y && topY - entity.y <= MAX_HOVER_STEP && topY > maxTopY) maxTopY = topY
            }
        }
        if (!maxTopY.isFinite()) return
        val liftY = Arrival.snapY(maxTopY) + HOVER_CLEARANCE
        if (liftY >= maxY) return
        val liftedBox = entity.boundingBox.move(0.0, liftY - entity.y, 0.0)
        if (level.noBlockCollision(entity, liftedBox)) entity.setPos(entity.x, liftY, entity.z)
    }

    private fun resetStepState() {
        walkingTo = null
        finalGoal = null
        currentStep = null
        stepAttempt = 0
        stepSetTick = -1L
        stepAnchor = Vec3.ZERO
    }

    override fun stop(level: ServerLevel, entity: LivingEntity, gameTime: Long) = EngineTiming.time {
        val spot = walkingTo
        resetStepState()
        if (spot == null) return@time
        val brain = entity.brain
        if (brain.getMemory(MemoryModuleType.WALK_TARGET).map { it.target.currentBlockPosition() == spot }.orElse(false)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        }
    }

    private companion object {
        const val WALK_SPEED = 0.4f
        const val HOLD_COOLDOWN_TICKS = 40L
        const val WAYPOINT_ADVANCE_DIST = 4.0
        const val WAYPOINT_STALL_TICKS = 60L
        const val PROGRESS_RADIUS_SQR = 0.25
        const val HOVER_STEP_PROBE = 0.1
        const val MAX_HOVER_STEP = 0.55
        const val HOVER_CLEARANCE = 0.06
    }
}

/** Looks at the target center, and turns the body to it while the worker holds its spot (CD-AI-05, CD-AI-09). */
class WorkLookTask : WorkOrderTask() {
    private var lookingAt: Vec3? = null

    override fun follow(entity: LivingEntity, order: WorkOrder) {
        val target = order.lookAt ?: return
        entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(target))
        lookingAt = target
        if (order.hold) BrainBridge.face(entity, target)
    }

    override fun stop(level: ServerLevel, entity: LivingEntity, gameTime: Long) = EngineTiming.time {
        val target = lookingAt ?: return@time
        lookingAt = null
        val brain = entity.brain
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET).map { it.currentPosition() == target }.orElse(false)) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET)
        }
    }
}
