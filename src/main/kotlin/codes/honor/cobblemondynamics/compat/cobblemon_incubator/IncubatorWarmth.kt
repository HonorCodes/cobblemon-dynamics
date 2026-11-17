package codes.honor.cobblemondynamics.compat.cobblemon_incubator

import codes.honor.cobblemondynamics.handler.WorkGrants
import com.nbp.cobblemon_incubator.config.IncubatorConfig
import net.minecraft.world.level.block.entity.BlockEntity

/** The Egg Incubators that Working Stokers warm on the current tick, which the incubator's mixin reads in its own speed. */
object IncubatorWarmth {
    private val grants = WorkGrants<Unit>()

    fun grant(incubator: BlockEntity, now: Long) = grants.grant(incubator, Unit, now)

    fun withdraw(incubator: BlockEntity) = grants.withdraw(incubator)

    fun reset() = grants.reset()

    /** CD-FUEL-11: the speed of [incubator] this tick, from its own speed [own]. Always [own] on a client. */
    @JvmStatic
    fun speedFor(incubator: BlockEntity, own: Int): Int =
        IncubatorRule.speed(own = own, upgraded = IncubatorConfig.upgradedSpeed, worked = grants.current(incubator) != null)
}
