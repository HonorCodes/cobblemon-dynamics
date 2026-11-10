package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Muse's target: a vanilla enchanting table (CD-MUS-01). A Working Muse adds its bookshelves through [MuseBonus], which the
 * enchanting-table mixin reads when the table's menu computes its offers, so the handler has no work of its own on each tick.
 */
object EnchantingHandler : TargetHandler {
    override val id: ResourceLocation = id("enchanting")

    /** The Muse is leash only (CD-MUS-02), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        state.`is`(Blocks.ENCHANTING_TABLE)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockState(canonical).`is`(Blocks.ENCHANTING_TABLE)

    /** CD-MUS-05: only while a player has this table's menu open. */
    override fun hasDemand(ctx: WorkContext): Boolean = EnchantingMenus.isOpenAt(ctx.level, ctx.target)

    /** The open menus show the Muse's offers at once, also when the player put the item in before the Muse began. */
    override fun onStart(ctx: WorkContext) = EnchantingMenus.refresh(ctx.level, ctx.target)

    override fun applyWork(ctx: WorkContext) {}

    /** The engine has ended the Working state or the claim by now, so the offers drop back to the table's own bookshelves. */
    override fun onStop(ctx: WorkContext) = EnchantingMenus.refresh(ctx.level, ctx.target)
}
