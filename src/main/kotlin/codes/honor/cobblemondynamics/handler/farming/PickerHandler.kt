package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.block.ApricornBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Picker's target (R95): a ripe apricorn, or a ripe block of `#cobblemon_dynamics:picker_targets`, inside the work area. A pick does
 * what a player's right click on a ripe apricorn does, with no player: the fruit's loot drops in its cell and falls to the ground, where a
 * player or a Magnet picks it up, and the block goes back to its first age, so it grows again. The pick is silent (CD-PCK-11).
 */
object PickerHandler : TargetHandler {
    /** A pick that the land claim checks refused, or that failed, waits as long as a refused harvest (CD-HRV-13, CD-PCK-09). */
    private const val REFUSED_WAIT_TICKS = 6_000L
    private const val ROLE = "Picker"

    /** The key of the Picker's areas among the cells that one tick works (CD-PCK-08). The Picker's claims have no lane. */
    private const val AREA_LANE = "picker"

    private val warnings = OnceLogger()

    override val id: ResourceLocation = id("picker")

    /** A fruit has no block entity, so only a pasture with a Bug/Flying candidate scans block positions for it. */
    override val scanKind: ScanKind = ScanKind.BLOCK

    /** An auto worker goes fruit to fruit, as the Harvester goes crop to crop (CD-CRP-10). */
    override val releasesWhenDone: Boolean = true

    /** CD-PCK-05: an apricorn or a block of the tag, ripe or not. The demand waits for the ripe fruit. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean = isTarget(state)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = isTarget(level.getBlockState(canonical))

    /** CD-PCK-06: the fruit is ripe on its tree, and no land claim refused its pick in this claim lately. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.let { ctx.now < it.refusedUntil } == true) return false
        return isRipe(ctx.level, ctx.target, ctx.level.getBlockState(ctx.target))
    }

    /** The pick counts from the tick that this stretch of work started. */
    override fun onStart(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo
        if (memo == null) ctx.memo.value = Memo(ctx.now) else memo.since = ctx.now
    }

    /**
     * CD-PCK-07: one pick, `harvest_ticks` after the work started. CD-PCK-08: the pick also takes up to N ripe fruits of the area of the
     * farming roles (CD-CRP-23, CD-CRP-25), nearest first, each in the same way. A refused or failed target ends the pick with no area and
     * waits, and a refused fruit of the area waits as long for this worker while the next cell takes its place.
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        if (!HarvestClock.isHarvestTick(memo.since, ctx.now, HarvestClock.ticksOf(ctx.role.params))) return
        val state = ctx.level.getBlockState(ctx.target)
        if (!isRipe(ctx.level, ctx.target, state)) return
        val picked = CropArea.attempt({ e -> warnFailed(state.block, ctx.target, e) }) { pick(ctx, ctx.target, state) }
        if (picked != true) {
            memo.refusedUntil = ctx.now + REFUSED_WAIT_TICKS
            ctx.stalled(REFUSED_WAIT_TICKS)
            return
        }
        val extra = CropArea.count(ctx.pokemon.level, AreaParams.of(ctx.role.params))
        CropArea.walk(ctx.target, extra, { cell -> ripeCell(ctx, cell) }, { cell, cellState ->
            if (!AreaGuard.take(ctx.level, AREA_LANE, cell)) return@walk false
            val done = pick(ctx, cell, cellState)
            if (!done) ctx.stalledAt(cell, REFUSED_WAIT_TICKS)
            done
        }) { cell, cellState, e ->
            AreaGuard.failed(ctx.level, ROLE, cellState?.block ?: AreaGuard.blockAt(ctx.level, cell).block, cell, e)
        }
    }

    /** CD-PCK-08: the state at [cell] when the area of this pick may take it in this tick, else null. */
    private fun ripeCell(ctx: WorkContext, cell: BlockPos): BlockState? {
        val area = ctx.area ?: return null
        val facts = LevelCell(ctx, cell) { area.contains(it) }
        return if (PickerRules.eligible(facts)) facts.state else null
    }

