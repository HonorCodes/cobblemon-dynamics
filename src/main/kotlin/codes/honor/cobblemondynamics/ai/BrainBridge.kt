package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.engine.ZoneTether
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.phys.Vec3

/** The engine's only way into a Pokémon's Brain: the work order memory and the `cobblemon_dynamics:work` activity. */
object BrainBridge {
    private const val POSED = "$MOD_ID:posed"

    private const val LIE_TOLERANCE_SQR = 1e-4

    fun registerTaskTypes() {
        try {
            TaskConfig.types[id("switch_to_work")] = SwitchToWorkTaskConfig::class.java
            TaskConfig.types[id("work")] = WorkTaskConfig::class.java
            TaskConfig.types[id("switch_from_work")] = SwitchFromWorkTaskConfig::class.java
        } catch (e: LinkageError) {
            CobblemonDynamics.LOGGER.error("Cobblemon's task table changed, so no Pokémon can work: {}", e.toString())
        }
    }

    /** Every tick while the worker holds a claim (CD-AI-04). */
    fun write(entity: PokemonEntity, order: WorkOrder) {
        val brain = entity.brain
        val memory = DynamicsMemories.WORK_ORDER.get()
        brain.setMemory(memory, order)
        if (brain.hasMemoryValue(memory) && !brain.isActive(DynamicsActivities.WORK.get())) enterWork(entity)
    }

    fun clear(entity: PokemonEntity) {
        entity.brain.eraseMemory(DynamicsMemories.WORK_ORDER.get())
    }

    fun endHold(entity: PokemonEntity) {
        entity.brain.eraseMemory(CobblemonMemories.PATH_COOLDOWN)
    }

    fun pauseWander(entity: PokemonEntity, ticks: Long) {
        if (entity.brain.checkMemory(CobblemonMemories.PATH_COOLDOWN, MemoryStatus.REGISTERED)) {
            entity.brain.setMemoryWithExpiry(CobblemonMemories.PATH_COOLDOWN, true, ticks)
        }
    }

    fun isWorking(entity: PokemonEntity): Boolean = entity.brain.isActive(DynamicsActivities.WORK.get())

    fun holdsOrder(entity: PokemonEntity): Boolean = entity.brain.hasMemoryValue(DynamicsMemories.WORK_ORDER.get())

    fun canHoldOrder(entity: PokemonEntity): Boolean = entity.brain.checkMemory(DynamicsMemories.WORK_ORDER.get(), MemoryStatus.REGISTERED)

    internal enum class ResidualMemory {
        WALK_TARGET,
        ATTACK_TARGET,
        ANGRY_AT,
        CANT_REACH_WALK_TARGET_SINCE,
    }

    fun enterWork(entity: PokemonEntity) {
        val brain = entity.brain
        val work = DynamicsActivities.WORK.get()
        brain.setActiveActivityIfPossible(work)
        if (!brain.isActive(work)) return
        val level = entity.level() as? ServerLevel ?: return
        brain.stopAll(level, entity)
        clearResidualTargets(
            erase = { memory ->
                val type = when (memory) {
                    ResidualMemory.WALK_TARGET -> MemoryModuleType.WALK_TARGET
                    ResidualMemory.ATTACK_TARGET -> MemoryModuleType.ATTACK_TARGET
                    ResidualMemory.ANGRY_AT -> MemoryModuleType.ANGRY_AT
                    ResidualMemory.CANT_REACH_WALK_TARGET_SINCE -> MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
                }
                eraseIfRegistered(brain, type)
            },
            stopNavigation = { entity.navigation.stop() },
        )
        endNightSleep(entity)
    }

    internal fun clearResidualTargets(erase: (ResidualMemory) -> Unit, stopNavigation: () -> Unit) {
        for (memory in ResidualMemory.entries) erase(memory)
        stopNavigation()
    }

    fun anchorIdle(entity: PokemonEntity, area: WorkArea, suppressSleep: Boolean = false) {
        val brain = entity.brain
        if (suppressSleep) {
            endNightSleep(entity)
            eraseIfRegistered(brain, CobblemonMemories.POKEMON_DROWSY)
            if (brain.isActive(CobblemonActivities.POKEMON_SLEEPING_ACTIVITY) ||
                brain.isActive(CobblemonActivities.POKEMON_SLEEP_ON_TRAINER_BED)
            ) {
                brain.setActiveActivityIfPossible(Activity.IDLE)
            }
        }
        if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent { walkTarget ->
                val entityTarget = walkTarget.target is EntityTracker
                val targetPos = walkTarget.target.currentBlockPosition()
                if (ZoneTether.shouldSuppressIdleWalk(workMode = true, entityTarget = entityTarget, targetPos = targetPos, area = area)) {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                    if (entityTarget) eraseIfRegistered(brain, MemoryModuleType.LOOK_TARGET)
                    entity.navigation.stop()
                }
            }
        }
        val navTarget = entity.navigation.targetPos
        if (navTarget != null && !area.contains(navTarget)) entity.navigation.stop()
    }

    private fun eraseIfRegistered(brain: Brain<*>, vararg types: MemoryModuleType<*>) {
        for (type in types) {
            if (brain.checkMemory(type, MemoryStatus.REGISTERED)) brain.eraseMemory(type)
        }
    }

    private fun endNightSleep(entity: PokemonEntity) {
        val brain = entity.brain
        if (!brain.checkMemory(CobblemonMemories.POKEMON_SLEEPING, MemoryStatus.REGISTERED)) return
        if (!brain.isMemoryValue(CobblemonMemories.POKEMON_SLEEPING, true)) return
        val pokemon = entity.pokemon
        if (!entity.isBattling && pokemon.status?.status == Statuses.SLEEP) pokemon.status = null
        brain.eraseMemory(CobblemonMemories.POKEMON_SLEEPING)
    }

    fun pose(entity: PokemonEntity) {
        entity.persistentData.putBoolean(POSED, true)
        entity.enablePoseTypeRecalculation = false
        if (entity.entityData.get(PokemonEntity.POSE_TYPE) != PoseType.SLEEP) entity.entityData.set(PokemonEntity.POSE_TYPE, PoseType.SLEEP)
    }

    fun wake(entity: PokemonEntity) {
        if (entity.enablePoseTypeRecalculation) return
        val data = entity.persistentData
        if (!data.getBoolean(POSED)) return
        data.remove(POSED)
        entity.enablePoseTypeRecalculation = true
    }

    fun lieAt(entity: PokemonEntity, point: Vec3) {
        if (entity.position().distanceToSqr(point) > LIE_TOLERANCE_SQR) entity.teleportTo(point.x, point.y, point.z)
        entity.deltaMovement = Vec3.ZERO
    }

    fun face(entity: LivingEntity, target: Vec3) {
        val yaw = LookMath.yawTowards(entity.position(), target)
        entity.yRot = yaw
        entity.yBodyRot = yaw
        entity.yHeadRot = yaw
    }
}
