package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.tree.CellFacts
import codes.honor.cobblemondynamics.handler.tree.TreeSearch
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * CD-ASG-25: the blocks that the way out of a grown tree may pass. Only the tree that changed counts: its natural leaves, the logs that
 * the tree search of CD-LUM-04 finds for it, and the mangrove roots joined to those logs. A wall of logs or of placed leaves stops the
 * fill, so a worker never lands inside a log cabin.
 */
object TreeWay {
    /** The most trunk cells that [trunk] gathers, and the most logs of the search, which bound the cost at the tick of a change. */
    const val MAX_TRUNK = 512

    /** The most leaves of the search. */
    const val MAX_LEAVES = 2048

    /** How far above a root, or above air over roots, the first log of a mangrove's trunk may lie. */
    const val ROOT_REACH = 3

    private val ROOTS = setOf(ResourceLocation.parse("minecraft:mangrove_roots"), ResourceLocation.parse("minecraft:muddy_mangrove_roots"))
    private val MANGROVE_LOG = ResourceLocation.parse("minecraft:mangrove_log")

    fun isRoot(cell: CellFacts): Boolean = cell.blockId in ROOTS

    /**
     * The cell to search the tree from: [base] when it holds a log, or the first log within [ROOT_REACH] above it when [base] holds a root
     * or air over a root, as a mangrove grows its trunk above its roots. Null for any other cell, as after a fell.
     */
    fun searchStart(base: BlockPos, cells: (BlockPos) -> CellFacts): BlockPos? {
        val cell = cells(base)
        if (cell.logTarget) return base
        if (!isRoot(cell) && !(cell.air && isRoot(cells(base.below())))) return null
        return (1..ROOT_REACH).map(base::above).firstOrNull { cells(it).logTarget }
    }

    /**
     * The trunk of the tree that changed at [base]: the logs of its tree search and the mangrove roots joined to them, the logs nearest
     * [around] first, so that the cap keeps the cells around the worker. Empty when the search finds no tree, as for a tree that reaches a
     * chunk that is not loaded. The never tag of CD-LUM-08 holds as for a fell, except for the mangrove's own logs and roots: the net frees
     * a worker from a mangrove, which the Lumberjack never fells.
     */
    fun treeTrunk(cells: (BlockPos) -> CellFacts, base: BlockPos, around: BlockPos): Set<BlockPos> {
        val start = searchStart(base, cells) ?: return emptySet()
        val view = { pos: BlockPos ->
            cells(pos).let { if (!it.unloaded && (isRoot(it) || it.blockId == MANGROVE_LOG)) it.copy(never = false) else it }
        }
        val tree = TreeSearch.find(view, start, MAX_TRUNK, MAX_LEAVES) ?: return emptySet()
        val seeds = tree.logs.sortedBy { it.distSqr(around) }
        return trunk(seeds, { it in tree.logs || isRoot(cells(it)) })
    }

    /**
     * The cells that [isTrunk] accepts, from each of [seeds] in order and through their 26 neighbours, at most [max]. Empty when no seed
     * is a trunk cell.
     */
    fun trunk(seeds: List<BlockPos>, isTrunk: (BlockPos) -> Boolean, max: Int = MAX_TRUNK): Set<BlockPos> {
        val found = LinkedHashSet<BlockPos>()
        for (seed in seeds) {
            if (found.size >= max) return found
            if (seed !in found && isTrunk(seed)) found += seed.immutable()
        }
        val queue = ArrayDeque(found)
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        val next = pos.offset(dx, dy, dz)
                        if (next in found || !isTrunk(next)) continue
                        if (found.size >= max) return found
                        found += next
                        queue += next
                    }
                }
            }
        }
        return found
    }

    /** A cell that the fill passes: one with no collision, a natural leaf, or a cell of the changed tree's trunk, asked last. */
    fun passes(empty: Boolean, naturalLeaf: Boolean, inTrunk: () -> Boolean): Boolean = empty || naturalLeaf || inTrunk()

    fun canLandAbove(below: CellFacts, inChangedTrunk: Boolean): Boolean =
        !inChangedTrunk && !below.anyLog && !below.logTarget && !isRoot(below) && !(below.leaves && !below.persistent)
}
