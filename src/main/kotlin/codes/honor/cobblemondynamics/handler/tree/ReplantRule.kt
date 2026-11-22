package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * The replant of a fell (ADR-0017, decision 10 and its 1.2.1 amendment). The handler plants it in the tick of the last removal. The
 * saplings come from the fell's drops first, and the replant creates only the shortfall (CD-LUM-32).
 */
object ReplantRule {
    /** [item] goes into each of [cells]. [taken] of them come from the drops, and the rest are created. */
    data class Replant<K>(val item: K, val cells: List<BlockPos>, val taken: Int) {
        val created: Int get() = cells.size - taken

        /**
         * CD-LUM-29: the items of the drops that [planted] of the cells used. The held items go first, so a cell whose planting failed
         * leaves a created sapling out, and then a held one, which pops with the other drops.
         */
        fun takenFor(planted: Int): Int = minOf(taken, planted.coerceAtLeast(0))
    }

    /**
     * CD-LUM-17, CD-LUM-18, CD-LUM-32: the replant of [kind], of which the drops hold [held] items. [survives] tells whether the kind's
     * block goes into an empty cell and stands there, [needsFour] whether it is in `#cobblemon_dynamics:lumberjack/needs_four`, and
     * [allowed] the use question of a cell at the end of the fell, since a claim can change during a fell of several ticks. A 2 × 2 trunk
     * gets four saplings when each cell of its square can take one. Otherwise the base cell gets one, except a kind that needs four,
     * which never grows alone and gets none.
     */
    fun <K> choose(
        kind: K,
        held: Int,
        base: BlockPos,
        twoByTwo: Boolean,
        survives: (K, BlockPos) -> Boolean,
        needsFour: (K) -> Boolean,
        allowed: (BlockPos) -> Boolean,
    ): Replant<K>? {
        val fits = { cell: BlockPos -> survives(kind, cell) && allowed(cell) }
        val cells = when {
            twoByTwo && TreeRules.square(base).all(fits) -> TreeRules.square(base)
            !needsFour(kind) && fits(base) -> listOf(base)
            else -> return null
        }
        return Replant(kind, cells, held.coerceIn(0, cells.size))
    }

    /**
     * CD-LUM-18: a kind that needs four got no replant, because a cell of its square cannot take one, so the stump at [base] stays
     * empty. One line for each kind of [sapling] says why. Returns whether it logged.
     */
    fun warnStump(sapling: ResourceLocation, base: BlockPos, warnings: OnceLogger): Boolean = warnings.warn("needs-four:$sapling") {
        "The Lumberjack replants no $sapling at $base: it grows only as four, and a cell of the square cannot take one"
    }
}