    /** The facts of [PickerRules.AreaCell] at [cell], each read when [PickerRules.eligible] asks for it, and the block state once. */
    private class LevelCell(private val ctx: WorkContext, private val cell: BlockPos, private val inWorkArea: (BlockPos) -> Boolean) :
        PickerRules.AreaCell {
        val state: BlockState by lazy { ctx.level.getBlockState(cell) }
        override val inArea: Boolean get() = inWorkArea(cell)
        override val loaded: Boolean get() = ctx.level.isLoaded(cell)
        override val stalled: Boolean get() = ctx.isStalled(cell)
        override val worked: Boolean get() = AreaGuard.isWorked(ctx.level, AREA_LANE, cell)
        override val denied: Boolean get() = AreaGuard.isDenied(ctx.level, ROLE, state.block)
        override val ripe: Boolean get() = isRipe(ctx.level, cell, state, guarded = false)
        override val laneFree: Boolean get() = ctx.laneFree(cell)
    }

    /**
     * CD-PCK-06, CD-PCK-09: picks the ripe fruit [state] at [cell] when both land claim checks allow it. An apricorn takes Cobblemon's
     * own harvest, the one that a player's right click runs: the block's loot drops in its cell, and the apricorn goes back to age 0, with
     * no player and no sound. Another block of the tag takes its lowest `age` in the same way and then drops the loot of its ripe state
     * with no tool. Returns whether the pick ran.
     */
    private fun pick(ctx: WorkContext, cell: BlockPos, state: BlockState): Boolean {
        if (!PickerRules.mayPick({ ctx.mayChange(cell) }, { ctx.mayBreak(cell) })) return false
        val block = state.block
        if (block is ApricornBlock) {
            block.harvest(ctx.level, state, cell)
            return true
        }
        val age = CropFactsReader.ageProperty(state) ?: return false
        // The reset comes first, so a block that refuses its new state drops no loot. The loot is that of the ripe state.
        if (!ctx.level.setBlock(cell, state.setValue(age, age.possibleValues.min()), Block.UPDATE_CLIENTS)) return false
        Block.dropResources(state, ctx.level, cell)
        return true
    }

    private fun isTarget(state: BlockState): Boolean = PickerRules.isTarget(
        state.block is ApricornBlock,
        { state.`is`(FarmingTags.PICKER_TARGETS) },
        { isRefused(state.block) },
        { CropFactsReader.hasHalf(state) },
        { CropFactsReader.ageProperty(state) != null },
    )

    private fun isRefused(block: Block): Boolean = BuiltInRegistries.BLOCK.getKey(block).toString() in PickerRules.REFUSED

    /** CD-PCK-05: the kind of the tag's member [block] for the load line of `FarmingLines`, from its default state. */
    fun memberOf(block: Block): PickerRules.TagMember {
        val state = block.defaultBlockState()
        return PickerRules.memberOf(
            block is ApricornBlock,
            isRefused(block),
            CropFactsReader.hasHalf(state),
            CropFactsReader.ageProperty(state) != null,
        )
    }

    /**
     * CD-PCK-05: a target at its largest `age` that still holds where it hangs, as an apricorn on its leaves. With [guarded] false, as in
     * an area, a `canSurvive` that throws reaches the walk, which leaves the block out of the area (CD-PCK-08).
     */
    private fun isRipe(level: ServerLevel, pos: BlockPos, state: BlockState, guarded: Boolean = true): Boolean {
        if (!isTarget(state)) return false
        val age = CropFactsReader.ageProperty(state) ?: return false
        if (!PickerRules.isRipe(state.getValue(age), age.possibleValues.max())) return false
        if (!guarded) return state.canSurvive(level, pos)
        return CropArea.attempt({ e -> warnFailed(state.block, pos, e) }) { state.canSurvive(level, pos) } == true
    }

    /** A block of another mod in the tag can throw, and the role stays on (CD-LUM-29). */
    private fun warnFailed(block: Block, pos: BlockPos, e: Throwable) {
        val key = BuiltInRegistries.BLOCK.getKey(block)
        warnings.warn("pick:$key") { "The $ROLE skips $key at $pos, whose check or pick fails: $e" }
    }

    private class Memo(var since: Long) {
        var refusedUntil = Long.MIN_VALUE
    }
}
