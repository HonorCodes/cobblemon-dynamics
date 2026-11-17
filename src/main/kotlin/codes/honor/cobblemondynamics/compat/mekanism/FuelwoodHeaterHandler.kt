package codes.honor.cobblemondynamics.compat.mekanism

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import mekanism.api.heat.IHeatHandler
import mekanism.common.tile.machine.TileEntityFuelwoodHeater
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.BlockCapabilityCache

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

    /** A claim reads the heater's six neighbours through caches, which NeoForge clears when a neighbour or its capabilities change. */
    override fun onClaim(ctx: WorkContext) {
        ctx.memo.value = Neighbours(
            Direction.entries.map { BlockCapabilityCache.create(MekanismCompat.HEAT, ctx.level, ctx.target.relative(it), it.opposite) },
        )
    }

    override fun hasDemand(ctx: WorkContext): Boolean {
        if (heaterAt(ctx) == null) return false
        return FuelwoodRule.hasDemand(neighbourTakesHeat(ctx))
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

    /** One capability cache for each face of the heater, on the neighbour's face toward it. */
    private class Neighbours(val caches: List<BlockCapabilityCache<IHeatHandler, Direction?>>)

    /**
     * A neighbour with a heat capacitor, through the claim's caches. A context outside a claim, as when the engine weighs the heater
     * for a worker, asks the level once.
     */
    private fun neighbourTakesHeat(ctx: WorkContext): Boolean {
        val neighbours = ctx.memo.value as? Neighbours
            ?: return Direction.entries.any { MekanismCompat.heatAt(ctx.level, ctx.target.relative(it), it.opposite) != null }
        return neighbours.caches.any { (it.capability?.heatCapacitorCount ?: 0) > 0 }
    }

    private fun heaterAt(ctx: WorkContext): TileEntityFuelwoodHeater? = ctx.level.getBlockEntity(ctx.target) as? TileEntityFuelwoodHeater
}
