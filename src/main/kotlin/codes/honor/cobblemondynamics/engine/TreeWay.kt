package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos

/**
 * CD-ASG-25: the blocks that the way out of a grown tree may pass. Only the tree that changed counts: its natural leaves and the trunk
 * joined to the changed target. A wall of logs or of placed leaves stops the fill, so a worker never lands inside a log cabin.
 */
object TreeWay {
    /** The most trunk cells that [trunk] gathers, which bounds its cost at the tick of a change. */
    const val MAX_TRUNK = 512

    /**
     * The trunk of the tree at [start]: [start] and the cells joined to it through their 26 neighbours that [isTrunk] accepts, at most
     * [max]. Empty when [start] is no trunk cell, as after a fell.
     */
    fun trunk(start: BlockPos, isTrunk: (BlockPos) -> Boolean, max: Int = MAX_TRUNK): Set<BlockPos> {
        if (!isTrunk(start)) return emptySet()
        val found = LinkedHashSet<BlockPos>()
        found += start.immutable()
        val queue = ArrayDeque(found)
        while (queue.isNotEmpty() && found.size < max) {
            val pos = queue.removeFirst()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        val next = pos.offset(dx, dy, dz)
                        if (next in found || !isTrunk(next)) continue
                        found += next
                        queue += next
                        if (found.size >= max) return found
                    }
                }
            }
        }
        return found
    }

    /** A cell that the fill passes: one with no collision, a natural leaf, or a cell of the changed tree's trunk, asked last. */
    fun passes(empty: Boolean, naturalLeaf: Boolean, inTrunk: () -> Boolean): Boolean = empty || naturalLeaf || inTrunk()
}
