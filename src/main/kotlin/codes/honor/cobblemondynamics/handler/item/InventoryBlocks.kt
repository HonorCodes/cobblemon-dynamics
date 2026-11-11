package codes.honor.cobblemondynamics.handler.item

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/**
 * The blocks of one inventory that spans several blocks, such as the two halves of a double chest or the blocks of a Create item
 * vault. Every block of it maps to one canonical position, which keys its binding and its claim (CD-CLM-04), so two halves of one
 * chest are never the source and the destination of one binding (CD-TLK-10).
 */
internal object InventoryBlocks {
    /** A search stops after this many blocks. Create's largest item vault has 81. */
    const val MAX_BLOCKS = 256

    /**
     * The least position, in [BlockPos] order, of [start] and the blocks that [joined] links to it through their faces. [joined]
     * gets a block that the search has found and one of its six neighbours.
     */
    fun canonical(start: BlockPos, maxBlocks: Int = MAX_BLOCKS, joined: (from: BlockPos, to: BlockPos) -> Boolean): BlockPos {
        val first = start.immutable()
        val found = hashSetOf(first)
        val queue = ArrayDeque(listOf(first))
        var least = first
        while (queue.isNotEmpty() && found.size < maxBlocks) {
            val from = queue.removeFirst()
            for (direction in Direction.entries) {
                val to = from.relative(direction)
                if (to in found || !joined(from, to)) continue
                found += to
                queue += to
                if (to < least) least = to
                if (found.size >= maxBlocks) break
            }
        }
        return least
    }
}
