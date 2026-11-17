package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/** The tree search of ADR-0017, decision 3, over plain facts of the cells. It reads each cell through [CellFacts] only. */
object TreeSearch {
    /** CD-LUM-07: a trunk with fewer natural leaves is a build. */
    const val MIN_LEAVES = 3

    /** CD-LUM-03: the lowest cell first, then the least x, then the least z, so a 2 × 2 trunk resolves to its corner. */
    val LOWEST: Comparator<BlockPos> = compareBy<BlockPos>({ it.y }, { it.x }, { it.z })

    /** CD-LUM-03 to CD-LUM-08: the tree of the clicked or bound cell, or null for no tree. */
    fun find(cells: (BlockPos) -> CellFacts, start: BlockPos, maxLogs: Int, maxLeaves: Int): Tree? {
        val logs = logs(cells, start, maxLogs) ?: return null
        if (logs.any { nearNever(cells, it) }) return null
        val leaves = CanopyRule.canopy(cells, logs, maxLeaves) ?: return null
        if (leaves.size < MIN_LEAVES || leaves.any { nearNever(cells, it) }) return null
        return Tree(logs.minWith(LOWEST), logs, leaves)
    }

    /**
     * CD-LUM-04: the blocks of [start]'s own block id that the search reaches from it through the 26 neighbours, or null when [start]
     * is no log of the target tag or the logs pass [maxLogs]. Acacia logs step diagonally, and a stripped log, a wood block or a log of
     * another kind never joins.
     */
    fun logs(cells: (BlockPos) -> CellFacts, start: BlockPos, maxLogs: Int): Set<BlockPos>? {
        val first = cells(start)
        if (!first.logTarget) return null
        val found = LinkedHashSet<BlockPos>()
        found += start.immutable()
        val queue = ArrayDeque(found)
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            for (next in around(pos)) {
                if (next in found || cells(next).blockId != first.blockId) continue
                found += next
                if (found.size > maxLogs) return null
                queue += next
            }
        }
        return found
    }

    /** The 26 cells around [pos]: the faces, the edges and the corners. */
    fun around(pos: BlockPos): List<BlockPos> {
        val cells = ArrayList<BlockPos>(26)
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) if (dx != 0 || dy != 0 || dz != 0) cells += pos.offset(dx, dy, dz)
            }
        }
        return cells
    }

    /** The 6 cells that share a face with [pos]. */
    fun faces(pos: BlockPos): List<BlockPos> = Direction.entries.map(pos::relative)

    /** CD-LUM-08: the cell, or a cell that shares a face with it, is in the never tag. */
    private fun nearNever(cells: (BlockPos) -> CellFacts, pos: BlockPos): Boolean = cells(pos).never || faces(pos).any { cells(it).never }
}
