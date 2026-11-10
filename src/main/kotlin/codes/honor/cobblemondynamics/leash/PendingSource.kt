package codes.honor.cobblemondynamics.leash

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/** The source block of a source-and-destination binding, while the Work Leash waits for the destination click (CD-LSH-17). */
data class PendingSource(val pos: BlockPos, val face: Direction, val clickedAt: Long) {
    /** CD-LSH-19: alive [ttl] - 1 ticks after the click, gone at [ttl]. */
    fun expired(now: Long, ttl: Int): Boolean = now - clickedAt >= ttl
}
