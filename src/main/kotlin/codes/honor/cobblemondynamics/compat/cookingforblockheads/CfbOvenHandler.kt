package codes.honor.cobblemondynamics.compat.cookingforblockheads

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker's target in Cooking for Blockheads: the Oven of every color (CD-FUEL-09). The worker keeps the oven's public burn fields
 * up while a slot cooks, so its 9 slots cook at the oven's own rate and its fuel slot waits (CD-FUEL-13).
 */
object CfbOvenHandler : TargetHandler {
    override val id: ResourceLocation = id("cfb_oven")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is OvenBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is OvenBlockEntity

    override fun hasDemand(ctx: WorkContext): Boolean = ovenAt(ctx)?.let(::cooks) ?: false

    override fun applyWork(ctx: WorkContext) {
        val oven = ovenAt(ctx) ?: return
        val now = OvenBurn(oven.furnaceBurnTime, oven.currentItemBurnTime)
        val next = CfbOvenRule.burn(now, cooks(oven))
        if (next == now) return
        oven.furnaceBurnTime = next.burnTime
        oven.currentItemBurnTime = next.itemBurnTime
        oven.setChanged()
    }

    private fun ovenAt(ctx: WorkContext): OvenBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? OvenBlockEntity

    private fun cooks(oven: OvenBlockEntity): Boolean {
        val container = oven.internalContainer
        val filled = List(CfbOvenRule.PROCESSING_SLOTS) { !container.getItem(CfbOvenRule.FIRST_PROCESSING_SLOT + it).isEmpty }
        return CfbOvenRule.cooks(filled, oven.slotCookTime.toList())
    }
}
