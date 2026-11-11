package codes.honor.cobblemondynamics.handler.item

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemHandlerHelper

/** NeoForge's item handlers as the two ends of a batch. NeoForge 21.9 replaces `IItemHandler`, so only this file names it. */
internal object ItemPorts {
    fun source(handler: IItemHandler): ItemSource<ItemStack> = object : ItemSource<ItemStack> {
        override val slotCount: Int get() = handler.slots

        override fun peek(slot: Int, max: Int): Lot<ItemStack>? = handler.extractItem(slot, max, true).toLot()

        override fun take(slot: Int, count: Int): Lot<ItemStack>? = handler.extractItem(slot, count, false).toLot()

        override fun putBack(slot: Int, lot: Lot<ItemStack>): Int = handler.insertItem(slot, lot.stack(), false).count
    }

    /** Inserts a copy every time, because Create's chute and depot keep the stack object that they get (CD-TLK-07). */
    fun sink(handler: IItemHandler): ItemSink<ItemStack> = object : ItemSink<ItemStack> {
        override fun accept(lot: Lot<ItemStack>, simulate: Boolean): Int =
            lot.count - ItemHandlerHelper.insertItemStacked(handler, lot.stack(), simulate).count
    }

    /** A new stack of [Lot.count] items: no container ever gets an object that another one holds. */
    fun Lot<ItemStack>.stack(): ItemStack = item.copyWithCount(count)

    private fun ItemStack.toLot(): Lot<ItemStack>? = if (isEmpty) null else Lot(copy(), count)
}

/**
 * One face of a container, in a cache that NeoForge clears when the block or its capabilities change. A claim keeps one for each end
 * of its binding, and a new binding on the same claim, with another block or face, needs a new one ([isAt]).
 */
internal class ItemFace(level: ServerLevel, val pos: BlockPos, val face: Direction?) {
    private val cache: BlockCapabilityCache<IItemHandler, Direction?> =
        BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, pos, face)

    fun isAt(pos: BlockPos, face: Direction?): Boolean = this.pos == pos && this.face == face

    fun source(): ItemSource<ItemStack>? = cache.capability?.let(ItemPorts::source)

    fun sink(): ItemSink<ItemStack>? = cache.capability?.let(ItemPorts::sink)
}

internal object ItemCapability {
    /** The faces to ask, unsided first. */
    private val FACES: List<Direction?> = listOf(null) + Direction.entries

    /** CD-TLK-01, CD-MAG-01: an item handler with no side or on one of the six faces. AE2's cable parts answer only on their own face. */
    fun exposes(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        FACES.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, it) != null }

    fun exposes(level: ServerLevel, pos: BlockPos): Boolean =
        FACES.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, it) != null }

    /**
     * The canonical position of the inventory at [pos] (ruling 5 of the Phase 2 plan): the least of its blocks. Two blocks are one
     * inventory when they are the halves of a double chest, or when both give the same handler object, as every block of a Create
     * item vault gives its controller's.
     */
    fun canonical(level: ServerLevel, pos: BlockPos): BlockPos {
        val handler = unsided(level, pos)
        return InventoryBlocks.canonical(pos) { from, to ->
            chestPartner(level, from) == to || (handler != null && unsided(level, to) === handler)
        }
    }

    /** Whether [pos] is a block of the inventory whose canonical position is [canonical], with no search. */
    fun isPart(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean {
        if (pos == canonical) return true
        if (!level.isLoaded(pos)) return false
        if (chestPartner(level, canonical) == pos) return true
        val handler = unsided(level, canonical) ?: return false
        return unsided(level, pos) === handler
    }

    /** The unsided item handler of [pos], when it has a slot. An unloaded block has none, and no chunk loads. */
    private fun unsided(level: ServerLevel, pos: BlockPos): IItemHandler? {
        if (!level.isLoaded(pos)) return null
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)?.takeIf { it.slots > 0 }
    }

    /** The other half of the double chest at [pos], where vanilla joins the two into one container, or null. */
    private fun chestPartner(level: ServerLevel, pos: BlockPos): BlockPos? {
        if (!level.isLoaded(pos)) return null
        val state = level.getBlockState(pos)
        if (state.block !is ChestBlock || !state.hasProperty(ChestBlock.TYPE) || !state.hasProperty(ChestBlock.FACING)) return null
        val type = state.getValue(ChestBlock.TYPE)
        if (type == ChestType.SINGLE) return null
        val other = pos.relative(ChestBlock.getConnectedDirection(state))
        if (!level.isLoaded(other)) return null
        val partner = level.getBlockState(other)
        val joined = partner.`is`(state.block) &&
            partner.getValue(ChestBlock.TYPE) == type.opposite &&
            partner.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
        return other.takeIf { joined }
    }
}
