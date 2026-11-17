package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * Reads the [CellFacts] of one search from a level, once for each cell. It never loads a chunk: a cell of a chunk that is not loaded
 * reads as a block of the never tag, so a tree that reaches such a chunk is skipped whole rather than felled in part.
 */
internal class TreeFactsReader(private val level: ServerLevel) {
    private val facts = HashMap<BlockPos, CellFacts>()

    fun facts(pos: BlockPos): CellFacts = facts.getOrPut(pos.immutable()) { stateOf(level, pos)?.let(::factsOf) ?: UNLOADED }

    companion object {
        /** The logs that a Lumberjack fells. The default holds `#minecraft:logs` (CD-LUM-02). */
        val TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack_targets"))

        /** A tree with a block of this tag, or next to one, stays (CD-LUM-08). */
        val NEVER: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack/never"))

        /** Saplings that never grow alone, which a 2 × 2 trunk replants only as four (CD-LUM-18). */
        val NEEDS_FOUR: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack/needs_four"))

        private val UNLOADED = CellFacts(id("unloaded"), never = true)

        /** The state of [pos] in a loaded chunk, or null. A cell above or below the level reads as void air. */
        fun stateOf(level: ServerLevel, pos: BlockPos): BlockState? {
            if (level.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState()
            return level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4)?.getBlockState(pos)
        }

        fun factsOf(state: BlockState): CellFacts {
            val leaves = state.block is LeavesBlock
            return CellFacts(
                blockId = BuiltInRegistries.BLOCK.getKey(state.block),
                logTarget = state.`is`(TARGETS),
                anyLog = state.`is`(BlockTags.LOGS),
                leaves = leaves,
                // A leaf with no such property cannot tell natural from placed, so it counts as placed (CD-LUM-06).
                persistent = leaves && state.getOptionalValue(LeavesBlock.PERSISTENT).orElse(true),
                never = state.`is`(NEVER),
                air = state.isAir,
                sapling = state.`is`(BlockTags.SAPLINGS),
                fluid = state.block is LiquidBlock,
            )
        }
    }
}
