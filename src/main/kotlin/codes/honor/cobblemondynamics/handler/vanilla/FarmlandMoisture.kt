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

    /** A bound Pump checks its farmland on its first tick of work and every this many ticks after it. */
    const val CHECK_TICKS = 20

    /** Whether a farmland block of the area of [bound] is below moisture 7. [moistureAt] gives null where no farmland is. */
    fun hasDemand(bound: BlockPos, moistureAt: (BlockPos) -> Int?): Boolean = area(bound).any { (moistureAt(it) ?: WET) < WET }

    /**
     * One check of the area of [bound]: each farmland block below moisture 7 is asked of [mayChange] in this tick, as each block that
     * a harvest changes is (CD-HRV-13), and goes to [wet] where it is allowed. Wet farmland and other blocks are not asked.
     */
    fun check(bound: BlockPos, moistureAt: (BlockPos) -> Int?, mayChange: (BlockPos) -> Boolean, wet: (BlockPos) -> Unit) {
        for (cell in area(bound)) {
            val moisture = moistureAt(cell) ?: continue
            if (moisture < WET && mayChange(cell)) wet(cell)
        }
    }

    /** The 9 × 9 cells at the height of [bound], around it. */
    private fun area(bound: BlockPos): List<BlockPos> = buildList {
        for (dx in -REACH..REACH) for (dz in -REACH..REACH) add(bound.offset(dx, 0, dz))
    }
}
