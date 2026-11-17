package codes.honor.cobblemondynamics.compat.mekanism

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.capability.NearestFaces
import codes.honor.cobblemondynamics.id
import mekanism.api.heat.IHeatHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.BlockCapabilityCache

/**
 * The Heater on Mekanism heat: a Fire-type warms the valves of `#cobblemon_dynamics:heater_targets` through Mekanism's
 * `IHeatHandler`, the API of its own heat blocks (CD-HTR-01 to CD-HTR-05). It never reaches a reactor port, the Heat Generator or
 * the Entangloporter, which expose the same capability outside the tag (R80).
 */
object MekanismHeatHandler : TargetHandler {
    private val TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("heater_targets"))

    override val id: ResourceLocation = id("mekanism_heat")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        HeaterMath.isTarget(state.`is`(TARGETS)) { exposesHeat(level, pos) }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        HeaterMath.isTarget(level.getBlockState(canonical).`is`(TARGETS)) { exposesHeat(level, canonical) }

    override fun hasDemand(ctx: WorkContext): Boolean = handler(ctx)?.let { HeaterMath.hasDemand(temperatures(it)) } ?: false

    /** CD-HTR-04: Mekanism's own `handleHeat` splits the heat over the target's capacitors by their capacity. */
    override fun applyWork(ctx: WorkContext) {
        val handler = handler(ctx) ?: return
        if (!HeaterMath.hasDemand(temperatures(handler))) return
        val heat = HeaterMath.heatPerTick(HeaterParams.of(ctx.role.params).heatPerTickAt100, ctx.pokemon.level)
        handler.handleHeat(heat.toDouble())
    }

    /** The face that a claim heats through, in a cache that NeoForge clears when the block or its capabilities change. */
    private class Face(val cache: BlockCapabilityCache<IHeatHandler, Direction?>)

    /** CD-HTR-04: the claim's face while it still has a capacitor, else the face nearest the worker that has one. */
    private fun handler(ctx: WorkContext): IHeatHandler? {
        (ctx.memo.value as? Face)?.cache?.capability?.takeIf { it.heatCapacitorCount > 0 }?.let { return it }
        for (side in NearestFaces.of(ctx.target, ctx.worker.position())) {
            val handler = MekanismCompat.heatAt(ctx.level, ctx.target, side) ?: continue
            ctx.memo.value = Face(BlockCapabilityCache.create(MekanismCompat.HEAT, ctx.level, ctx.target, side))
            return handler
        }
        ctx.memo.value = null
        return null
    }

    private fun temperatures(handler: IHeatHandler): List<Double> = (0 until handler.heatCapacitorCount).map(handler::getTemperature)

    private fun exposesHeat(level: ServerLevel, pos: BlockPos): Boolean =
        Direction.entries.any { level.getCapability(MekanismCompat.HEAT, pos, it) != null }
}
