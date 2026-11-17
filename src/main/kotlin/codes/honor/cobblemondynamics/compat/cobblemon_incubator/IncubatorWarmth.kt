package codes.honor.cobblemondynamics.compat.cobblemon_incubator

import codes.honor.cobblemondynamics.engine.guarded
import codes.honor.cobblemondynamics.handler.WorkGrants
import com.nbp.cobblemon_incubator.config.IncubatorConfig
import net.minecraft.world.level.block.entity.BlockEntity

/** The Egg Incubators that Working Stokers warm on the current tick, which the incubator's mixin reads in its own speed. */
object IncubatorWarmth {
    private val grants = WorkGrants<Unit>()

    fun grant(incubator: BlockEntity, now: Long) = grants.grant(incubator, Unit, now)

    fun withdraw(incubator: BlockEntity) = grants.withdraw(incubator)

    fun reset() = grants.reset()

    /**
     * CD-FUEL-11: the speed of [incubator] this tick, from its own speed [own]. An incubator that no worker warms, and every incubator on a
     * client, keeps [own] with no read of the incubator's config. This runs in the incubator's own tick, outside the engine's guard, so
     * the config read takes that guard here: a member that a later version renamed turns the incubator's target off and keeps [own]
     * (CD-OPT-10, R88).
     */
    @JvmStatic
    fun speedFor(incubator: BlockEntity, own: Int): Int {
        if (grants.current(incubator) == null) return own
        return guarded(EggIncubatorHandler, own) {
            IncubatorRule.speed(
                own = own,
                upgraded = IncubatorConfig.upgradedSpeed,
                upgradesEnabled = IncubatorConfig.speedUpgradeEnabled,
                worked = true,
            )
        }
    }
}
