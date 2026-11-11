package codes.honor.cobblemondynamics.handler.item

import codes.honor.cobblemondynamics.engine.WorkArea
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * The dropped items of a work area as an [ItemSource]: one slot for each item entity of the last query (CD-MAG-04, CD-MAG-05). An
 * item that a player or another worker took since then, or that left the area, gives nothing.
 */
internal class DroppedItems(private val entities: List<ItemEntity>, private val area: WorkArea) : ItemSource<ItemStack> {
    override val slotCount: Int get() = entities.size

    override fun peek(slot: Int, max: Int): Lot<ItemStack>? =
        entities[slot].takeIf { collectable(it, area) }?.item?.let { Lot(it.copy(), minOf(max, it.count)) }

    override fun take(slot: Int, count: Int): Lot<ItemStack>? {
        val entity = entities[slot].takeIf { collectable(it, area) } ?: return null
        val stack = entity.item
        val n = minOf(count, stack.count)
        val taken = stack.copyWithCount(n)
        if (n == stack.count) entity.discard() else entity.item = stack.copyWithCount(stack.count - n)
        return Lot(taken, n)
    }

    /** A batch puts back only what it took from the same entity, so the stack never grows past its count before the batch. */
    override fun putBack(slot: Int, lot: Lot<ItemStack>): Int {
        val entity = entities[slot]
        if (entity.isAlive) {
            entity.item = entity.item.copyWithCount(entity.item.count + lot.count)
        } else {
            val level = entity.level()
            level.addFreshEntity(ItemEntity(level, entity.x, entity.y, entity.z, lot.item.copyWithCount(lot.count), 0.0, 0.0, 0.0))
        }
        return 0
    }

    companion object {
        /** An item that a player may not pick up yet keeps its delay, and the Magnet waits too (CD-MAG-05). */
        fun collectable(entity: ItemEntity, area: WorkArea): Boolean =
            entity.isAlive && !entity.hasPickUpDelay() && !entity.item.isEmpty && area.contains(entity.blockPosition())

        fun query(level: ServerLevel, area: WorkArea): List<ItemEntity> =
            level.getEntitiesOfClass(ItemEntity::class.java, area.box()) { collectable(it, area) }
    }
}
