package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.block.ApricornBlock
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.AttachedStemBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Harvester's target: a fully grown crop of `#cobblemon_dynamics:harvester_targets` that a harvest adapter reads (CD-HRV-02). The
 * worker harvests it once, drops the harvest beside it and replants it from the drops, silently (ADR-0015).
 */
object HarvesterHandler : TargetHandler {
    const val LANE = "harvester"

    /**
     * A harvest that the land claim checks refused waits as long as a timed-out target (CD-HRV-13), and so does a pick whose loot table
     * did not load (CD-FRM-03).
     */
    private const val REFUSED_WAIT_TICKS = 6_000L
    private const val ROLE = "Harvester"

    override val id: ResourceLocation = id("harvester")

    /** Crops have no block entity, so only a pasture with a Grass-type candidate scans block positions for them (CD-CRP-20). */
    override val scanKind: ScanKind = ScanKind.BLOCK

    override val claimLane: String = LANE

    /** CD-CRP-10: an auto-assigned worker goes crop to crop. */
    override val releasesWhenDone: Boolean = true

    /** CD-HRV-03: an apricorn first, also one that a datapack adds to the tag. Both halves of a crop share its block. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean {
        if (state.block is ApricornBlock || state.`is`(FarmingTags.APRICORNS) || !state.`is`(FarmingTags.HARVESTER_TARGETS)) return false
        val crop = FarmCells.lower(level, pos, state)
        return kindOf(level, crop, if (crop == pos) state else level.getBlockState(crop)) != null
    }

    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = FarmCells.lower(level, pos, level.getBlockState(pos))

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = FarmCells.isPart(level, canonical, pos)

    /**
     * CD-CRP-09: a harvest by hand leaves the cell empty, and the leashed worker waits there for the replant. A block of the role's
     * tag keeps the binding too, such as a stem that grows its next fruit.
     */
    override fun bindingHolds(level: ServerLevel, canonical: BlockPos): Boolean =
        FarmCells.plotHolds(level, canonical) { level.getBlockState(canonical).`is`(FarmingTags.HARVESTER_TARGETS) }

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

    /**
     * CD-HRV-05: one harvest, `harvest_ticks` after the work started. CD-HRV-24: the harvest also takes up to N fully grown crops of the
     * area (CD-CRP-23 to CD-CRP-25), each in the same way. A crop of the area that the land claim checks refuse counts as not harvested,
     * and the worker skips it as long as a refused target. A refused target ends the harvest with no area.
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        if (!HarvestClock.isHarvestTick(memo.since, ctx.now, HarvestClock.ticksOf(ctx.role.params))) return
        val facts = CropFactsReader.read(ctx.level, ctx.target, ctx.level.getBlockState(ctx.target))
        val kind = CropAdapters.harvestKind(facts) ?: return
        if (!CropRules.isFullyGrown(kind, facts)) return
        val spot = ctx.spot ?: ctx.target
        if (!HarvestApply.apply(ctx.level, ctx.target, kind, facts, spot, ctx.mayChange, ctx.mayBreak)) {
            memo.refusedUntil = ctx.now + REFUSED_WAIT_TICKS
            ctx.stalled(REFUSED_WAIT_TICKS)
            return
        }
        val extra = CropArea.count(ctx.pokemon.level, AreaParams.of(ctx.role.params))
        CropArea.walk(ctx.target, extra, { cell -> ripeCrop(ctx, cell) }, { cell, crop ->
            if (!AreaGuard.take(ctx.level, LANE, cell)) return@walk false
            val harvested = HarvestApply.apply(ctx.level, cell, crop.kind, crop.facts, spot, ctx.mayChange, ctx.mayBreak)
            if (!harvested) ctx.stalledAt(cell, REFUSED_WAIT_TICKS)
            harvested
        }) { cell, crop, e -> AreaGuard.failed(ctx.level, ROLE, crop?.block ?: AreaGuard.blockAt(ctx.level, cell).block, cell, e) }
    }

    /**
     * CD-CRP-24: the crop at [cell] when the area's harvest may take it in this tick, else null: loaded, not stalled for this worker
     * and not worked by another area of the lane in this tick, no sapling and no block that failed in an area, a target of the role and
     * its own canonical cell, fully grown for its adapter, each cell that the harvest changes inside the work area, and free in the
     * Harvester's lane. The harvest itself asks the land claim checks.
     */
    private fun ripeCrop(ctx: WorkContext, cell: BlockPos): AreaCrop? {
        val area = ctx.area ?: return null
        if (!area.contains(cell) || !ctx.level.isLoaded(cell)) return null
        if (ctx.isStalled(cell) || AreaGuard.isWorked(ctx.level, LANE, cell)) return null
        val state = ctx.level.getBlockState(cell)
        if (state.`is`(BlockTags.SAPLINGS) || AreaGuard.isDenied(ctx.level, ROLE, state.block)) return null
        if (!matches(ctx.level, cell, state, null) || canonical(ctx.level, cell) != cell) return null
        val facts = CropFactsReader.read(ctx.level, cell, state)
        val kind = CropAdapters.harvestKind(facts) ?: return null
        if (!CropRules.isFullyGrown(kind, facts)) return null
        val facing = if (kind == HarvestKind.GOURD) state.getOptionalValue(AttachedStemBlock.FACING).orElse(null) else null
        val fruit = facing?.let { cell.relative(it) }
        if (!CropArea.changedCells(cell, fruit).all(area::contains) || !ctx.laneFree(cell)) return null
        return AreaCrop(kind, facts, state.block)
    }

    private class AreaCrop(val kind: HarvestKind, val facts: CropFacts, val block: Block)

    private fun kindOf(level: ServerLevel, pos: BlockPos, state: BlockState): HarvestKind? =
        CropAdapters.harvestKind(CropFactsReader.read(level, pos, state))

    private class Memo(var since: Long) {
        var refusedUntil = Long.MIN_VALUE
    }
}
