package codes.honor.cobblemondynamics.handler.water

import codes.honor.cobblemondynamics.handler.vanilla.FarmlandMoisture
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
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
    /** An entity other than a dropped item or an XP orb is in the cell or in the water below it, which ice would close in (CD-FRZ-16). */
    val occupied: Boolean,
    /** A block of `#cobblemon_dynamics:needs_adjacent_water` needs the water of the cell (CD-FRZ-15). */
    val neededByBlock: Boolean,
)

/** The Freezer's decisions over plain facts (ADR-0019). */
object FreezeRules {
    /** CD-FRZ-11: a cell that a land claim question refused has no demand for that worker for this long. */
    const val REFUSED_WAIT_TICKS = 6_000L

    /** CD-FRZ-09: vanilla freezes no water at this block light or more, and its ice there melts again. */
    const val MIN_UNFROZEN_LIGHT = 10

    /** CD-FRZ-16: the occupant box reaches at most this many cells down the water column under the source. */
    const val MAX_COLUMN_DEPTH = 3

    private const val EPSILON = 1e-9

    /** CD-FRZ-03: the two cells that a block scan reads. Only a source under air ices over on top, as on a lake. */
    fun matches(cell: WaterCell, above: WaterCell): Boolean = cell == WaterCell.SOURCE && above == WaterCell.AIR

    /**
     * CD-FRZ-03, CD-FRZ-08, CD-FRZ-09: the cheap facts, which a Working tick reads: the match, the work area, the flow and the light. The
     * farmland box, the blocks that need the water and the occupants wait for a freeze tick (R84).
     */
    fun stillFreezable(f: WaterFacts): Boolean = f.inWorkArea &&
        matches(f.cell, f.above) &&
        f.cell != WaterCell.BUBBLE_COLUMN &&
        !f.feedsFlow &&
        f.blockLight < MIN_UNFROZEN_LIGHT

    /** CD-FRZ-03, CD-FRZ-06 to CD-FRZ-09, CD-FRZ-15, CD-FRZ-16: a target cell in the work area that no rule leaves as water. */
    fun freezable(f: WaterFacts): Boolean = stillFreezable(f) && !f.hydratesFarmland && !f.neededByBlock && !f.occupied

    /** CD-FRZ-08: one of the four horizontal neighbours of [cell], or the cell below it, holds flowing water. */
    fun feedsFlow(cell: BlockPos, kindAt: (BlockPos) -> WaterCell): Boolean =
        listOf(cell.north(), cell.south(), cell.east(), cell.west(), cell.below()).any { kindAt(it) == WaterCell.FLOWING }

    /**
     * CD-FRZ-06: farmland lies within 4 blocks of [cell] horizontally, at its height or one block below it, so the water of [cell]
     * hydrates it: `FarmBlock.isNearWater` reads (−4, 0, −4) to (4, 1, 4) around the farmland. The reach is the Pump's on farmland
     * (CD-MCH-09). Reads the 9 × 9 × 2 box once, and stops at the first farmland. [isFarmland] gets one mutable position, which it
     * must not keep.
     */
    fun hydratesFarmland(cell: BlockPos, isFarmland: (BlockPos) -> Boolean): Boolean {
        val reach = FarmlandMoisture.REACH
        val pos = BlockPos.MutableBlockPos()
        for (dy in -1..0) {
            for (dx in -reach..reach) {
                for (dz in -reach..reach) {
                    if (isFarmland(pos.setWithOffset(cell, dx, dy, dz))) return true
                }
            }
        }
        return false
    }

    /**
     * CD-FRZ-15: a block that [needsWater] picks, such as sugar cane on the bank, a coral block or a conduit, holds one of the four
     * horizontal neighbours of [cell], the block above one of them, or the block below [cell]. Reads these 9 cells once, and stops at
     * the first such block. [needsWater] gets one mutable position, which it must not keep.
     */
    fun neededByBlock(cell: BlockPos, needsWater: (BlockPos) -> Boolean): Boolean {
        val pos = BlockPos.MutableBlockPos()
        for ((dx, dz) in SIDES) {
            for (dy in 0..1) if (needsWater(pos.setWithOffset(cell, dx, dy, dz))) return true
        }
        return needsWater(pos.setWithOffset(cell, 0, -1, 0))
    }

    /**
     * CD-FRZ-16: the box in which an occupant keeps [cell] as water: the cell, and the water column under it down to the first cell that
     * [isWater] refuses, at most [MAX_COLUMN_DEPTH] cells. A diver one block down keeps the surface above it open.
     */
    fun occupantBox(cell: BlockPos, isWater: (BlockPos) -> Boolean): AABB {
        val pos = BlockPos.MutableBlockPos()
        val depth = (1..MAX_COLUMN_DEPTH).firstOrNull { !isWater(pos.setWithOffset(cell, 0, -it, 0)) }?.minus(1) ?: MAX_COLUMN_DEPTH
        return AABB(cell.x.toDouble(), cell.y.toDouble() - depth, cell.z.toDouble(), cell.x + 1.0, cell.y + 1.0, cell.z + 1.0)
    }

    private val SIDES = listOf(0 to -1, 0 to 1, 1 to 0, -1 to 0)

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
