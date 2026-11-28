package codes.honor.cobblemondynamics.handler.capability

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.vanilla.CauldronFill
import codes.honor.cobblemondynamics.handler.vanilla.FarmlandMoisture
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * The Pump's target: a block whose NeoForge fluid capability takes water on one of its faces, a vanilla cauldron, and farmland
 * (CD-PMP-01). Tanks, boilers and basins of any mod go through the capability. A cauldron goes through [CauldronFill], never through
 * NeoForge's cauldron wrapper, which moves water only 1,000 mB at a time (CD-PMP-04). Farmland goes through [FarmlandMoisture], which
 * keeps the farmland around it wet as a water source would (CD-MCH-09).
 */
object FluidFillHandler : TargetHandler {
    override val id: ResourceLocation = id("fluid_fill")

    /** The Pump is leash only (CD-PMP-02, CD-ASG-05), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        cauldronOf(state) != null ||
            isFarmland(state) ||
            Direction.entries.any { side ->
                level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, side)
                    ?.let { FluidTick.acceptsWater(FluidPort.of(it)) } == true
            }

    /** A tank that is full, or holds another fluid, stays the target and has no demand (CD-PMP-05). */
    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        level.getBlockState(canonical).let { cauldronOf(it) != null || isFarmland(it) } ||
            Direction.entries.any { level.getCapability(Capabilities.FluidHandler.BLOCK, canonical, it) != null }

    /** Farmland counts its demand at most once in 20 ticks, and leaves out the blocks that the land claim checks refused (R90). */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val rate = mbPerTick(ctx)
        val state = ctx.level.getBlockState(ctx.target)
        if (isFarmland(state)) {
            return rate > 0 && wetting(ctx).hasDemand(ctx.target, ctx.now, { moistureAt(ctx.level, it) }, ctx.mayChange)
        }
        cauldronOf(state)?.let { return rate > 0 && CauldronFill.hasDemand(it.contents, it.level) }
        val port = port(ctx) ?: return false
        return FluidTick.hasDemand(port, rate)
    }

    /** The farmland checks count from the tick that this stretch of work started. */
    override fun onStart(ctx: WorkContext) {
        if (isFarmland(ctx.level.getBlockState(ctx.target))) wetting(ctx).since = ctx.now
    }

    override fun applyWork(ctx: WorkContext) {
        val rate = mbPerTick(ctx)
        val state = ctx.level.getBlockState(ctx.target)
        if (isFarmland(state)) {
            wetFarmland(ctx, rate)
            return
        }
        val cauldron = cauldronOf(state)
        if (cauldron != null) {
            fillCauldron(ctx, cauldron, rate)
            return
        }
        val port = port(ctx) ?: return
        FluidTick.work(port, rate)
    }

    private fun mbPerTick(ctx: WorkContext): Int = PumpParams.of(ctx.role.params).mbPerTick(ctx.pokemon.level, ctx.multiplier)

    private class Cauldron(val contents: CauldronFill.Contents, val level: Int)

    /** Only vanilla's four cauldrons. The powder snow cauldron has a `LEVEL` too, so the block decides, not the property. */
    private fun cauldronOf(state: BlockState): Cauldron? = when {
        state.`is`(Blocks.CAULDRON) -> Cauldron(CauldronFill.Contents.EMPTY, 0)
        state.`is`(Blocks.WATER_CAULDRON) -> Cauldron(CauldronFill.Contents.WATER, state.getValue(LayeredCauldronBlock.LEVEL))
        state.`is`(Blocks.LAVA_CAULDRON) -> Cauldron(CauldronFill.Contents.LAVA, 0)
        state.`is`(Blocks.POWDER_SNOW_CAULDRON) -> Cauldron(CauldronFill.Contents.POWDER_SNOW, state.getValue(LayeredCauldronBlock.LEVEL))
        else -> null
    }

    /** Vanilla's farmland and each mod's farmland that extends it, by its moisture property. */
    private fun isFarmland(state: BlockState): Boolean = state.block is FarmBlock && state.hasProperty(FarmBlock.MOISTURE)

    /** The moisture of the farmland at [pos], or null for another block or a chunk that is not loaded, which no check loads. */
    private fun moistureAt(level: ServerLevel, pos: BlockPos): Int? {
        if (!level.isLoaded(pos)) return null
        val state = level.getBlockState(pos)
        return if (isFarmland(state)) state.getValue(FarmBlock.MOISTURE) else null
    }

    /** The claim's farmland memo. A context outside a claim gets one of its own, which counts the demand at once. */
    private fun wetting(ctx: WorkContext): FarmlandMoisture.Wetting =
        ctx.memo.value as? FarmlandMoisture.Wetting ?: FarmlandMoisture.Wetting(ctx.now).also { ctx.memo.value = it }

    /**
     * CD-MCH-09: a check on the first tick of work and every 20 ticks after it. Each block goes to moisture 7 with flag 2, as vanilla's
     * random tick of farmland near water sets it, so it posts no game event.
     */
    private fun wetFarmland(ctx: WorkContext, rate: Int) {
        if (rate <= 0) return
        wetting(ctx).work(ctx.target, ctx.now, { moistureAt(ctx.level, it) }, ctx.mayChange) { pos ->
            val farmland = ctx.level.getBlockState(pos)
            ctx.level.setBlock(pos, farmland.setValue(FarmBlock.MOISTURE, FarmlandMoisture.WET), Block.UPDATE_CLIENTS)
        }
    }

    /** The mB that the claim has delivered toward the cauldron's next level. */
    private class Delivered(var mb: Int)

    /**
     * A new level goes in by setting the block state, as vanilla's `receiveStalactiteDrip` does, with its game event. Vanilla's drip
     * sound stays out: work sounds play only for clients that turn them on (ADR-0007).
     */
    private fun fillCauldron(ctx: WorkContext, cauldron: Cauldron, rate: Int) {
        if (rate <= 0 || !CauldronFill.hasDemand(cauldron.contents, cauldron.level)) return
        val delivered = ctx.memo.value as? Delivered ?: Delivered(0).also { ctx.memo.value = it }
        val fill = CauldronFill.deliver(cauldron.level, delivered.mb, rate)
        delivered.mb = fill.storedMb
        if (fill.level == cauldron.level) return
        val next = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, fill.level)
        ctx.level.setBlockAndUpdate(ctx.target, next)
        ctx.level.gameEvent(GameEvent.BLOCK_CHANGE, ctx.target, GameEvent.Context.of(next))
    }

    /** The face that a claim fills through, in a cache that NeoForge clears when the block or its capabilities change. */
    private class Face(val cache: BlockCapabilityCache<IFluidHandler, Direction?>)

    /** The claim's face while it takes water, else the face nearest the worker that takes it, which the claim then keeps. */
    private fun port(ctx: WorkContext): FluidPort? {
        (ctx.memo.value as? Face)?.cache?.capability?.let(FluidPort::of)?.takeIf { it.simulate(1) == 1 }?.let { return it }
        for (side in NearestFaces.of(ctx.target, ctx.worker.position())) {
            val port = ctx.level.getCapability(Capabilities.FluidHandler.BLOCK, ctx.target, side)?.let(FluidPort::of) ?: continue
            if (port.simulate(1) != 1) continue
            ctx.memo.value = Face(BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, ctx.level, ctx.target, side))
            return port
        }
        return null
    }
}
