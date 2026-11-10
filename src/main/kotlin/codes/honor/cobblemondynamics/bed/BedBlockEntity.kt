package codes.honor.cobblemondynamics.bed

import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** A bed's block entity. It saves nothing: it exists so that a scan finds the bed in the chunk's block entity map. */
class BedBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(DynamicsBlocks.BED.get(), pos, state) {
    /** The tier of the bed block that this entity belongs to. */
    val tier: BedTier = (state.block as? BedBlock)?.tier ?: BedTier.HAY
}
