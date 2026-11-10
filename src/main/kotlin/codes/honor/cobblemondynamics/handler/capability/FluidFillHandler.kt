package codes.honor.cobblemondynamics.handler.capability

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.vanilla.CauldronFill
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * The Pump's target: a block whose NeoForge fluid capability takes water on one of its faces, and a vanilla cauldron (CD-PMP-01).
 * Tanks, boilers and basins of any mod go through the capability. A cauldron goes through [CauldronFill], never through NeoForge's
 * cauldron wrapper, which moves water only 1,000 mB at a time (CD-PMP-04).
 */
object FluidFillHandler : TargetHandler {
    override val id: ResourceLocation = id("fluid_fill")

    /** The Pump is leash only (CD-PMP-02, CD-ASG-05), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        cauldronOf(state) != null ||
            Direction.entries.any { side ->
                level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, side)
                    ?.let { FluidTick.acceptsWater(FluidPort.of(it)) } == true
            }

    /** A tank that is full, or holds another fluid, stays the target and has no demand (CD-PMP-05). */
    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = cauldronOf(level.getBlockState(canonical)) != null ||
        Direction.entries.any { level.getCapability(Capabilities.FluidHandler.BLOCK, canonical, it) != null }

    override fun hasDemand(ctx: WorkContext): Boolean {
        val rate = mbPerTick(ctx)
        cauldronOf(ctx.level.getBlockState(ctx.target))?.let { return rate > 0 && CauldronFill.hasDemand(it.contents, it.level) }
        val port = port(ctx) ?: return false
        return FluidTick.hasDemand(port, rate)
    }

    override fun applyWork(ctx: WorkContext) {
        val rate = mbPerTick(ctx)
        val cauldron = cauldronOf(ctx.level.getBlockState(ctx.target))
        if (cauldron != null) {
            fillCauldron(ctx, cauldron, rate)
            return
        }
        val port = port(ctx) ?: return
        FluidTick.work(port, rate)
    }

    private fun mbPerTick(ctx: WorkContext): Int = PumpParams.of(ctx.role.params).mbPerTick(ctx.pokemon.level)

    private class Cauldron(val contents: CauldronFill.Contents, val level: Int)

    /** Only vanilla's four cauldrons. The powder snow cauldron has a `LEVEL` too, so the block decides, not the property. */
    private fun cauldronOf(state: BlockState): Cauldron? = when {
        state.`is`(Blocks.CAULDRON) -> Cauldron(CauldronFill.Contents.EMPTY, 0)
        state.`is`(Blocks.WATER_CAULDRON) -> Cauldron(CauldronFill.Contents.WATER, state.getValue(LayeredCauldronBlock.LEVEL))
        state.`is`(Blocks.LAVA_CAULDRON) -> Cauldron(CauldronFill.Contents.LAVA, 0)
        state.`is`(Blocks.POWDER_SNOW_CAULDRON) -> Cauldron(CauldronFill.Contents.POWDER_SNOW, state.getValue(LayeredCauldronBlock.LEVEL))
        else -> null
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
