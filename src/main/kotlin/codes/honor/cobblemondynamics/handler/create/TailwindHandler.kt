package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.simibubi.create.content.contraptions.bearing.BearingContraption
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** Tailwind: a Flying-type adds virtual sails to a Create windmill while it Works, recomputed on every tick (CD-TLW-03). */
object TailwindHandler : TargetHandler {
    override val id: ResourceLocation = id("tailwind")

    /** Leash only (CD-TLW-02, CD-ASG-05). */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is WindmillBearingBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        level.getBlockEntity(canonical) is WindmillBearingBlockEntity

    /** The worker's body stays out of the turning sails (design decision 5 of the Phase 2 plan). */
    override fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean {
        val facing = level.getBlockState(canonical).getOptionalValue(DirectionalKineticBlock.FACING).orElse(null) ?: return false
        return TailwindMath.clearOfSails(canonical, facing, spot, bodyHeight)
    }

    /** CD-TLW-08: the windmill is assembled and turns. A running windmill always gives at least 1 RPM. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val bearing = bearingAt(ctx) ?: return false
        return bearing.isRunning && bearing.movedContraption != null
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** CD-TLW-03, CD-TLW-04, CD-TLW-06: more real sails or a new level count on the next tick. */
    override fun applyWork(ctx: WorkContext) {
        val bearing = bearingAt(ctx) ?: return
        val contraption = bearing.movedContraption?.contraption as? BearingContraption ?: return
        val params = TailwindParams.of(ctx.role.params)
        val bonus = TailwindMath.bonus(contraption.sailBlocks, ctx.pokemon.level, params.perLevel, params.maxTotalSails)
        TailwindBonus.of(bearing)?.grant(bonus, ctx.now)
    }

    /** CD-TLW-09. In a chunk that is gone, the grant lapses by itself. */
    override fun onStop(ctx: WorkContext) {
        if (ctx.level.isLoaded(ctx.target)) TailwindBonus.of(bearingAt(ctx))?.withdraw()
    }

    private fun bearingAt(ctx: WorkContext): WindmillBearingBlockEntity? =
        ctx.level.getBlockEntity(ctx.target) as? WindmillBearingBlockEntity
}
