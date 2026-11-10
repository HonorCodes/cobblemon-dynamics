package codes.honor.cobblemondynamics.handler.capability

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * The Generator's target: any block whose NeoForge energy capability can receive on some face (CD-GEN-01). AE2's acceptors and
 * machines are such blocks, and AE2 turns the FE into AE at its own ratio (CD-GEN-07), so the handler names no AE2 class.
 */
object FePushHandler : TargetHandler {
    override val id: ResourceLocation = id("fe_push")

    /** FE machines have block entities. A block whose capability needs none can still be bound with the Work Leash. */
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        Direction.entries.any { level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, it)?.canReceive() == true }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        Direction.entries.any { receiving(level, canonical, it) != null }

    override fun hasDemand(ctx: WorkContext): Boolean {
        val port = port(ctx) ?: return false
        return FeTick.hasDemand(port, fePerTick(ctx))
    }

    override fun applyWork(ctx: WorkContext) {
        val port = port(ctx) ?: return
        FeTick.work(port, fePerTick(ctx))
    }

    private fun fePerTick(ctx: WorkContext): Int = GeneratorParams.of(ctx.role.params).fePerTick(ctx.pokemon.level, ctx.pokemon.form.labels)

    /** The face that a claim pushes through, in a cache that NeoForge clears when the block or its capabilities change (R41). */
    private class Face(val cache: BlockCapabilityCache<IEnergyStorage, Direction?>)

    /**
     * CD-GEN-04: the claim's face while it still receives, else the face nearest the worker that receives, which the claim then keeps.
     * AE2 machines take FE on their power sides only, and a charger or an inscriber has none at its front.
     */
    private fun port(ctx: WorkContext): EnergyPort? {
        (ctx.memo.value as? Face)?.cache?.capability?.takeIf { it.canReceive() }?.let { return EnergyPort.of(it) }
        for (side in NearestFaces.of(ctx.target, ctx.worker.position())) {
            val storage = receiving(ctx.level, ctx.target, side) ?: continue
            ctx.memo.value = Face(BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, ctx.level, ctx.target, side))
            return EnergyPort.of(storage)
        }
        ctx.memo.value = null
        return null
    }

    private fun receiving(level: ServerLevel, pos: BlockPos, side: Direction): IEnergyStorage? =
        level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side)?.takeIf { it.canReceive() }
}
