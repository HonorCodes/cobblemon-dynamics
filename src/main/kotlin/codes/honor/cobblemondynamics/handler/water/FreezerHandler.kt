package codes.honor.cobblemondynamics.handler.water

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * The Freezer: an Ice-type ices over the water of its pasture's work area on top, within reach of itself (ADR-0019). It claims one
 * water cell, freezes the cells around it nearest first and its own cell last, then lets the claim go, so that the next scan pass gives
 * it the nearest cell left, as a farming worker goes crop to crop. A freeze sets ice with `Level.setBlock`, which plays nothing, and the
 * worker walks out over the new ice. Vanilla melts the ice where the light allows.
 */
object FreezerHandler : TargetHandler {
    override val id: ResourceLocation = id("freezer")

    /** Water has no block entity, so only a pasture with an Ice-type candidate scans block positions for it. */
    override val scanKind: ScanKind = ScanKind.BLOCK

    /** CD-FRZ-12: an auto-assigned worker goes cell to cell. */
    override val releasesWhenDone: Boolean = true

    /** CD-FRZ-03: the cell and the cell above it. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        FreezeRules.matches(kindOf(state), kindOf(level.getBlockState(pos.above())))

    /** A frozen cell ends the claim at once (CD-CLM-08). */
    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        matches(level, canonical, level.getBlockState(canonical), null)

    /**
     * CD-FRZ-03, CD-FRZ-04, CD-FRZ-06 to CD-FRZ-09, CD-FRZ-11, CD-FRZ-15, CD-FRZ-16: the claimed cell is freezable, and no claim question
     * refused it to this worker lately. The scan and the arrival run the whole test. A Working tick runs the cheap facts only, because
     * each freeze tick runs the whole test on its cell, and a freeze tick with nothing left to freeze ends the work (R84).
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if (!FreezerParams.of(ctx.role.params).freezes || ctx.isStalled(ctx.target)) return false
        val working = ctx.memo.value as? Memo ?: return freezable(ctx.level, ctx.target, area(ctx))
        return !working.done && FreezeRules.stillFreezable(cheapFacts(ctx.level, ctx.target, area(ctx)) ?: return false)
    }

    /** The worker never stands in water, so it works from the shore or from the ice, and no freeze closes it in. */
    override fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean =
        (0 until maxOf(1, ceil(bodyHeight).toInt())).all { level.getFluidState(spot.above(it)).isEmpty }

    /** The freezes count from the tick that this stretch of work started. */
    override fun onStart(ctx: WorkContext) {
        ctx.memo.value = Memo(ctx.now)
    }

    /**
     * CD-FRZ-04, CD-FRZ-05, CD-FRZ-10, CD-FRZ-11: on each freeze tick of the pace, the freezable cell nearest to the worker within reach
     * turns into ice, and the claimed cell comes last. The use question and the break question of that cell come in the tick of the
     * freeze, and a refusal leaves the cell as water, with no demand for this worker for 6,000 ticks. Work Stamina drains only through
     * Working.
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        val params = FreezerParams.of(ctx.role.params)
        if (!FreezePace.isFreezeTick(memo.since, ctx.now, FreezePace.intervalTicks(ctx.pokemon.level, params.cellsPerMinuteAt100))) return
        val area = area(ctx) ?: return
        // On arrival the engine puts the worker on its spot, which it holds while it Works.
        val from = ctx.spot ?: ctx.worker.blockPosition()
        val cells = matchesInReach(ctx.level, area, from, params.reach)
        val cell = FreezeRules.next(Vec3.atCenterOf(from), ctx.target, cells, params.reach) {
            !ctx.isStalled(it) && freezable(ctx.level, it, area)
        }
        if (cell == null) {
            // The claimed cell failed the whole test, so the next demand check lets the claim go.
            memo.done = true
            return
        }
        if (!ctx.mayChange(cell) || !ctx.mayBreak(cell)) {
            ctx.stalledAt(cell, FreezeRules.REFUSED_WAIT_TICKS)
            return
        }
        ctx.level.setBlock(cell, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL)
    }

    /** The cells in the work area and within [reach] of [from], between block centres, that the scan would match. Two reads each. */
    private fun matchesInReach(level: ServerLevel, area: WorkArea, from: BlockPos, reach: Double): List<BlockPos> {
        val r = ceil(reach).toInt()
        val limit = reach * reach + EPSILON
        val found = ArrayList<BlockPos>()
        val pos = BlockPos.MutableBlockPos()
        for (x in maxOf(from.x - r, area.minX)..minOf(from.x + r, area.maxX)) {
            for (y in maxOf(from.y - r, area.minY)..minOf(from.y + r, area.maxY)) {
                for (z in maxOf(from.z - r, area.minZ)..minOf(from.z + r, area.maxZ)) {
                    val dx = x - from.x
                    val dy = y - from.y
                    val dz = z - from.z
                    if (dx * dx + dy * dy + dz * dz > limit || !level.isLoaded(pos.set(x, y, z))) continue
                    if (matches(level, pos, level.getBlockState(pos), null)) found += pos.immutable()
                }
            }
        }
        return found
    }

