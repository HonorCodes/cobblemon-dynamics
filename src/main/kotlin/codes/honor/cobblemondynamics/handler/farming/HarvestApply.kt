package codes.honor.cobblemondynamics.handler.farming

import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.AttachedStemBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property

/**
 * Applies a harvest to the level (ADR-0015). The loot comes from `Block.getDrops` with an empty tool and no entity, so the pack's loot
 * tables decide. A harvest posts no level event, plays no sound, runs no block's own pick and posts no break event (CD-CRP-14): it sets
 * each cell with `Level.setBlock` and drops each item through `Block.popResourceFromFace`.
 */
object HarvestApply {
    /**
     * Harvests the crop at [crop] once. Its items come out of the crop's face toward [spot] (CD-HRV-06). [mayChange] answers the land
     * claim checks of each cell that the harvest changes, in this tick (CD-HRV-13), and [mayBreak] the break question of each cell that
     * it breaks (CD-HRV-16). One refusal leaves every cell and item as it is. Returns whether the harvest ran.
     */
    fun apply(
        level: ServerLevel,
        crop: BlockPos,
        kind: HarvestKind,
        facts: CropFacts,
        spot: BlockPos,
        mayChange: (BlockPos) -> Boolean,
        mayBreak: (BlockPos) -> Boolean,
    ): Boolean {
        val state = level.getBlockState(crop)
        val face = DropFace.toward(crop, spot)
        if (kind == HarvestKind.BERRY_TREE) return harvestTree(level, crop, state, face, mayChange)
        val upper = crop.above()
        val fruit = if (kind == HarvestKind.GOURD) crop.relative(state.getValue(AttachedStemBlock.FACING)) else null
        // The crop adapter takes a two-block crop whole, such as flax. The upper half adapter takes the upper block only.
        val whole = facts.upperPresent && (kind == HarvestKind.CROP || kind == HarvestKind.BREAK_AND_REPLANT)
        val looted = when (kind) {
            HarvestKind.UPPER_HALF -> listOf(upper)
            HarvestKind.GOURD -> listOfNotNull(fruit)
            else -> if (whole) listOf(crop, upper) else listOf(crop)
        }
        val stacks = looted.flatMap { pos ->
            Block.getDrops(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, ItemStack.EMPTY)
        }.filterNot { it.isEmpty }
        val plan = HarvestPlans.plan(kind, stacks.map { Drop(idOf(it), it.count) }, ownSeeds(stacks, state), seeds(stacks, state), whole) {
            replantState(it?.let(BuiltInRegistries.BLOCK::get) ?: state.block, state).canSurvive(level, crop)
        }
        val changed = listOfNotNull(
            crop.takeIf { plan.crop != CellChange.Keep },
            upper.takeIf { plan.upper != CellChange.Keep },
            fruit?.takeIf { plan.fruit != CellChange.Keep },
        )
        val cells = mapOf(HarvestCell.CROP to crop, HarvestCell.UPPER to upper, HarvestCell.FRUIT to fruit)
        if (!changed.all(mayChange) || !plan.broken().mapNotNull(cells::get).all(mayBreak)) return false
        if (plan.upper == CellChange.Clear) {
            // ADR-0015 decision 4: the crop adapter clears the upper half with no shape update, so that the lower half does not break
            // itself with its own drops before its replant. The upper half adapter leaves the lower half to its own rule, which a
            // shape update runs: hearty grains set themselves to age 3.
            val flags = if (kind == HarvestKind.UPPER_HALF) Block.UPDATE_ALL else Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE
            clear(level, upper, flags)
        }
        changeCrop(level, crop, state, plan.crop)
        if (fruit != null && plan.fruit == CellChange.Clear) clear(level, fruit, Block.UPDATE_ALL)
        drop(level, crop, face, stacks, plan.seed)
        return true
    }

    /** CD-HRV-11: Cobblemon's own harvest of a berry tree, which sets it back to age 3 with no player and no sound. */
    private fun harvestTree(
        level: ServerLevel,
        crop: BlockPos,
        state: BlockState,
        face: Direction,
        mayChange: (BlockPos) -> Boolean,
    ): Boolean {
        val tree = level.getBlockEntity(crop) as? BerryBlockEntity ?: return false
        if (!mayChange(crop)) return false
        for (stack in tree.harvest(level, state, crop)) if (!stack.isEmpty) Block.popResourceFromFace(level, crop, face, stack)
        return true
    }

