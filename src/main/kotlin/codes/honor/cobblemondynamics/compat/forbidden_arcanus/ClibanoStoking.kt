package codes.honor.cobblemondynamics.compat.forbidden_arcanus

import codes.honor.cobblemondynamics.handler.WorkGrants
import net.minecraft.world.level.block.entity.BlockEntity

/** The Clibanos that Working Stokers burn on the current tick, which the Clibano's mixin reads at the start of the Clibano's own tick. */
object ClibanoStoking {
    private val grants = WorkGrants<Unit>()

    fun grant(clibano: BlockEntity, now: Long) = grants.grant(clibano, Unit, now)

    fun withdraw(clibano: BlockEntity) = grants.withdraw(clibano)

    fun reset() = grants.reset()

    /** Whether a worker burns [clibano] on the current tick. Always false on a client. */
    @JvmStatic
    fun isGranted(clibano: BlockEntity): Boolean = grants.current(clibano) != null
}
