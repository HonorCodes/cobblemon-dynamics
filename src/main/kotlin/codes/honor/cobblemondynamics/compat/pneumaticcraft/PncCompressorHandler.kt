package codes.honor.cobblemondynamics.compat.pneumaticcraft

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import me.desht.pneumaticcraft.common.block.entity.compressor.AirCompressorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker on PneumaticCraft's Air Compressor and Advanced Air Compressor, which extends it (CD-FUEL-08). It writes the compressor's
 * public `burnTime`, and its flame gauge through the mixin of `CompressorGauge`. A compressor whose gauge mixin did not apply still
 * burns the worker's fuel, so no gate turns the role off then.
 */
object PncCompressorHandler : TargetHandler {
    override val id: ResourceLocation = id("pnc_compressor")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is AirCompressorBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is AirCompressorBlockEntity

    override fun hasDemand(ctx: WorkContext): Boolean = compressorAt(ctx)?.let(::wantsFuel) ?: false

    /** CD-FUEL-08, CD-FUEL-13: the base burn rate on each tick of work, which keeps a compressor with no Speed Upgrade off its fuel. */
    override fun applyWork(ctx: WorkContext) {
        val compressor = compressorAt(ctx) ?: return
        if (!wantsFuel(compressor)) return
        val gauge = compressor as? CompressorGauge
        val before = CompressorBurn(compressor.burnTime, gauge?.dynamicsMaxBurnTime() ?: 0)
        val after = PncCompressorRule.worked(before, PncCompressorRule.baseBurnRate(compressor.baseProduction))
        if (after == before) return
        compressor.burnTime = after.time
        gauge?.dynamicsSetMaxBurnTime(after.max)
        compressor.setChanged()
    }

    /**
     * CD-FUEL-08: the burn given now makes air in the compressor's next tick, so the check reads the air of that tick. Without the
     * mixin, the private buffer takes its bound.
     */
    private fun wantsFuel(compressor: AirCompressorBlockEntity): Boolean {
        val handler = compressor.getAirHandler(null) ?: return false
        val buffer = (compressor as? CompressorGauge)?.dynamicsAirBuffer() ?: PncCompressorRule.UNKNOWN_BUFFER
        val air = CompressorAir(handler.air, handler.volume, compressor.airPerTick, buffer)
        return PncCompressorRule.hasDemand(air, PncCompat.lowestDanger(handler, compressor), compressor.rsController.shouldRun())
    }

    private fun compressorAt(ctx: WorkContext): AirCompressorBlockEntity? =
        ctx.level.getBlockEntity(ctx.target) as? AirCompressorBlockEntity
}
