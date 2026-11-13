package codes.honor.cobblemondynamics.handler.farming

import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.BerryBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.AttachedStemBlock
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.CaveVines
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.state.properties.Property

/**
 * Reads [CropFacts] from a level (ADR-0015): class flags through vanilla's and Cobblemon's classes, the adapter tags, and the block
 * state properties `age`, `half` and `berries`. It names no class of a crop mod (CD-CRP-19).
 */
object CropFactsReader {
    /** The facts of [state] at [pos]. For a two-block crop, [pos] is the lower half, which the farming handlers key by. */
    fun read(level: BlockGetter, pos: BlockPos, state: BlockState): CropFacts {
        val block = state.block
        val age = ageProperty(state)
        val ages = age?.possibleValues
        val half = half(state)
        return CropFacts(
            adapterTags = FarmingTags.ADAPTERS.filterValues { state.`is`(it) }.keys,
            apricorn = block is ApricornBlock || state.`is`(FarmingTags.APRICORNS),
            cropBlock = block is CropBlock,
            bonemealable = block is BonemealableBlock,
            berryTree = block is BerryBlock,
            caveVines = block is CaveVines,
            attachedStem = block is AttachedStemBlock,
            age = age?.let { state.getValue(it) },
            ageMin = ages?.min() ?: 0,
            ageMax = ages?.max() ?: 0,
            cropMaxAge = (block as? CropBlock)?.isMaxAge(state) == true,
            upperHalf = half == DoubleBlockHalf.UPPER,
            upperPresent = half == DoubleBlockHalf.LOWER && level.getBlockState(pos.above()).let { it.block === block && isUpper(it) },
            berries = state.getOptionalValue(BlockStateProperties.BERRIES).orElse(false),
        )
    }

    /** The `IntegerProperty` named `age`, which the crops of every mod of RES8 use. `CropBlock.getAgeProperty` is protected. */
    fun ageProperty(state: BlockState): IntegerProperty? =
        state.properties.firstOrNull { it is IntegerProperty && it.name == AGE } as? IntegerProperty

    /** Whether [state] is the upper block of a two-block crop. */
    fun isUpper(state: BlockState): Boolean = half(state) == DoubleBlockHalf.UPPER

    /**
     * The value of the state's `half` property of [DoubleBlockHalf] values, found by name, because a mod may declare its own property
     * where vanilla blocks use `BlockStateProperties.DOUBLE_BLOCK_HALF`.
     */
    private fun half(state: BlockState): DoubleBlockHalf? {
        val property = state.properties.firstOrNull { it.name == HALF && it.valueClass == DoubleBlockHalf::class.java } ?: return null
        @Suppress("UNCHECKED_CAST")
        return state.getValue(property as Property<DoubleBlockHalf>)
    }

    private const val AGE = "age"
    private const val HALF = "half"
}
