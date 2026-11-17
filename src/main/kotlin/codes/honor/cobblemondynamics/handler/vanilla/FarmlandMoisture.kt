package codes.honor.cobblemondynamics.handler.vanilla

import net.minecraft.core.BlockPos

/**
 * The Pump on farmland (CD-MCH-09). `FarmBlock.isNearWater` looks 4 blocks out on each side, so one water source keeps a 9 × 9 of
 * farmland wet. A Pump bound to a farmland block stands in for that water: each check sets every farmland block within 4 blocks of
 * the bound block horizontally, at its height, to moisture 7. The Freezer keeps the water within the same reach of farmland
 * (CD-FRZ-06), so the two roles agree on which farmland one water source serves.
 */
object FarmlandMoisture {
    /** `FarmBlock.MAX_MOISTURE`, the moisture of farmland near water. */
    const val WET = 7

    /** The horizontal reach of `FarmBlock.isNearWater`. */
    const val REACH = 4

    /** A bound Pump checks its farmland on its first tick of work and every this many ticks after it, and counts its demand as often. */
    const val CHECK_TICKS = 20

    /**
     * One check of the area of [bound]: each farmland block below moisture 7 is asked of [mayChange] in this tick, as each block that
     * a harvest changes is (CD-HRV-13), and goes to [wet] where it is allowed. Wet farmland and other blocks are not asked. Returns the
     * blocks that [mayChange] refused.
     */
    fun check(bound: BlockPos, moistureAt: (BlockPos) -> Int?, mayChange: (BlockPos) -> Boolean, wet: (BlockPos) -> Unit): Set<BlockPos> {
        val refused = HashSet<BlockPos>()
        for (cell in dry(bound, moistureAt)) {
            if (mayChange(cell)) wet(cell) else refused += cell
        }
        return refused
    }

    /** The farmland blocks of the area of [bound] below moisture 7. [moistureAt] gives null where no farmland is. */
    private fun dry(bound: BlockPos, moistureAt: (BlockPos) -> Int?): List<BlockPos> = area(bound).filter { (moistureAt(it) ?: WET) < WET }

    /** The 9 × 9 cells at the height of [bound], around it. */
    private fun area(bound: BlockPos): List<BlockPos> = buildList {
        for (dx in -REACH..REACH) for (dz in -REACH..REACH) add(bound.offset(dx, 0, dz))
    }

    /**
     * What one claim of a Pump on farmland keeps between its ticks (R90). It counts the demand at most once in [CHECK_TICKS] ticks
     * after its last count or check, so it reads the 81 blocks of the area once a count, not on every tick. A dry block that the land
     * claim checks refused at the last check gives no demand, so a Pump whose only dry farmland lies in a claim that refuses it stands
     * by, and its Work Stamina recovers. Each count asks the claim checks again for such a block, so the Pump starts again within 20
     * ticks of the claim allowing it.
     */
    class Wetting(
        /** The tick that the claim's stretch of work started, from which the checks count. */
        var since: Long,
    ) {
        private var refused: Set<BlockPos> = emptySet()
        private var demand = false
        private var countedAt: Long? = null

        /** The demand of the area of [bound] at [now]: a dry block that the last check did not refuse, or one that [mayChange] now allows. */
        fun hasDemand(bound: BlockPos, now: Long, moistureAt: (BlockPos) -> Int?, mayChange: (BlockPos) -> Boolean): Boolean {
            val last = countedAt
            if (last != null && now - last < CHECK_TICKS) return demand
            val dry = dry(bound, moistureAt)
            demand = if (dry.any { it !in refused }) {
                true
            } else {
                refused = dry.filterTo(HashSet()) { !mayChange(it) }
                refused.size < dry.size
            }
            countedAt = now
            return demand
        }

        /** A check on the tick [since] and every 20 ticks after it. After a check every dry block is wet or refused, so no demand is left. */
        fun work(bound: BlockPos, now: Long, moistureAt: (BlockPos) -> Int?, mayChange: (BlockPos) -> Boolean, wet: (BlockPos) -> Unit) {
            if (!TickClock.every(CHECK_TICKS, since, now)) return
            refused = check(bound, moistureAt, mayChange, wet)
            demand = false
            countedAt = now
        }
    }
}
