package codes.honor.cobblemondynamics.compat.ae2

import appeng.api.implementations.blockentities.ICrankable
import appeng.block.misc.CrankBlock
import appeng.blockentity.misc.CrankBlockEntity
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Cranker's target in AE2: a Crank on a machine that takes turns, such as the Charger, the Inscriber and ExtendedAE's machines
 * (CD-KIN-06). The worker gives the machine one turn of AE2's `ICrankable` API every 18 ticks while the machine can take one, as a
 * player at the crank does.
 */
object Ae2CrankHandler : TargetHandler {
    override val id: ResourceLocation = id("ae2_crank")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is CrankBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is CrankBlockEntity

    /** The crank and the machine that it turns form the target, so the machine never hides the crank from a work spot beside it. */
    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || pos == attachedTo(level, canonical)

    /** CD-KIN-06: while the machine takes a turn, which AE2's own crank asks with `canTurn()`. */
    override fun hasDemand(ctx: WorkContext): Boolean = crankableAt(ctx)?.canTurn() ?: false

    /** The memo holds the tick that this stretch of work started, which the turns count from. */
    override fun onStart(ctx: WorkContext) {
        ctx.memo.value = ctx.now
    }

    override fun applyWork(ctx: WorkContext) {
        val since = ctx.memo.value as? Long ?: ctx.now.also { ctx.memo.value = it }
        val crankable = crankableAt(ctx) ?: return
        val interval = Ae2CrankRule.intervalOf(ctx.role.params)
        if (Ae2CrankRule.shouldTurn(crankable.canTurn(), since, ctx.now, interval)) crankable.applyTurn()
    }

    private fun crankableAt(ctx: WorkContext): ICrankable? {
        val state = ctx.level.getBlockState(ctx.target)
        return (state.block as? CrankBlock)?.getCrankable(state, ctx.level, ctx.target)
    }

    /** The machine that a crank turns: the block behind its facing, as AE2's private `getAttachedToPos` finds it. */
    private fun attachedTo(level: ServerLevel, crank: BlockPos): BlockPos? {
        val state = level.getBlockState(crank)
        val block = state.block as? CrankBlock ?: return null
        return crank.relative(block.orientationStrategy.getFacing(state).opposite)
    }
}
