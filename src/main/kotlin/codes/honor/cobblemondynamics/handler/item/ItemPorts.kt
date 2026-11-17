package codes.honor.cobblemondynamics.handler.item

import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemHandlerHelper
import net.neoforged.neoforge.items.wrapper.InvWrapper
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper

/**
 * NeoForge's item handlers as the two ends of a batch. NeoForge 21.9 replaces `IItemHandler` and its wrappers, so only this file names
 * them.
 */
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
 * CD-MCH-10, CD-MCH-11: the items of a block entity that has no item capability, as a hopper reaches them. Cobblemon, CobbleFurnies,
 * Handcrafted and Valhelsia Furniture register none for their containers (RES11). A vanilla `Container` goes through `InvWrapper`, and
 * a `WorldlyContainer` through `SidedInvWrapper` on the face, so that its own face rules hold, such as the empty faces of Cobblemon's
 * fake Gilded Chest. Each take first asks the container's `canTakeItem`, as a hopper does and neither wrapper does (R90). A block of
 * `#cobblemon_dynamics:logistics/no_fallback` gets none: Lootr gives each player their own loot, and the pedestals of Legendary
 * Monuments are quest altars. Public for the GameTests, the only tests that can make an item stack.
 */
object ContainerFallback {
    /** The taker that `canTakeItem` gets where a hopper gives itself. Lazy, because the unit tests load this object with no game. */
    private val EMPTY_TARGET: Container by lazy { SimpleContainer(0) }

    /** Whether [blockEntity] takes the fallback: a vanilla `Container` that the deny tag does not hold ([denied]). */
    fun reaches(blockEntity: Any?, denied: Boolean): Boolean = !denied && blockEntity is Container

    /** The fallback's handler on [face]. A `WorldlyContainer` has none with no face, because its slots depend on the face. */
    fun handler(blockEntity: Any?, denied: Boolean, face: Direction?): IItemHandler? {
        if (!reaches(blockEntity, denied)) return null
        val container = blockEntity as Container
        if (container is WorldlyContainer) return face?.let { CheckedSidedInvWrapper(container, it) }
        return CheckedInvWrapper(container)
    }

    /**
     * Whether the fallback may take [stack] out of the container's own [slot], by the question that a hopper asks: Cobblemon's TM
     * Machine allows only its slot 0, and Visual Workbench none of its crafting grid. An empty slot has nothing to take.
     */
    internal fun mayTake(container: Container, slot: Int, stack: ItemStack): Boolean =
        !stack.isEmpty && container.canTakeItem(EMPTY_TARGET, slot, stack)
}

/** `InvWrapper` that takes nothing that the container's `canTakeItem` refuses (R90). */
private class CheckedInvWrapper(container: Container) : InvWrapper(container) {
    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack =
        if (ContainerFallback.mayTake(inv, slot, getStackInSlot(slot))) super.extractItem(slot, amount, simulate) else ItemStack.EMPTY
}

/** `SidedInvWrapper` that takes nothing that `canTakeItem` refuses, which it asks of the container's slot behind the face's [slot]. */
private class CheckedSidedInvWrapper(container: WorldlyContainer, face: Direction) : SidedInvWrapper(container, face) {
    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        val own = SidedInvWrapper.getSlot(inv, slot, side)
        val mayTake = own >= 0 && ContainerFallback.mayTake(inv, own, inv.getItem(own))
        return if (mayTake) super.extractItem(slot, amount, simulate) else ItemStack.EMPTY
    }
}

/**
 * One face of a container, in a cache that NeoForge clears when the block or its capabilities change. A claim keeps one for each end
 * of its binding, and a new binding on the same claim, with another block or face, needs a new one ([isAt]). A block entity with no
 * item capability goes through [ContainerFallback], whose handler the face keeps for that block entity until another block entity
 * stands at [pos], or NeoForge clears the cache (R90).
 */
internal class ItemFace(private val level: ServerLevel, val pos: BlockPos, val face: Direction?) {
    /** The block entity that [fallback] was made for, or null when there was none. */
    private var fallbackOf: BlockEntity? = null
    private var fallback: IItemHandler? = null

    private val cache: BlockCapabilityCache<IItemHandler, Direction?> =
        BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, pos, face, { true }) {
            fallbackOf = null
            fallback = null
        }

    fun isAt(pos: BlockPos, face: Direction?): Boolean = this.pos == pos && this.face == face

    fun source(): ItemSource<ItemStack>? = handler()?.let(ItemPorts::source)

    fun sink(): ItemSink<ItemStack>? = handler()?.let(ItemPorts::sink)

    private fun handler(): IItemHandler? = cache.capability ?: fallback()

    /** CD-MCH-10: the fallback of the block entity at [pos], made again only for another block entity or a removed one. No chunk loads. */
    private fun fallback(): IItemHandler? {
        if (!level.isLoaded(pos)) return null
        val blockEntity = level.getBlockEntity(pos)?.takeUnless { it.isRemoved }
        if (blockEntity !== fallbackOf) {
            fallbackOf = blockEntity
            fallback = blockEntity?.let { ItemCapability.fallback(level, pos, it, face) }
        }
        return fallback
    }
}

internal object ItemCapability {
    /** CD-MCH-11: the blocks that the fallback of CD-MCH-10 never reaches. */
    val NO_FALLBACK: TagKey<Block> = TagKey.create(Registries.BLOCK, id("logistics/no_fallback"))

    /** The faces to ask, unsided first. */
    private val FACES: List<Direction?> = listOf(null) + Direction.entries

    /**
     * CD-TLK-01, CD-MAG-01: an item handler with no side or on one of the six faces. AE2's cable parts answer only on their own face.
     * CD-MCH-10: else a vanilla `Container` outside the deny tag.
     */
    fun exposes(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        hasCapability(level, pos, state, blockEntity) || ContainerFallback.reaches(blockEntity, state.`is`(NO_FALLBACK))

    fun exposes(level: ServerLevel, pos: BlockPos): Boolean {
        if (FACES.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, it) != null }) return true
        if (!level.isLoaded(pos)) return false
        val blockEntity = level.getBlockEntity(pos) ?: return false
        return ContainerFallback.reaches(blockEntity, blockEntity.blockState.`is`(NO_FALLBACK))
    }

    /**
     * CD-MCH-10: the fallback handler of [blockEntity] at [pos] on [face], only while the block has no item capability on any face. A
     * block that answers on some faces only, as AE2's cable parts do, keeps its own choice.
     */
    fun fallback(level: ServerLevel, pos: BlockPos, blockEntity: BlockEntity, face: Direction?): IItemHandler? {
        val state = blockEntity.blockState
        if (hasCapability(level, pos, state, blockEntity)) return null
        return ContainerFallback.handler(blockEntity, state.`is`(NO_FALLBACK), face)
    }

    private fun hasCapability(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        FACES.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, it) != null }

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
