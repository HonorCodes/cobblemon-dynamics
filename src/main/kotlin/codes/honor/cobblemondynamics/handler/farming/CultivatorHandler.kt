package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Cultivator's target: a block of `#cobblemon_dynamics:cultivator_targets` that bonemeal grows (CD-CUL-02). The worker gives it
 * the bonemeal effect with no item, at a pace of its level, for Work Stamina, and silently (ADR-0015).
 */
object CultivatorHandler : TargetHandler {
    const val LANE = "cultivator"

    override val id: ResourceLocation = id("cultivator")

    /** Crops have no block entity, so only a pasture with a Ground-type candidate scans block positions for them (CD-CRP-20). */
    override val scanKind: ScanKind = ScanKind.BLOCK

    override val claimLane: String = LANE

    /** CD-CRP-10: an auto-assigned worker goes crop to crop. */
    override val releasesWhenDone: Boolean = true

    /** The block's tag first, so that the scan reads no property of a block outside it. Both halves of a crop share its block. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean {
        if (!state.`is`(FarmingTags.CULTIVATOR_TARGETS)) return false
        val crop = FarmCells.lower(level, pos, state)
        return grows(level, crop, if (crop == pos) state else level.getBlockState(crop))
    }

    /** CD-CUL-13: a sapling of a complete 2 × 2 of its kind resolves to the square's least corner, as the Lumberjack resolves it. */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos {
        val state = level.getBlockState(pos)
        if (state.`is`(BlockTags.SAPLINGS)) return SaplingRules.corner(pos) { sameSapling(level, it, state) }
        return FarmCells.lower(level, pos, state)
    }

    /** CD-CUL-13: each cell of the 2 × 2 whose corner is [canonical] is part of that target. */
    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean {
        if (pos == canonical) return true
        val state = level.getBlockState(canonical)
        if (!state.`is`(BlockTags.SAPLINGS)) return FarmCells.isPart(level, canonical, pos)
        val square = SaplingRules.square(canonical)
        return pos in square && square.all { sameSapling(level, it, state) }
    }

    /** CD-ASG-24: a sapling's tree fills its cell, or each cell of its 2 × 2. A crop grows in place. */
    override fun growthCells(level: ServerLevel, canonical: BlockPos): List<BlockPos> {
        val state = level.getBlockState(canonical)
        if (!state.`is`(BlockTags.SAPLINGS)) return emptyList()
        return SaplingRules.growthCells(canonical) { sameSapling(level, it, state) }
    }

    /** CD-ASG-25: a growth use on a sapling grows a tree. */
    override val growsTrees: Boolean = true

    private fun sameSapling(level: ServerLevel, pos: BlockPos, sapling: BlockState): Boolean =
        level.isLoaded(pos) && level.getBlockState(pos).`is`(sapling.block)

    /**
     * CD-CRP-09: a harvest by hand leaves the cell empty, and the leashed worker waits there for the replant. A block of the role's
     * tag keeps the binding too, such as a stem that grows its next fruit.
     */
    override fun bindingHolds(level: ServerLevel, canonical: BlockPos): Boolean =
        FarmCells.plotHolds(level, canonical) { level.getBlockState(canonical).`is`(FarmingTags.CULTIVATOR_TARGETS) }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = grows(level, canonical, level.getBlockState(canonical))

    /**
     * CD-CUL-03, CD-CUL-09, CD-LUM-23: bonemeal can still change the crop, the Harvester would not take it yet, it has not stalled,
     * and a sapling that grows only in a 2 × 2 is in a complete one.
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.stall?.isStalled(ctx.now) == true) return false
        return demandAt(ctx.level, ctx.target, ctx.level.getBlockState(ctx.target))
    }

    /** The uses count from the tick that this stretch of work started. The stall lasts as long as the claim. */
    override fun onStart(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo
        if (memo == null) ctx.memo.value = Memo(ctx.now) else memo.since = ctx.now
    }

