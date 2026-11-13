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

    /** The block's tag first, so that the scan reads no property of a block outside it. Both halves of a crop share its block. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean {
        if (!state.`is`(FarmingTags.CULTIVATOR_TARGETS)) return false
        val crop = FarmCells.lower(level, pos, state)
        return grows(level, crop, if (crop == pos) state else level.getBlockState(crop))
    }

    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = FarmCells.lower(level, pos, level.getBlockState(pos))

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = FarmCells.isPart(level, canonical, pos)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = grows(level, canonical, level.getBlockState(canonical))

    /** CD-CUL-03, CD-CUL-09: bonemeal can still change the crop, the Harvester would not take it yet, and it has not stalled. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.stall?.isStalled(ctx.now) == true) return false
        val state = ctx.level.getBlockState(ctx.target)
        val block = state.block as? BonemealableBlock ?: return false
        val facts = CropFactsReader.read(ctx.level, ctx.target, state)
        val valid = block.isValidBonemealTarget(ctx.level, ctx.target, state)
        return CropRules.cultivatorDemand(valid, CropAdapters.harvestKind(facts), facts)
    }

    /** The uses count from the tick that this stretch of work started. The stall lasts as long as the claim. */
    override fun onStart(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo
        if (memo == null) ctx.memo.value = Memo(ctx.now) else memo.since = ctx.now
    }

    /**
     * CD-CUL-04 to CD-CUL-07: one growth use on each use tick of the pace. NeoForge's `applyBonemeal` runs the block's own bonemeal
     * rule and `BonemealEvent`, as a dispenser does, so a mod that changes or blocks bonemeal on its crops still decides. A use that
     * returns true costs Work Stamina and shows the bonemeal sparkles, with no level event and so no sound.
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        val params = CultivatorParams.of(ctx.role.params)
        if (!TickClock.every(GrowthPace.intervalTicks(ctx.pokemon.level, params.usesPerMinuteAt100), memo.since, ctx.now)) return
        val before = FarmCells.states(ctx.level, ctx.target)
        val sparkleAt = (before.first().block as? BonemealableBlock)?.getParticlePos(ctx.target) ?: ctx.target
        // CD-CUL-05: a new stack that no inventory holds, and no player.
        if (!BoneMealItem.applyBonemeal(ItemStack(Items.BONE_MEAL), ctx.level, ctx.target, null)) return
        ctx.spend(params.staminaTicksPerUse)
        sparkle(ctx.level, sparkleAt)
        if (memo.stall.record(changed = FarmCells.states(ctx.level, ctx.target) != before, now = ctx.now)) ctx.stalled(memo.stall.waitTicks)
    }

    /** CD-CUL-07: the green of a bone meal use, inside the block, through a particle packet. */
    private fun sparkle(level: ServerLevel, pos: BlockPos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, SPARKLES, 0.25, 0.25, 0.25, 0.0)
    }

    private fun grows(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        state.`is`(FarmingTags.CULTIVATOR_TARGETS) && CropAdapters.grows(CropFactsReader.read(level, pos, state))

    private class Memo(var since: Long) {
        val stall = GrowthStall()
    }

    private const val SPARKLES = 15
}
