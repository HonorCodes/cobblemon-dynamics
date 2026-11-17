package codes.honor.cobblemondynamics.compat.pneumaticcraft

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.capability.NearestFaces
import codes.honor.cobblemondynamics.id
import me.desht.pneumaticcraft.api.PNCCapabilities
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.BlockCapabilityCache

/**
 * Tailwind on PneumaticCraft air: a Flying-type blows air into a machine through the air handler of PneumaticCraft's API (CD-AIR-07).
 * PneumaticCraft's `addAir` has no pressure guard, and a machine above its danger pressure can explode, so the worker adds only the air
 * that keeps the pressure below the danger pressure (CD-AIR-08).
 */
object PncAirHandler : TargetHandler {
    private val AIR = PNCCapabilities.AIR_HANDLER_MACHINE

    override val id: ResourceLocation = id("pnc_air")

    /** Leash only (ADR-0020), as Tailwind on a Create windmill. */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity != null && Direction.entries.any { level.getCapability(AIR, pos, state, blockEntity, it) != null }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        Direction.entries.any { PncCompat.airAt(level, canonical, it) != null }

    /** CD-AIR-08: while some air still fits below the danger pressure. */
    override fun hasDemand(ctx: WorkContext): Boolean = handler(ctx)?.let { air(ctx, it) > 0 } ?: false

    override fun applyWork(ctx: WorkContext) {
        val handler = handler(ctx) ?: return
        val air = air(ctx, handler)
        if (air <= 0) return
        handler.addAir(air)
        ctx.level.getBlockEntity(ctx.target)?.setChanged()
    }

    private fun air(ctx: WorkContext, handler: IAirHandlerMachine): Int {
        val perTick = PncAirMath.airPerTick(PncAirParams.of(ctx.role.params).airPerTickAt100, ctx.pokemon.level)
        return PncAirMath.allowed(perTick, handler.air, handler.volume, handler.dangerPressure)
    }

    /** The face that a claim blows through, in a cache that NeoForge clears when the block or its capabilities change. */
    private class Face(val cache: BlockCapabilityCache<IAirHandlerMachine, Direction?>)

    /** The claim's face while it still has an air handler, else the face nearest the worker that has one. */
    private fun handler(ctx: WorkContext): IAirHandlerMachine? {
        (ctx.memo.value as? Face)?.cache?.capability?.let { return it }
        for (side in NearestFaces.of(ctx.target, ctx.worker.position())) {
            val handler = PncCompat.airAt(ctx.level, ctx.target, side) ?: continue
            ctx.memo.value = Face(BlockCapabilityCache.create(AIR, ctx.level, ctx.target, side))
            return handler
        }
        ctx.memo.value = null
        return null
    }
}
