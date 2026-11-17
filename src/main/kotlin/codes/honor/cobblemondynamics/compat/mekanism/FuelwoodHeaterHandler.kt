package codes.honor.cobblemondynamics.compat.mekanism

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import mekanism.common.tile.machine.TileEntityFuelwoodHeater
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker on Mekanism's Fuelwood Heater (CD-FUEL-06). It writes the heater's public burn fields, with no mixin. The heater then
 * makes its own 400 J/t of heat, which its own tick pushes into the blocks beside it.
 */
object FuelwoodHeaterHandler : TargetHandler {
    override val id: ResourceLocation = id("fuelwood_heater")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is TileEntityFuelwoodHeater

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is TileEntityFuelwoodHeater

    override fun hasDemand(ctx: WorkContext): Boolean {
        if (heaterAt(ctx) == null) return false
        return FuelwoodRule.hasDemand(
            Direction.entries.any {
                MekanismCompat.heatAt(ctx.level, ctx.target.relative(it), it.opposite) != null
            },
        )
    }

    /** Mekanism sets the heater's active state itself while it burns. */
    override fun applyWork(ctx: WorkContext) {
        val heater = heaterAt(ctx) ?: return
        if (!hasDemand(ctx)) return
        val (burnTime, maxBurnTime) = FuelwoodRule.worked(heater.burnTime, heater.maxBurnTime)
        if (burnTime == heater.burnTime && maxBurnTime == heater.maxBurnTime) return
        heater.burnTime = burnTime
        heater.maxBurnTime = maxBurnTime
        heater.setChanged()
    }

    private fun heaterAt(ctx: WorkContext): TileEntityFuelwoodHeater? = ctx.level.getBlockEntity(ctx.target) as? TileEntityFuelwoodHeater
}