    /**
     * CD-CUL-04 to CD-CUL-07: one growth use on each use tick of the pace. NeoForge's `applyBonemeal` runs the block's own bonemeal
     * rule and `BonemealEvent`, as a dispenser does, so a mod that changes or blocks bonemeal on its crops still decides. A use that
     * returns true costs Work Stamina and shows the bonemeal sparkles, with no level event and so no sound. Each use that leaves the
     * crop unchanged counts toward the stall of CD-CUL-09, also a use that the block or a mod refused.
     *
     * CD-CUL-11, CD-CUL-12: the use also grows up to N crops of the area (CD-CRP-23 to CD-CRP-25), each in the same way, and the use
     * tick costs its Work Stamina once when one of its calls returned true. A growth use that throws, as a block of another mod can,
     * counts as a use that changed nothing, with one warning. Each crop of the area keeps its own stall of CD-CUL-09, and a use that
     * leaves it unchanged gives its place to the next crop (CD-CRP-24).
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        val params = CultivatorParams.of(ctx.role.params)
        if (!TickClock.every(GrowthPace.intervalTicks(ctx.pokemon.level, params.usesPerMinuteAt100), memo.since, ctx.now)) return
        val before = FarmCells.states(ctx.level, ctx.target)
        val target = before.first()
        var grew = CropArea.attempt({ e -> AreaGuard.targetFailed(ROLE, target.block, ctx.target, e) }) {
            grow(ctx.level, ctx.target, target)
        } == true
        val changed = FarmCells.states(ctx.level, ctx.target) != before
        val extra = CropArea.count(ctx.pokemon.level, AreaParams.of(ctx.role.params))
        CropArea.walk(ctx.target, extra, { cell -> areaCrop(ctx, cell) }, { cell, state ->
            if (!AreaGuard.take(ctx.level, LANE, cell)) return@walk false
            val cellBefore = FarmCells.states(ctx.level, cell)
            if (grow(ctx.level, cell, state)) grew = true
            val cellChanged = FarmCells.states(ctx.level, cell) != cellBefore
            val stall = memo.areaStalls.getOrPut(cell.asLong()) { GrowthStall() }
            if (stall.record(changed = cellChanged, now = ctx.now)) ctx.stalledAt(cell, stall.waitTicks)
            cellChanged
        }) { cell, state, e -> AreaGuard.failed(ctx.level, ROLE, (state ?: AreaGuard.blockAt(ctx.level, cell)).block, cell, e) }
        val cost = CropArea.cost(grew, params.staminaTicksPerUse)
        if (cost > 0) ctx.spend(cost)
        if (memo.stall.record(changed = changed, now = ctx.now)) ctx.stalled(memo.stall.waitTicks)
    }

    /** CD-CUL-05, CD-CUL-07: one growth use with a new stack that no inventory holds and no player, and its sparkles where it grew. */
    private fun grow(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        val sparkleAt = (state.block as? BonemealableBlock)?.getParticlePos(pos) ?: pos
        val grew = BoneMealItem.applyBonemeal(ItemStack(Items.BONE_MEAL), level, pos, null)
        if (grew) sparkle(level, sparkleAt)
        return grew
    }

    /**
     * CD-CRP-24: the state of the crop at [cell] when the area's use may grow it in this tick, else null: it and the cell above it
     * inside the work area, loaded, not stalled for this worker and not worked by another area of the lane in this tick, no sapling,
     * which grows only as a target, and no block that failed in an area, a target of the role and its own canonical cell, with the
     * demand of CD-CUL-03, free in the Cultivator's lane, and allowed by the land claim checks.
     */
    private fun areaCrop(ctx: WorkContext, cell: BlockPos): BlockState? {
        val area = ctx.area ?: return null
        if (!CropArea.changedCells(cell, null).all(area::contains) || !ctx.level.isLoaded(cell)) return null
        if (ctx.isStalled(cell) || AreaGuard.isWorked(ctx.level, LANE, cell)) return null
        val state = ctx.level.getBlockState(cell)
        if (state.`is`(BlockTags.SAPLINGS) || AreaGuard.isDenied(ctx.level, ROLE, state.block)) return null
        if (!matches(ctx.level, cell, state, null) || canonical(ctx.level, cell) != cell) return null
        if (!demandAt(ctx.level, cell, state) || !ctx.laneFree(cell) || !ctx.mayChange(cell)) return null
        return state
    }

    /** CD-CUL-03, CD-LUM-23: bonemeal can still change the crop, the Harvester would not take it yet, and a sapling's square is complete. */
    private fun demandAt(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        val block = state.block as? BonemealableBlock ?: return false
        if (!saplingGrows(level, pos, state)) return false
        val facts = CropFactsReader.read(level, pos, state)
        val valid = block.isValidBonemealTarget(level, pos, state)
        return CropRules.cultivatorDemand(valid, CropAdapters.harvestKind(facts), facts)
    }

    /** CD-CUL-07: the green of a bone meal use, inside the block, through a particle packet. */
    private fun sparkle(level: ServerLevel, pos: BlockPos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, SPARKLES, 0.25, 0.25, 0.25, 0.0)
    }

    private fun grows(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        state.`is`(FarmingTags.CULTIVATOR_TARGETS) && CropAdapters.grows(CropFactsReader.read(level, pos, state)) &&
            saplingGrows(level, pos, state)

    /**
     * CD-LUM-23: a sapling of [SaplingRules.NEEDS_FOUR] only in a complete 2 × 2 of its kind, because bone meal never grows it alone.
     * A cell in a chunk that is not loaded counts as missing.
     */
    private fun saplingGrows(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        if (!state.`is`(SaplingRules.NEEDS_FOUR)) return true
        return SaplingRules.inFullSquare { dx, dz ->
            val cell = pos.offset(dx, 0, dz)
            level.isLoaded(cell) && level.getBlockState(cell).`is`(state.block)
        }
    }

    /** The target's stall, and one stall for each crop of the area that a use reached in this claim, at most 74. */
    private class Memo(var since: Long) {
        val stall = GrowthStall()
        val areaStalls = HashMap<Long, GrowthStall>()
    }

    private const val SPARKLES = 15
    private const val ROLE = "Cultivator"
}
