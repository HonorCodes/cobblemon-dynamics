package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.farming.PlotRule
import net.minecraft.core.BlockPos

/** The Lumberjack's choices over plain facts of the cells (ADR-0017). `LumberjackHandler` reads the facts from the level. */
object TreeRules {
    /** CD-LUM-20: "{Pokémon} cannot fell this tree". */
    const val CANNOT_FELL = "cobblemon_dynamics.leash.cannot_fell"

    /** CD-LUM-02: a log of `#cobblemon_dynamics:lumberjack_targets`, or a block of `#minecraft:saplings`. */
    fun matches(cell: CellFacts): Boolean = cell.logTarget || cell.sapling

    /**
     * CD-LUM-04 to CD-LUM-08: the tree that stands on [base], whose search from [base] finds [base] as its trunk base cell, or null. A log
     * that a player puts below the base makes another cell the base, so the bound cell holds no tree.
     */
    fun treeOn(cells: (BlockPos) -> CellFacts, base: BlockPos, maxLogs: Int, maxLeaves: Int): Tree? {
        if (!cells(base).logTarget) return null
        return TreeSearch.find(cells, base, maxLogs, maxLeaves)?.takeIf { it.base == base }
    }

    /** CD-LUM-10: a tree stands on the base cell. A sapling or an empty cell waits in Standby. */
    fun hasDemand(cells: (BlockPos) -> CellFacts, base: BlockPos, maxLogs: Int, maxLeaves: Int): Boolean =
        treeOn(cells, base, maxLogs, maxLeaves) != null

    /** CD-LUM-19: the base cell holds a log of the target tag, a sapling, or air or a fluid above a block that is not air. */
    fun bindingHolds(cell: CellFacts, below: CellFacts): Boolean =
        PlotRule.keeps(cellInRoleTag = cell.logTarget || cell.sapling, cellEmpty = cell.air || cell.fluid, belowIsAir = below.air)

    /** CD-LUM-20: a bind on [base] names no sapling and no tree that the search finds. */
    fun refusesBind(cells: (BlockPos) -> CellFacts, base: BlockPos, maxLogs: Int, maxLeaves: Int): Boolean =
        !cells(base).sapling && treeOn(cells, base, maxLogs, maxLeaves) == null

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
