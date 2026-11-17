package codes.honor.cobblemondynamics.handler.water

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/** What one cell holds, as far as the Freezer's rules read it. */
enum class WaterCell {
    /** `minecraft:water` at its source level. */
    SOURCE,

    /** Water below its source level, which flows. */
    FLOWING,

    /** `minecraft:bubble_column`, the top block of a bubble column included. */
    BUBBLE_COLUMN,

    /** Another block that holds a water source, such as a waterlogged slab. */
    WATERLOGGED,
    AIR,
    OTHER,
    ;

    companion object {
        /**
         * The kind of a cell from its block and its fluid. A bubble column and a waterlogged block hold a water source as their fluid,
         * but neither is a `minecraft:water` block, so neither is a [SOURCE] (CD-FRZ-03, CD-FRZ-07).
         */
        fun of(air: Boolean, waterBlock: Boolean, bubbleColumn: Boolean, water: Boolean, source: Boolean): WaterCell = when {
            air -> AIR
            bubbleColumn -> BUBBLE_COLUMN
            !water -> OTHER
            !source -> FLOWING
            waterBlock -> SOURCE
            else -> WATERLOGGED
        }
    }
}

/** The facts of one cell that decide whether a Freezer may freeze it (ADR-0019, decisions 2 and 4). */
data class WaterFacts(
    val cell: WaterCell,
    val above: WaterCell,
    /** A horizontal neighbour or the cell below holds flowing water (CD-FRZ-08). */
    val feedsFlow: Boolean,
    /** Farmland lies within 4 blocks of the cell horizontally, at its height or one block below it (CD-FRZ-06). */
    val hydratesFarmland: Boolean,
    val blockLight: Int,
    val inWorkArea: Boolean,
    /** A living entity is in the cell, which ice would close in. */
    val occupied: Boolean,
)

/** The Freezer's decisions over plain facts (ADR-0019). */
object FreezeRules {
    /** CD-FRZ-11: a cell that a land claim question refused has no demand for that worker for this long. */
    const val REFUSED_WAIT_TICKS = 6_000L

    /** CD-FRZ-09: vanilla freezes no water at this block light or more, and its ice there melts again. */
    const val MIN_UNFROZEN_LIGHT = 10

    /** The farmland box of CD-FRZ-06: `FarmBlock.isNearWater` reads (−4, 0, −4) to (4, 1, 4) around the farmland. */
    private const val FARMLAND_REACH = 4

    private const val EPSILON = 1e-9

    /** CD-FRZ-03: the two cells that a block scan reads. Only a source under air ices over on top, as on a lake. */
    fun matches(cell: WaterCell, above: WaterCell): Boolean = cell == WaterCell.SOURCE && above == WaterCell.AIR

    /** CD-FRZ-03, CD-FRZ-06 to CD-FRZ-09: a target cell in the work area that no rule leaves as water. */
    fun freezable(f: WaterFacts): Boolean = f.inWorkArea &&
        matches(f.cell, f.above) &&
        f.cell != WaterCell.BUBBLE_COLUMN &&
        !f.feedsFlow &&
        f.blockLight < MIN_UNFROZEN_LIGHT &&
        !f.hydratesFarmland &&
        !f.occupied

    /** CD-FRZ-08: one of the four horizontal neighbours of [cell], or the cell below it, holds flowing water. */
    fun feedsFlow(cell: BlockPos, kindAt: (BlockPos) -> WaterCell): Boolean =
        listOf(cell.north(), cell.south(), cell.east(), cell.west(), cell.below()).any { kindAt(it) == WaterCell.FLOWING }

    /**
     * CD-FRZ-06: farmland lies within 4 blocks of [cell] horizontally, at its height or one block below it, so the water of [cell]
     * hydrates it. Reads the 9 × 9 × 2 box once, and stops at the first farmland.
     */
    fun hydratesFarmland(cell: BlockPos, isFarmland: (BlockPos) -> Boolean): Boolean {
        val pos = BlockPos.MutableBlockPos()
        for (dy in -1..0) {
            for (dx in -FARMLAND_REACH..FARMLAND_REACH) {
                for (dz in -FARMLAND_REACH..FARMLAND_REACH) {
                    if (isFarmland(pos.setWithOffset(cell, dx, dy, dz).immutable())) return true
                }
            }
        }
        return false
    }

    /**
     * CD-FRZ-05: the next cell to freeze. Of [cells], the one nearest to [worker] within [reach] blocks, measured between block centres,
     * for which [freezable] holds, and [claimed] only when no other cell does. Cells at one distance go by `BlockPos.asLong()`, so the
     * order is fixed. The claimed cell is the worker's target, which it works from its spot within the role's range, so it is the last
     * cell even beyond reach. Null when nothing is left to freeze. [freezable] runs only until a cell passes it, nearest first.
     */
    fun next(
        worker: Vec3,
        claimed: BlockPos,
        cells: List<BlockPos>,
        reach: Double,
        freezable: (BlockPos) -> Boolean = { true },
    ): BlockPos? {
        val limit = reach * reach + EPSILON
        return cells.asSequence()
            .filter { it != claimed }
            .map { it to Vec3.atCenterOf(it).distanceToSqr(worker) }
            .filter { (_, distance) -> distance <= limit }
            .sortedWith(compareBy<Pair<BlockPos, Double>> { it.second }.thenBy { it.first.asLong() })
            .map { it.first }
            .firstOrNull(freezable)
            ?: claimed.takeIf(freezable)
    }
}
