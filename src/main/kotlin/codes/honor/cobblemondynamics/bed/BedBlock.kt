package codes.honor.cobblemondynamics.bed

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A bed of one tier: a low block, 6/16 high, that a Pokémon of any size lies on. Its block entity lets a scan find it in the
 * chunk's block entity map, so no scan iterates block positions for beds.
 */
class BedBlock(val tier: BedTier, properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {
    override fun codec(): MapCodec<BedBlock> = CODEC

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    /** `BaseEntityBlock` renders nothing unless a block says otherwise. */
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = BedBlockEntity(pos, state)

    companion object {
        /** The bed's top, as a share of a block. A sleeper lies at this height above the bed's block position. */
        const val HEIGHT = 6.0 / 16.0

        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 16.0 * HEIGHT, 16.0)

        val CODEC: MapCodec<BedBlock> = RecordCodecBuilder.mapCodec<BedBlock> { instance ->
            instance.group(BedTier.CODEC.fieldOf("tier").forGetter(BedBlock::tier), propertiesCodec<BedBlock>())
                .apply(instance, ::BedBlock)
        }
    }
}
