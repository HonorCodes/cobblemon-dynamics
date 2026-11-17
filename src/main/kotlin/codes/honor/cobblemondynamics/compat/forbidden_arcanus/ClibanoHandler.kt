package codes.honor.cobblemondynamics.compat.forbidden_arcanus

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker's target in Forbidden & Arcanus: a built Clibano, keyed by its main block in the middle of the cube (CD-FUEL-10). The
 * worker grants the Clibano its burn for each tick of work, and the Clibano's mixin keeps it lit in the Clibano's own tick, so it smelts
 * with normal fire at its own pace and never takes a fuel item (CD-FUEL-13).
 */
object ClibanoHandler : TargetHandler {
    override val id: ResourceLocation = id("clibano")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is ClibanoMainBlockEntity

    /** The frame blocks around the main block have collision, so the whole cube is the target, and a work spot sees it through them. */
    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = ClibanoRule.isPart(canonical, pos)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is ClibanoMainBlockEntity

    /** CD-FUEL-10: while it smelts, by the Clibano's own check. */
    override fun hasDemand(ctx: WorkContext): Boolean = (clibanoAt(ctx) as? ClibanoAccess)?.`cobblemon_dynamics$smelts`() ?: false

    override fun applyWork(ctx: WorkContext) {
        clibanoAt(ctx)?.let { ClibanoStoking.grant(it, ctx.now) }
    }

    /** CD-FUEL-14: the Clibano's next tick finds no grant and burns down what it holds. In a chunk that is gone, the grant lapses. */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        clibanoAt(ctx)?.let(ClibanoStoking::withdraw)
    }

    private fun clibanoAt(ctx: WorkContext): ClibanoMainBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? ClibanoMainBlockEntity
}
