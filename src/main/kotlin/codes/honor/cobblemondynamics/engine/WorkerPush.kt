package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.OnceLogger
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/**
 * Two workers that the engine directs do not push each other (`WorkerPushMixin`). Minecraft pushes two entities apart along the line
 * between their centres, and navigation follows cell centres, so two workers that meet in one row pushed each other straight back
 * until the trip timed out, and CD-ASG-14 then kept the worker off its target. Players, wild Pokémon, other mobs and Idle Pokémon
 * still push workers and are pushed by them.
 */
object WorkerPush {
    private val warnings = OnceLogger()

    /** True when [self] and [other] are both workers that the engine directs, on the server. The mixin then skips the push. */
    @JvmStatic
    fun ignores(self: LivingEntity, other: Entity): Boolean {
        if (self !is PokemonEntity || other !is PokemonEntity || self.level().isClientSide) return false
        val engine = WorkEngine.get() ?: return false
        return EngineTiming.time { engine.isDirected(self) && engine.isDirected(other) }
    }

    /** One warning when the mixin did not inject. Workers then push each other as before, and nothing else changes. */
    fun warnIfMissing() {
        // The class literal loads the mixin's target, so MixinState holds its final answer.
        CobblemonDynamics.LOGGER.debug("Worker push mixin target loaded: {}", LivingEntity::class.java.simpleName)
        if (MixinState.applied(MixinState.WORKER_PUSH)) return
        warnings.warn(MixinState.WORKER_PUSH) { "Workers can push each other off their paths: the worker push mixin did not apply" }
    }
}
