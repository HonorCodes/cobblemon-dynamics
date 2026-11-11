package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity

/** The fan worker mark: the fan that a worker holds a work claim on. It syncs to clients, which push entities too (CD-FAN-12). */
object FanWorkers {
    /** A worker with a work claim on a fan: no fan pushes, burns, harms or processes it. */
    @JvmStatic
    fun isExempt(entity: Entity): Boolean = entity is PokemonEntity && entity.hasData(DynamicsAttachments.FAN_WORKER)

    fun mark(worker: PokemonEntity, fan: BlockPos) {
        worker.setData(DynamicsAttachments.FAN_WORKER, fan)
    }

    fun clear(worker: PokemonEntity) {
        if (worker.hasData(DynamicsAttachments.FAN_WORKER)) worker.removeData(DynamicsAttachments.FAN_WORKER)
    }
}
