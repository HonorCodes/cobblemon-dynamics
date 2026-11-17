package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
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

        /** Saplings whose grown trees the never tag skips, so a bind on one is refused (CD-LUM-20). */
        val NEVER_SAPLINGS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack/never_saplings"))

        /** The branches of Dynamic Trees, which only that mod fills, so the tag is empty without it (CD-LUM-25). */
        val DYNAMIC_BRANCHES: TagKey<Block> =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dynamictrees", "branches"))

        /** It could hold a log of the tree, or one that keeps a leaf (CD-LUM-04). */
        private val UNLOADED = CellFacts(id("unloaded"), anyLog = true, never = true, unloaded = true)

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
                neverSapling = state.`is`(NEVER_SAPLINGS),
                dynamicBranch = state.`is`(DYNAMIC_BRANCHES),
            )
        }
    }
}
