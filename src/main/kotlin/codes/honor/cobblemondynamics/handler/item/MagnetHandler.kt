package codes.honor.cobblemondynamics.handler.item

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Containers
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * The Magnet: a Steel-type collects dropped items from its pasture's whole work area into its bound container, in one batch every 20
 * ticks (R52). It looks for items at most once every 20 ticks (CD-MAG-08) and keeps what it found in the claim's memo.
 */
object MagnetHandler : TargetHandler {
    override val id: ResourceLocation = id("magnet")

    /** Leash only (CD-MAG-02, CD-ASG-05), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE

    /** CD-MAG-09: other Magnets and Telekinesis workers share the container. */
    override val sharesTargets: Boolean = true

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        ItemCapability.exposes(level, pos, state, blockEntity)

    /** A double chest or a Create item vault is one container, as for Telekinesis (ruling 5 of the Phase 2 plan). */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = ItemCapability.canonical(level, pos)

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        ItemCapability.isPart(level, canonical, pos)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = ItemCapability.exposes(level, canonical)

    /** CD-MAG-07: a dropped item in the work area that the container takes. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if (itemsPerBatch(ctx) <= 0) return false
        val sink = sink(ctx) ?: return false
        val area = area(ctx) ?: return false
        return ItemBatch.canMove(DroppedItems(drops(ctx, area), area), sink)
    }

    override fun onStart(ctx: WorkContext) {
        memo(ctx).startedAt = ctx.now
    }

    /** CD-MAG-03, CD-MAG-06: a batch on the first Working tick and every 20 ticks after it. What the container refuses stays put. */
    override fun applyWork(ctx: WorkContext) {
        if (!TickClock.every(TransferParams.BATCH_TICKS, memo(ctx).startedAt, ctx.now)) return
        val sink = sink(ctx) ?: return
        val area = area(ctx) ?: return
        ItemBatch.move(DroppedItems(drops(ctx, area), area), sink, itemsPerBatch(ctx)) { lot -> spill(ctx, lot) }
    }

    private fun itemsPerBatch(ctx: WorkContext): Int = TransferParams.of(ctx.role.params).itemsPerBatch(ctx.pokemon.level)

    /** The claim's batch start, its query clock with what the last query found, and the face of its container. */
    private class Memo(var startedAt: Long) {
        val clock = QueryClock()
        var found: List<ItemEntity> = emptyList()
        var face: ItemFace? = null
    }

    private fun memo(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }

    /** CD-MAG-04: the work area of the worker's own pasture. */
    private fun area(ctx: WorkContext): WorkArea? =
        ctx.pasture?.let { WorkArea.of(it, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()) }

    /** CD-MAG-08: one query of the work area, at most once every 20 ticks. */
    private fun drops(ctx: WorkContext, area: WorkArea): List<ItemEntity> {
        val memo = memo(ctx)
        if (memo.clock.isDue(ctx.now)) {
            memo.found = DroppedItems.query(ctx.level, area)
            memo.clock.ran(ctx.now)
        }
        return memo.found
    }

    /** Through the face that the player clicked. A rebind to the same container keeps the claim, so the face is checked again. */
    private fun sink(ctx: WorkContext): ItemSink<ItemStack>? {
        val memo = memo(ctx)
        val face = memo.face?.takeIf { it.isAt(ctx.target, ctx.sourceFace) }
            ?: ItemFace(ctx.level, ctx.target, ctx.sourceFace).also { memo.face = it }
        return face.sink()
    }

    /** [DroppedItems] puts every refused item back where it lay, so nothing reaches this drop. It keeps the batch's no-loss rule. */
    private fun spill(ctx: WorkContext, lot: Lot<ItemStack>) {
        val at = Vec3.atCenterOf(ctx.target)
        Containers.dropItemStack(ctx.level, at.x, at.y + 0.6, at.z, lot.item.copyWithCount(lot.count))
    }
}
