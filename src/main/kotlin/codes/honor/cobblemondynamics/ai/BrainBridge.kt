package codes.honor.cobblemondynamics.ai

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

/** The engine's only way into a Pokémon's Brain: the work order memory and the `cobblemon_dynamics:work` activity. */
object BrainBridge {
    /** In the entity's NeoForge data while this mod holds the entity in the sleep pose, so that only a pose of ours is restored. */
    private const val POSED = "$MOD_ID:posed"

    /** A sleeper further than this from its sleep point is put back on it. */
    private const val LIE_TOLERANCE_SQR = 1e-4

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

    /**
     * CD-QWK-15: a party Pokémon whose Quick Work order ended follows its owner from the next tick. The hold of a work order renews
     * Cobblemon's path cooldown on every tick (`WorkMoveTask`), and Cobblemon's `move_to_owner` waits for it to expire.
     */
    fun endHold(entity: PokemonEntity) {
        entity.brain.eraseMemory(CobblemonMemories.PATH_COOLDOWN)
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
        endNightSleep(entity)
    }

    /**
     * Cobblemon's night sleep gives a Pokémon the battle SLEEP status and the sleeping memory, and only its own wake-up task clears
     * them. That task runs in Cobblemon's sleeping activity, which the work activity replaces, so a Pokémon that Cobblemon put to sleep
     * would work with the status, which persists into battles, and with the sleep pose. This wakes it as Cobblemon's wake-up task does.
     */
    private fun endNightSleep(entity: PokemonEntity) {
        val brain = entity.brain
        if (!brain.isMemoryValue(CobblemonMemories.POKEMON_SLEEPING, true)) return
        val pokemon = entity.pokemon
        if (!entity.isBattling && pokemon.status?.status == Statuses.SLEEP) pokemon.status = null
        brain.eraseMemory(CobblemonMemories.POKEMON_SLEEPING)
    }

    /**
     * The sleep pose of a worker on a bed or at rest. Cobblemon recalculates the pose every tick unless recalculation is off, and it
     * chooses the sleep pose only for the battle SLEEP status or its own sleeping memory, so the bridge turns recalculation off and
     * sets the pose itself. It never sets the battle status, which persists (CD-AI-07).
     */
    fun pose(entity: PokemonEntity) {
        entity.persistentData.putBoolean(POSED, true)
        entity.enablePoseTypeRecalculation = false
        if (entity.entityData.get(PokemonEntity.POSE_TYPE) != PoseType.SLEEP) entity.entityData.set(PokemonEntity.POSE_TYPE, PoseType.SLEEP)
    }

    /**
     * Ends a pose of ours. Cobblemon saves the recalculation flag with the entity, so a flag left off would freeze the pose for good
     * (CD-AI-08). An entity that loads with our mark, because it was saved while it slept, gets its pose back here too. The flag is
     * read first, because NeoForge creates an entity's data tag on its first read, and most entities never had a pose of ours.
     */
    fun wake(entity: PokemonEntity) {
        if (entity.enablePoseTypeRecalculation) return
        val data = entity.persistentData
        if (!data.getBoolean(POSED)) return
        data.remove(POSED)
        entity.enablePoseTypeRecalculation = true
    }

    /** Keeps a sleeper on its sleep point, at rest (CD-BED-10). */
    fun lieAt(entity: PokemonEntity, point: Vec3) {
        if (entity.position().distanceToSqr(point) > LIE_TOLERANCE_SQR) entity.teleportTo(point.x, point.y, point.z)
        entity.deltaMovement = Vec3.ZERO
    }

    /** Turns the head, the body and the yaw to [target] (CD-AI-05). */
    fun face(entity: LivingEntity, target: Vec3) {
        val yaw = LookMath.yawTowards(entity.position(), target)
        entity.yRot = yaw
        entity.yBodyRot = yaw
        entity.yHeadRot = yaw
    }
}
