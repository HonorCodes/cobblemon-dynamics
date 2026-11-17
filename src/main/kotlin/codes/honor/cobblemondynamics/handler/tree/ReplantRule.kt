package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos

/**
 * The replant of a fell (ADR-0017, decision 10). The sapling comes from the fell's own drops, so no sapling comes from nothing, and the
 * handler plants it in the tick of the last removal.
 */
object ReplantRule {
    /** [item] goes into each of [cells], one item each. */
    data class Replant<K>(val item: K, val cells: List<BlockPos>)

    /**
     * CD-LUM-17, CD-LUM-18: the sapling of [drops] to plant, or null. [isSapling] tells a dropped item that places a block of
     * `#minecraft:saplings`, [survives] whether its block stands in a cell, and [needsFour] whether it is in
     * `#cobblemon_dynamics:lumberjack/needs_four`. A 2 × 2 trunk takes four saplings of one kind when the drops hold four and each cell of
     * the square holds one. Otherwise the base cell takes one, except a sapling that needs four, which never grows alone.
     */
    fun <K> choose(
        drops: List<Pair<K, Int>>,
        base: BlockPos,
        twoByTwo: Boolean,
        isSapling: (K) -> Boolean,
        survives: (K, BlockPos) -> Boolean,
        needsFour: (K) -> Boolean,
    ): Replant<K>? {
        val saplings = drops.filter { (item, count) -> count > 0 && isSapling(item) && survives(item, base) }
        if (twoByTwo) {
            val square = TreeRules.square(base)
            saplings.firstOrNull { (item, count) ->
                count >= 4 && square.all { survives(item, it) }
            }?.let { return Replant(it.first, square) }
        }
        return saplings.firstOrNull { (item, _) -> !needsFour(item) }?.let { Replant(it.first, listOf(base)) }
    }
}
