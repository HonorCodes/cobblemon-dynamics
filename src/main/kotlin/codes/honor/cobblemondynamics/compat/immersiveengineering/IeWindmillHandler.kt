package codes.honor.cobblemondynamics.compat.immersiveengineering

import blusunrize.immersiveengineering.common.blocks.wooden.WindmillBlockEntity
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Tailwind on the IE windmill: a weather bonus only, because IE caps a windmill at 8 sails (M8 of ADR-0020). The hook in
 * `mixin/compat/immersiveengineering` reads the worker's level from the windmill's synced attachment (CD-AIR-05, CD-AIR-06).
 */
object IeWindmillHandler : TargetHandler {
    override val id: ResourceLocation = id("ie_windmill")

    /** Leash only (CD-TLW-02). */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is WindmillBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is WindmillBlockEntity

    override fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean {
        val windmill = level.getBlockEntity(canonical) as? WindmillBlockEntity ?: return false
        return IeWindmillMath.clearOfSails(canonical, windmill.facing, spot)
    }

    /** The windmill turns, and its weather leaves room for a bonus. A thunderstorm already gives IE's top factor. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val windmill = windmillAt(ctx) ?: return false
        return windmill.turnSpeed > 0f && IeWindmillMath.weather(ctx.level.isRaining, ctx.level.isThundering) < 1.0
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** Renews the bonus on every Working tick. A new level reaches the clients, and a renewal of the same level sends nothing. */
    override fun applyWork(ctx: WorkContext) {
        val windmill = windmillAt(ctx) ?: return
        val type = IeAttachments.WINDMILL_BONUS.get()
        val level = ctx.pokemon.level
        val bonus = windmill.getExistingDataOrNull(type)
        if (bonus != null && bonus.level == level) {
            bonus.until = IeWindmillMath.until(ctx.now)
        } else {
            windmill.setData(type, WindmillBonus(level).also { it.until = IeWindmillMath.until(ctx.now) })
        }
    }

    override fun onStop(ctx: WorkContext) = removeBonus(ctx)

    /**
     * Every end of the claim ends the bonus too, also after a handler that threw, which skips [onStop]. IE asks for the turn speed only
     * while the windmill turns and has a dynamo, so the hook alone would leave a lapsed bonus on a still windmill (CD-AIR-05).
     */
    override fun onUnclaim(ctx: WorkContext) = removeBonus(ctx)

    /** The bonus ends at once, and its removal reaches the clients. In a chunk that is gone, the windmill and its bonus are gone too. */
    private fun removeBonus(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        val windmill = windmillAt(ctx) ?: return
        if (windmill.hasData(IeAttachments.WINDMILL_BONUS.get())) windmill.removeData(IeAttachments.WINDMILL_BONUS.get())
    }

    private fun windmillAt(ctx: WorkContext): WindmillBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? WindmillBlockEntity
}
