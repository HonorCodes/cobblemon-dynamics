package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/** What the tree rules read of one cell. `TreeFactsReader` fills it from a level, and the unit tests build grids by hand. */
data class CellFacts(
    val blockId: ResourceLocation,
    /** `#cobblemon_dynamics:lumberjack_targets` (CD-LUM-02). */
    val logTarget: Boolean = false,
    /** `#minecraft:logs`, which keeps a leaf alive. */
    val anyLog: Boolean = false,
    /** A `LeavesBlock`. */
    val leaves: Boolean = false,
    /** `LeavesBlock.PERSISTENT`, which every leaf that a player places holds (CD-LUM-06). */
    val persistent: Boolean = false,
    /** `#cobblemon_dynamics:lumberjack/never` (CD-LUM-08). */
    val never: Boolean = false,
    val air: Boolean = false,
    /** `#minecraft:saplings` (CD-LUM-02). */
    val sapling: Boolean = false,
    /** A `LiquidBlock`, such as water (CD-LUM-19). */
    val fluid: Boolean = false,
) {
    companion object {
        val AIR = CellFacts(ResourceLocation.withDefaultNamespace("air"), air = true)
    }
}

/** A tree of CD-LUM-04 to CD-LUM-08: its trunk base cell, its logs and its own natural leaves. */
data class Tree(val base: BlockPos, val logs: Set<BlockPos>, val leaves: Set<BlockPos>) {
    /** Every cell that the fell empties, less the shell. */
    val cells: Set<BlockPos> by lazy { logs + leaves }
}
