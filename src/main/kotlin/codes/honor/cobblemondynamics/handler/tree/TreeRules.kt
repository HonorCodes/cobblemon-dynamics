package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos

/** The Lumberjack's choices over plain facts of the cells (ADR-0017). `LumberjackHandler` reads the facts from the level. */
object TreeRules {
    /**
     * CD-LUM-03: a clicked log resolves to the trunk base cell of its logs, and a clicked sapling to itself or to the corner of its 2 × 2
     * of saplings of one kind. Any other cell, and logs above [maxLogs], resolve to the clicked cell.
     */
    fun canonical(cells: (BlockPos) -> CellFacts, clicked: BlockPos, maxLogs: Int): BlockPos {
        val facts = cells(clicked)
        if (facts.sapling) return saplingCorner(cells, clicked, facts)
        return TreeSearch.logs(cells, clicked, maxLogs)?.minWith(TreeSearch.LOWEST) ?: clicked
    }

    /** The four cells of the 2 × 2 whose corner with the least coordinates is [corner]. */
    fun square(corner: BlockPos): List<BlockPos> = listOf(corner, corner.east(), corner.south(), corner.east().south())

    /** The least corner of a 2 × 2 of saplings like [sapling] that holds [pos], or [pos]. */
    private fun saplingCorner(cells: (BlockPos) -> CellFacts, pos: BlockPos, sapling: CellFacts): BlockPos {
        val corners = listOf(pos.offset(-1, 0, -1), pos.offset(-1, 0, 0), pos.offset(0, 0, -1), pos)
        return corners.firstOrNull { corner -> square(corner).all { cells(it).sapling && cells(it).blockId == sapling.blockId } } ?: pos
    }
}
