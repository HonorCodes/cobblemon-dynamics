package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.block.ApricornBlock
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Harvester's target: a fully grown crop of `#cobblemon_dynamics:harvester_targets` that a harvest adapter reads (CD-HRV-02). The
 * worker harvests it once, drops the harvest beside it and replants it from the drops, silently (ADR-0015).
 */
object HarvesterHandler : TargetHandler {
    const val LANE = "harvester"

    /** A harvest that the land claim checks refused waits as long as a timed-out target (CD-HRV-13). */
    private const val REFUSED_WAIT_TICKS = 6_000L

    override val id: ResourceLocation = id("harvester")

    /** Crops have no block entity, so only a pasture with a Grass-type candidate scans block positions for them (CD-CRP-20). */
    override val scanKind: ScanKind = ScanKind.BLOCK

    override val claimLane: String = LANE

    /** CD-HRV-03: an apricorn first, also one that a datapack adds to the tag. Both halves of a crop share its block. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean {
        if (state.block is ApricornBlock || state.`is`(FarmingTags.APRICORNS) || !state.`is`(FarmingTags.HARVESTER_TARGETS)) return false
        val crop = FarmCells.lower(level, pos, state)
        return kindOf(level, crop, if (crop == pos) state else level.getBlockState(crop)) != null
    }

    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = FarmCells.lower(level, pos, level.getBlockState(pos))

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = FarmCells.isPart(level, canonical, pos)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean {
        val state = level.getBlockState(canonical)
        return state.`is`(FarmingTags.HARVESTER_TARGETS) && kindOf(level, canonical, state) != null
    }

    /** CD-HRV-04: the crop's harvest adapter finds it fully grown, and no land claim refused its harvest in this claim lately. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.let { ctx.now < it.refusedUntil } == true) return false
        val facts = CropFactsReader.read(ctx.level, ctx.target, ctx.level.getBlockState(ctx.target))
        val kind = CropAdapters.harvestKind(facts) ?: return false
        return CropRules.isFullyGrown(kind, facts)
    }

    /** The harvest counts from the tick that this stretch of work started. */
    override fun onStart(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo
        if (memo == null) ctx.memo.value = Memo(ctx.now) else memo.since = ctx.now
    }

    /** CD-HRV-05: one harvest, `harvest_ticks` after the work started. */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        if (!HarvestClock.isHarvestTick(memo.since, ctx.now, HarvestClock.ticksOf(ctx.role.params))) return
        val facts = CropFactsReader.read(ctx.level, ctx.target, ctx.level.getBlockState(ctx.target))
        val kind = CropAdapters.harvestKind(facts) ?: return
        if (!CropRules.isFullyGrown(kind, facts)) return
        if (!HarvestApply.apply(ctx.level, ctx.target, kind, facts, ctx.spot ?: ctx.target, ctx.mayChange)) {
            memo.refusedUntil = ctx.now + REFUSED_WAIT_TICKS
            ctx.stalled(REFUSED_WAIT_TICKS)
        }
    }

    private fun kindOf(level: ServerLevel, pos: BlockPos, state: BlockState): HarvestKind? =
        CropAdapters.harvestKind(CropFactsReader.read(level, pos, state))

    private class Memo(var since: Long) {
        var refusedUntil = Long.MIN_VALUE
    }
}
