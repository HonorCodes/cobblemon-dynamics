package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos

/**
 * CD-LUM-05, CD-LUM-06: the tree's own natural leaves. Vanilla keeps a leaf while a block of `#minecraft:logs` lies within 6 face steps
 * of it through leaves, persistent or not. So the leaves within reach of the tree's logs and of no other log are those that would decay
 * once the tree is gone, and a neighbouring tree keeps its own. A persistent leaf carries the reach but never joins.
 */
object CanopyRule {
    /** The face steps through leaves within which a log keeps a leaf. */
    const val MAX_STEPS = 6

    /** The natural leaves within reach of [logs] and of no other log, or null when they are more than [maxLeaves]. */
    fun canopy(cells: (BlockPos) -> CellFacts, logs: Set<BlockPos>, maxLeaves: Int = Int.MAX_VALUE): Set<BlockPos>? {
        // A log that keeps a leaf of the tree's reach lies within 6 steps of that leaf, so one search of 2 × 6 - 1 steps from the
        // tree's logs meets every leaf next to such a log.
        val around = leavesWithin(cells, logs, 2 * MAX_STEPS - 1)
        val others = HashSet<BlockPos>()
        for (leaf in around.keys) for (next in TreeSearch.faces(leaf)) if (next !in logs && cells(next).anyLog) others += next
        val kept = if (others.isEmpty()) emptySet() else leavesWithin(cells, others, MAX_STEPS).keys
        val own = LinkedHashSet<BlockPos>()
        for ((leaf, steps) in around) {
            if (steps > MAX_STEPS || leaf in kept || cells(leaf).persistent) continue
            own += leaf
            if (own.size > maxLeaves) return null
        }
        return own
    }

    /** Each leaf within [maxSteps] face steps of [sources] through leaves, with its steps: 1 for a leaf next to a source. */
    fun leavesWithin(cells: (BlockPos) -> CellFacts, sources: Set<BlockPos>, maxSteps: Int): Map<BlockPos, Int> {
        val steps = LinkedHashMap<BlockPos, Int>()
        var frontier: List<BlockPos> = sources.toList()
        for (step in 1..maxSteps) {
            val next = ArrayList<BlockPos>()
            for (pos in frontier) {
                for (cell in TreeSearch.faces(pos)) {
                    if (cell in steps || cell in sources || !cells(cell).leaves) continue
                    steps[cell] = step
                    next += cell
                }
            }
            if (next.isEmpty()) break
            frontier = next
        }
        return steps
    }
}