    private fun changeCrop(level: ServerLevel, crop: BlockPos, state: BlockState, change: CellChange) {
        when (change) {
            CellChange.Keep -> {}
            CellChange.Clear -> clear(level, crop, Block.UPDATE_ALL)
            is CellChange.SetAge -> level.setBlock(crop, withAge(state, change.age), Block.UPDATE_ALL)
            CellChange.ClearBerries -> level.setBlock(crop, state.trySetValue(BlockStateProperties.BERRIES, false), Block.UPDATE_ALL)
            CellChange.Replant -> broken(level, crop, state) { replantState(state.block, state) }
            is CellChange.ReplantAs -> broken(level, crop, state) { replantState(BuiltInRegistries.BLOCK.get(change.block), state) }
        }
    }

    /** A broken cell takes [next], and the old state runs its break effects with no tool, as `Block.dropResources` runs them. */
    private fun broken(level: ServerLevel, pos: BlockPos, old: BlockState, next: () -> BlockState) {
        level.setBlock(pos, next(), Block.UPDATE_ALL)
        old.spawnAfterBreak(level, pos, ItemStack.EMPTY, true)
    }

    /** CD-HRV-08: the cell goes to its fluid, air or water. */
    private fun clear(level: ServerLevel, pos: BlockPos, flags: Int) {
        val old = level.getBlockState(pos)
        level.setBlock(pos, old.fluidState.createLegacyBlock(), flags)
        old.spawnAfterBreak(level, pos, ItemStack.EMPTY, true)
    }

    /**
     * CD-HRV-07: the first growth state of [block] in the harvested cell. A `CropBlock` gives `getStateForAge(0)`. Another block gives
     * its state with the lowest `age`, which keeps the values of the properties that it shares with [harvested], such as the facing of
     * cocoa.
     */
    private fun replantState(block: Block, harvested: BlockState): BlockState {
        if (block is CropBlock) return block.getStateForAge(0)
        val base = block.defaultBlockState()
        val wanted = HashMap<Property<*>, Comparable<*>>()
        for ((property, value) in harvested.values) if (base.hasProperty(property)) wanted[property] = value
        CropFactsReader.ageProperty(base)?.let { wanted[it] = it.possibleValues.min() }
        return block.stateDefinition.possibleStates.firstOrNull { candidate -> wanted.all { (p, v) -> candidate.values[p] == v } } ?: base
    }

    /** CD-HRV-09: a pick's state, with the other properties kept. A value outside the `age` property takes its nearest end. */
    private fun withAge(state: BlockState, age: Int): BlockState {
        val property = CropFactsReader.ageProperty(state) ?: return state
        return state.setValue(property, age.coerceIn(property.possibleValues.min(), property.possibleValues.max()))
    }

    /** The dropped items whose `BlockItem` places the crop's own block, such as wheat seeds or a Pam's crop item. */
    private fun ownSeeds(stacks: List<ItemStack>, crop: BlockState): Set<ResourceLocation> =
        stacks.filter { (it.item as? BlockItem)?.block === crop.block }.mapTo(HashSet(), ::idOf)

    /** Each dropped item that places another block of the Cultivator's tag, such as the Aether's stem, to that block. */
    private fun seeds(stacks: List<ItemStack>, crop: BlockState): Map<ResourceLocation, ResourceLocation> = stacks.mapNotNull { stack ->
        val block = (stack.item as? BlockItem)?.block ?: return@mapNotNull null
        if (block === crop.block || !block.defaultBlockState().`is`(FarmingTags.CULTIVATOR_TARGETS)) return@mapNotNull null
        idOf(stack) to BuiltInRegistries.BLOCK.getKey(block)
    }.toMap()

    /** CD-HRV-06: every stack on the crop's face, less the one item of the replant, which leaves the first stack of its kind. */
    private fun drop(level: ServerLevel, crop: BlockPos, face: Direction, stacks: List<ItemStack>, seed: ResourceLocation?) {
        var planted = seed == null
        for (stack in stacks) {
            val out = if (!planted && idOf(stack) == seed) stack.copyWithCount(stack.count - 1).also { planted = true } else stack
            if (!out.isEmpty) Block.popResourceFromFace(level, crop, face, out)
        }
    }

    private fun idOf(stack: ItemStack): ResourceLocation = BuiltInRegistries.ITEM.getKey(stack.item)
}
