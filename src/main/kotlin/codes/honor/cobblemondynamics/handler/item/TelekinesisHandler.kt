package codes.honor.cobblemondynamics.handler.item

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleDefinition
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * Telekinesis: a Psychic-type moves items from its bound source to its bound destination, through the faces that the player clicked
 * (CD-TLK-04), in one batch every 20 ticks (R52). Only the source is claimed (CD-TLK-05). The engine gives it the destination and both
 * faces through the work context.
 */
object TelekinesisHandler : TargetHandler {
    override val id: ResourceLocation = id("telekinesis")

    /** Leash only (CD-TLK-02, CD-ASG-05), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE
    override val bindingShape: BindingShape = BindingShape.SOURCE_AND_DESTINATION

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        ItemCapability.exposes(level, pos, state, blockEntity)

    /** A double chest or a Create item vault is one source or destination (ruling 5 of the Phase 2 plan). */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = ItemCapability.canonical(level, pos)

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        ItemCapability.isPart(level, canonical, pos)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = ItemCapability.exposes(level, canonical)

    override fun destinationInReach(role: RoleDefinition, pasture: BlockPos, destination: BlockPos): Boolean =
        TransferParams.of(role.params).inReach(pasture, destination)

    /** CD-TLK-06: the source holds an item that the destination takes. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if (itemsPerBatch(ctx) <= 0) return false
        val (source, sink) = ports(ctx) ?: return false
        return ItemBatch.canMove(source, sink)
    }

    override fun onStart(ctx: WorkContext) {
        memo(ctx).startedAt = ctx.now
    }

    /** CD-TLK-03, CD-TLK-07: a batch on the first Working tick and every 20 ticks after it. */
    override fun applyWork(ctx: WorkContext) {
        if (!TickClock.every(TransferParams.BATCH_TICKS, memo(ctx).startedAt, ctx.now)) return
        val (source, sink) = ports(ctx) ?: return
        ItemBatch.move(source, sink, itemsPerBatch(ctx)) { lot -> spill(ctx, lot) }
    }

    private fun itemsPerBatch(ctx: WorkContext): Int = TransferParams.of(ctx.role.params).itemsPerBatch(ctx.pokemon.level)

    /** The claim's batch start and the faces of its two ends. */
    private class Memo(var startedAt: Long) {
        var source: ItemFace? = null
        var destination: ItemFace? = null
    }

    private fun memo(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }

    /**
     * The two ends of the binding. A rebind that keeps the source keeps the claim and its memo, so each face is checked against the
     * binding. A destination that is not loaded takes nothing.
     */
    private fun ports(ctx: WorkContext): Pair<ItemSource<ItemStack>, ItemSink<ItemStack>>? {
        val destination = ctx.destination ?: return null
        if (!ctx.level.isLoaded(destination)) return null
        val memo = memo(ctx)
        val from = memo.source?.takeIf { it.isAt(ctx.target, ctx.sourceFace) }
            ?: ItemFace(ctx.level, ctx.target, ctx.sourceFace).also { memo.source = it }
        val to = memo.destination?.takeIf { it.isAt(destination, ctx.destinationFace) }
            ?: ItemFace(ctx.level, destination, ctx.destinationFace).also { memo.destination = it }
        val source = from.source() ?: return null
        val sink = to.sink() ?: return null
        return source to sink
    }

    /** What fits neither the destination nor its own slot drops at the source, so no item is lost (CD-TLK-07). */
    private fun spill(ctx: WorkContext, lot: Lot<ItemStack>) {
        val at = Vec3.atCenterOf(ctx.target)
        Containers.dropItemStack(ctx.level, at.x, at.y + 0.6, at.z, lot.item.copyWithCount(lot.count))
    }
}
