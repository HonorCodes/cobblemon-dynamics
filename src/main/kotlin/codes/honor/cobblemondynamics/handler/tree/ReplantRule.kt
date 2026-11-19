package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos

/**
 * The replant of a fell (ADR-0017, decision 10 and its 1.2.1 amendment). The handler plants it in the tick of the last removal. The
 * saplings come from the fell's drops first, and the replant creates only the shortfall (CD-LUM-32).
 */
object ReplantRule {
    /** [item] goes into each of [cells]. [taken] of them come from the drops, and the rest are created. */
    data class Replant<K>(val item: K, val cells: List<BlockPos>, val taken: Int) {
        val created: Int get() = cells.size - taken
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
}
