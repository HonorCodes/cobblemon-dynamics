package codes.honor.cobblemondynamics.compat.ars_nouveau

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.hollingsworth.arsnouveau.common.block.tile.VolcanicSourcelinkTile
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker's target in Ars Nouveau: the Volcanic Sourcelink, which turns burned fuel into Source (CD-FUEL-12). Free fuel would make free
 * Source, so the worker is one fuel stream: the Source of one coal for each 1,600 ticks of Working, through the sourcelink's public
 * `addSource`. The fuel items on the pedestals around it keep their own rules.
 */
object VolcanicSourcelinkHandler : TargetHandler {
    override val id: ResourceLocation = id("volcanic_sourcelink")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /** The Working ticks of this claim toward the next gift, kept through the claim's stretches of Standby. */
    private class Memo(var worked: Int = 0)

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is VolcanicSourcelinkTile

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is VolcanicSourcelinkTile

    override fun hasDemand(ctx: WorkContext): Boolean =
        linkAt(ctx)?.let { SourcelinkRule.hasDemand(canAcceptSource = it.canAcceptSource(), disabled = it.isDisabled) } ?: false

    override fun applyWork(ctx: WorkContext) {
        val link = linkAt(ctx) ?: return
        val memo = ctx.memo.value as? Memo ?: Memo().also { ctx.memo.value = it }
        val step = SourcelinkRule.step(memo.worked, SourcelinkRule.intervalOf(ctx.role.params))
        memo.worked = step.worked
        val source = SourcelinkRule.gift(step) { link.getSourceValue(ItemStack(Items.COAL)) }
        if (source <= 0) return
        link.addSource(source)
        link.setChanged()
    }

    private fun linkAt(ctx: WorkContext): VolcanicSourcelinkTile? = ctx.level.getBlockEntity(ctx.target) as? VolcanicSourcelinkTile
}
