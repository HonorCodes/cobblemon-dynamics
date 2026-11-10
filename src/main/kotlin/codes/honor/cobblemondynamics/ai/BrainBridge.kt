package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

/** The engine's only way into a Pokémon's Brain: the work order memory and the `cobblemon_dynamics:work` activity. */
object BrainBridge {
    /** Before Cobblemon loads the behaviour files. Without these types the work behaviour cannot load, and workers stay Idle. */
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
        // The Brain ticks before the engine, so its core task would switch one tick late after Cobblemon rebuilds the Brain.
        if (brain.hasMemoryValue(memory) && !brain.isActive(DynamicsActivities.WORK.get())) enterWork(entity)
    }

    fun clear(entity: PokemonEntity) {
        entity.brain.eraseMemory(DynamicsMemories.WORK_ORDER.get())
    }

    fun isWorking(entity: PokemonEntity): Boolean = entity.brain.isActive(DynamicsActivities.WORK.get())

    fun holdsOrder(entity: PokemonEntity): Boolean = entity.brain.hasMemoryValue(DynamicsMemories.WORK_ORDER.get())

    /** False for a species whose `baseAI` replaces Cobblemon's auto behaviours, and with them ours. */
    fun canHoldOrder(entity: PokemonEntity): Boolean = entity.brain.checkMemory(DynamicsMemories.WORK_ORDER.get(), MemoryStatus.REGISTERED)

    /**
     * A Brain keeps running the tasks of the activity that it leaves until each one stops on its own, so every running task stops
     * here, and the work activity runs in place of Cobblemon's idle tasks (CD-AI-02, CD-AI-09). Core tasks and our own start again
     * when their memories say so.
     */
    fun enterWork(entity: PokemonEntity) {
        val brain = entity.brain
        val work = DynamicsActivities.WORK.get()
        brain.setActiveActivityIfPossible(work)
        if (!brain.isActive(work)) return
        val level = entity.level() as? ServerLevel ?: return
        brain.stopAll(level, entity)
    }

    /** Turns the head, the body and the yaw to [target] (CD-AI-05). */
    fun face(entity: LivingEntity, target: Vec3) {
        val yaw = LookMath.yawTowards(entity.position(), target)
        entity.yRot = yaw
        entity.yBodyRot = yaw
        entity.yHeadRot = yaw
    }
}
