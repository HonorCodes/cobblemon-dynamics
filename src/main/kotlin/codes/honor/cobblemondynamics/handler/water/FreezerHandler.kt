package codes.honor.cobblemondynamics.handler.water

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
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

    /** CD-FRZ-03, CD-FRZ-06 to CD-FRZ-09, CD-FRZ-11: the claimed cell is freezable, and no claim question refused it to this worker lately. */
    override fun hasDemand(ctx: WorkContext): Boolean = !ctx.isStalled(ctx.target) && freezable(ctx.level, ctx.target, area(ctx))

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
        } ?: return
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
     * CD-FRZ-03, CD-FRZ-06 to CD-FRZ-09: the cheap facts first, then the 9 × 9 × 2 farmland box and the entity query only for a cell
     * that passes them. A cell of an unloaded chunk in the box counts as farmland, so no freeze dries farmland that the level does not
     * show.
     */
    private fun freezable(level: ServerLevel, cell: BlockPos, area: WorkArea?): Boolean {
        if (!level.isLoaded(cell)) return false
        val cheap = WaterFacts(
            cell = kindOf(level.getBlockState(cell)),
            above = kindOf(level.getBlockState(cell.above())),
            feedsFlow = FreezeRules.feedsFlow(cell) { neighbourKind(level, it) },
            hydratesFarmland = false,
            blockLight = level.getBrightness(LightLayer.BLOCK, cell),
            inWorkArea = area?.contains(cell) == true,
            occupied = false,
        )
        if (!FreezeRules.freezable(cheap)) return false
        val dry = cheap.copy(
            hydratesFarmland = FreezeRules.hydratesFarmland(cell) { !level.isLoaded(it) || level.getBlockState(it).block is FarmBlock },
        )
        if (!FreezeRules.freezable(dry)) return false
        return FreezeRules.freezable(dry.copy(occupied = occupied(level, cell)))
    }

    /** A neighbour in an unloaded chunk counts as flowing water, so a source at the edge of the loaded level stays (CD-FRZ-08). */
    private fun neighbourKind(level: ServerLevel, pos: BlockPos): WaterCell =
        if (level.isLoaded(pos)) kindOf(level.getBlockState(pos)) else WaterCell.FLOWING

    /** A living entity in the cell, such as a swimming player or a wild Cobblemon, which ice would close in. */
    private fun occupied(level: ServerLevel, cell: BlockPos): Boolean =
        level.getEntitiesOfClass(LivingEntity::class.java, AABB(cell)) { it.isAlive && !it.isSpectator }.isNotEmpty()

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

    private class Memo(val since: Long)

    private const val EPSILON = 1e-9
}