    /**
     * CD-FRZ-03, CD-FRZ-08, CD-FRZ-09: the facts that a Working tick reads, about 8 cells, with the costly facts left false. Null for a
     * cell in an unloaded chunk.
     */
    private fun cheapFacts(level: ServerLevel, cell: BlockPos, area: WorkArea?): WaterFacts? {
        if (!level.isLoaded(cell)) return null
        return WaterFacts(
            cell = kindOf(level.getBlockState(cell)),
            above = kindOf(level.getBlockState(cell.above())),
            feedsFlow = FreezeRules.feedsFlow(cell) { neighbourKind(level, it) },
            hydratesFarmland = false,
            blockLight = level.getBrightness(LightLayer.BLOCK, cell),
            inWorkArea = area?.contains(cell) == true,
            occupied = false,
            neededByBlock = false,
        )
    }

    /**
     * CD-FRZ-03, CD-FRZ-06 to CD-FRZ-09, CD-FRZ-15, CD-FRZ-16: the cheap facts first, then the 9 cells of the blocks that need the
     * water, the 9 × 9 × 2 farmland box and the entity query, each only for a cell that passed the ones before it. A cell of an
     * unloaded chunk counts as such a block and as farmland, so no freeze dries a block that the level does not show.
     */
    private fun freezable(level: ServerLevel, cell: BlockPos, area: WorkArea?): Boolean {
        val cheap = cheapFacts(level, cell, area) ?: return false
        if (!FreezeRules.stillFreezable(cheap)) return false
        val needed = FreezeRules.neededByBlock(cell) { !level.isLoaded(it) || level.getBlockState(it).`is`(NEEDS_ADJACENT_WATER) }
        if (needed) return false
        if (FreezeRules.hydratesFarmland(cell) { !level.isLoaded(it) || level.getBlockState(it).block is FarmBlock }) return false
        return !occupied(level, cell)
    }

    /** A neighbour in an unloaded chunk counts as flowing water, so a source at the edge of the loaded level stays (CD-FRZ-08). */
    private fun neighbourKind(level: ServerLevel, pos: BlockPos): WaterCell =
        if (level.isLoaded(pos)) kindOf(level.getBlockState(pos)) else WaterCell.FLOWING

    /**
     * CD-FRZ-16: an entity in the cell or in the water column under it, such as a swimming player, a diver, a boat or a wild Cobblemon,
     * which ice would close in. A dropped item and an XP orb do not count, and neither does the work focus entity, which the engine
     * puts at the centre of a Working worker's target for its effect: otherwise no Freezer could freeze its own claimed cell.
     */
    private fun occupied(level: ServerLevel, cell: BlockPos): Boolean {
        val box = FreezeRules.occupantBox(cell) { level.isLoaded(it) && level.getFluidState(it).`is`(FluidTags.WATER) }
        return level.getEntities(null as Entity?, box) { holdsWater(it) }.isNotEmpty()
    }

    private fun holdsWater(entity: Entity): Boolean =
        entity !is ItemEntity && entity !is ExperienceOrb && entity !is WorkFocusEntity && !entity.isSpectator

    private fun kindOf(state: BlockState): WaterCell {
        val fluid = state.fluidState
        return WaterCell.of(
            air = state.isAir,
            waterBlock = state.`is`(Blocks.WATER),
            bubbleColumn = state.`is`(Blocks.BUBBLE_COLUMN),
            water = fluid.`is`(FluidTags.WATER),
            source = fluid.isSource,
        )
    }

    /** CD-FRZ-03: the work area of the worker's own pasture. A Freezer takes no Quick Work, so a worker always has one. */
    private fun area(ctx: WorkContext): WorkArea? =
        ctx.pasture?.let { WorkArea.of(it, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()) }

    /** [done]: a freeze tick found nothing left to freeze, so the claim ends. */
    private class Memo(val since: Long) {
        var done = false
    }

    /** CD-FRZ-15: the blocks that need the water beside them, such as sugar cane, corals and a conduit. */
    private val NEEDS_ADJACENT_WATER: TagKey<Block> = TagKey.create(Registries.BLOCK, id("needs_adjacent_water"))

    private const val EPSILON = 1e-9
}
