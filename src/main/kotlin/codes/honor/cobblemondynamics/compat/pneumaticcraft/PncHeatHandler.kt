package codes.honor.cobblemondynamics.compat.pneumaticcraft

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.capability.NearestFaces
import codes.honor.cobblemondynamics.id
import me.desht.pneumaticcraft.api.PNCCapabilities
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.BlockCapabilityCache

/**
 * The Heater on PneumaticCraft heat: a Fire-type warms a block entity through the heat exchanger of PneumaticCraft's API, as one lava
 * block beside it would (CD-HTR-06, CD-HTR-07). It asks a horizontal face only: the null side of some machines returns a dummy
 * exchanger (RES12), and the machines that take heat on a face, such as the Thermal Compressor, take it on the horizontal ones. It reads
 * `PNCCapabilities` only in its methods, inside the engine's guard, so a later PneumaticCraft that renames a field turns off this handler
 * alone.
 */
object PncHeatHandler : TargetHandler {
    private val HORIZONTAL: List<Direction> = Direction.Plane.HORIZONTAL.toList()

    override val id: ResourceLocation = id("pnc_heat")

    /** Leash only (CD-HTR-06): some machines need a cold side, which only the player knows. */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean = blockEntity != null &&
        HORIZONTAL.any { level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, pos, state, blockEntity, it) != null }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        HORIZONTAL.any { PncCompat.heatAt(level, canonical, it) != null }

    /** CD-HTR-07: while the exchanger is colder than the lava block that the Heater copies. */
    override fun hasDemand(ctx: WorkContext): Boolean = exchanger(ctx)?.let { heat(ctx, it) > 0.0 } ?: false

    override fun applyWork(ctx: WorkContext) {
        val exchanger = exchanger(ctx) ?: return
        val heat = heat(ctx, exchanger)
        if (heat > 0.0) exchanger.addHeat(heat)
    }

    private fun heat(ctx: WorkContext, exchanger: IHeatExchangerLogic): Double =
        PncHeatMath.heatPerTick(PncHeaterParams.of(ctx.role.params), exchanger.temperature, ctx.pokemon.level, ctx.multiplier)

    /** The face that a claim heats through, in a cache that NeoForge clears when the block or its capabilities change. */
    private class Face(val cache: BlockCapabilityCache<IHeatExchangerLogic, Direction?>)

    /** CD-HTR-07: the claim's face while it still has an exchanger, else the horizontal face nearest the worker that has one. */
    private fun exchanger(ctx: WorkContext): IHeatExchangerLogic? {
        (ctx.memo.value as? Face)?.cache?.capability?.let { return it }
        for (side in NearestFaces.of(ctx.target, ctx.worker.position()).filter { it.axis.isHorizontal }) {
            val exchanger = PncCompat.heatAt(ctx.level, ctx.target, side) ?: continue
            ctx.memo.value = Face(BlockCapabilityCache.create(PNCCapabilities.HEAT_EXCHANGER_BLOCK, ctx.level, ctx.target, side))
            return exchanger
        }
        ctx.memo.value = null
        return null
    }
}
