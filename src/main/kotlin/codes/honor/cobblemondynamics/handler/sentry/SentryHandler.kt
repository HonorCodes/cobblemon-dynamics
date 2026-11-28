package codes.honor.cobblemondynamics.handler.sentry

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.PastureKey
import com.cobblemon.mod.common.block.PastureBlock
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

object SentryHandler : TargetHandler {
    override val id: ResourceLocation = id("sentry")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY
    override val releasesWhenDone: Boolean = true

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is PokemonPastureBlockEntity && PastureAdapter.isWorkMode(blockEntity)

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || (pos == canonical.above() && level.getBlockState(pos).block is PastureBlock)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        PastureAdapter.pastureAt(level, canonical)?.let(PastureAdapter::isWorkMode) == true

    override fun hasDemand(ctx: WorkContext): Boolean {
        if (ctx.pasture != ctx.target || ctx.area == null) return false
        val pasture = PastureAdapter.pastureAt(ctx.level, ctx.target) ?: return false
        return PastureAdapter.isWorkMode(pasture) &&
            PastureAdapter.isTethered(ctx.pokemon) &&
            PastureAdapter.tetheredPokemon(pasture, ctx.pokemon.uuid) != null
    }

    override fun applyWork(ctx: WorkContext) {
        val pasture = ctx.pasture ?: return
        val area = ctx.area ?: return
        if (pasture != ctx.target) return
        SentryWatch.watch(PastureKey(ctx.level.dimension().location(), pasture.asLong()), area, ctx.now)
    }

    override fun onStop(ctx: WorkContext) {
        clearWatch(ctx)
    }

    override fun onUnclaim(ctx: WorkContext) {
        clearWatch(ctx)
    }

    private fun clearWatch(ctx: WorkContext) {
        val pasture = ctx.pasture ?: ctx.target
        SentryWatch.unwatch(PastureKey(ctx.level.dimension().location(), pasture.asLong()))
    }
}
