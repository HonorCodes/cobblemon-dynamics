package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState

/** The cells of one crop: a two-block crop is keyed by its lower half, which both farming handlers read (ADR-0015). */
internal object FarmCells {
    /** The lower half of a two-block crop for its upper half at [pos], or [pos] itself. */
    fun lower(level: BlockGetter, pos: BlockPos, state: BlockState): BlockPos {
        if (!CropFactsReader.isUpper(state)) return pos
        val below = pos.below()
        return if (level.getBlockState(below).block === state.block) below else pos
    }

    /** The crop at [canonical] and its upper half, if [pos] holds that half. */
    fun isPart(level: BlockGetter, canonical: BlockPos, pos: BlockPos): Boolean {
        if (pos == canonical) return true
        if (pos != canonical.above()) return false
        val state = level.getBlockState(pos)
        return state.block === level.getBlockState(canonical).block && CropFactsReader.isUpper(state)
    }

    /** The states of the crop's cell and the cell above it, where an upper half or a rice panicle grows (CD-CUL-09). */
    fun states(level: BlockGetter, crop: BlockPos): List<BlockState> = listOf(level.getBlockState(crop), level.getBlockState(crop.above()))
}
