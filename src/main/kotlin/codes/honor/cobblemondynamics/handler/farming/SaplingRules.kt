package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block

/** When the Cultivator grows a sapling (CD-LUM-23), and which cell names a 2 × 2 of saplings (CD-LUM-03, CD-CUL-13). */
object SaplingRules {
    /**
     * The saplings whose tree grows only from a complete 2 × 2 of them: the Lumberjack's tag `#cobblemon_dynamics:lumberjack/needs_four`,
     * whose default holds the dark oak sapling. The Cultivator and the Lumberjack's replant (CD-LUM-18) read this one tag. Bone meal on
     * such a sapling outside a 2 × 2 moves it to its last stage and then changes nothing.
     */
    val NEEDS_FOUR: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack/needs_four"))

    /** The lowest corner of each 2 × 2 that holds a cell, as x and z offsets from that cell. */
    private val SQUARES = listOf(0 to 0, 0 to -1, -1 to 0, -1 to -1)

    /** The least corners of the four squares that hold a cell, as x and z offsets from that cell. */
    private val CORNERS = listOf(-1 to -1, -1 to 0, 0 to -1, 0 to 0)

    /**
     * Whether a sapling lies in a complete 2 × 2 of its kind. [same] tells whether the cell at an x and a z offset from the sapling
     * holds the same block. The four squares that hold the sapling are those that vanilla's tree grower tries.
     */
    fun inFullSquare(same: (dx: Int, dz: Int) -> Boolean): Boolean =
        SQUARES.any { (x, z) -> same(x, z) && same(x + 1, z) && same(x, z + 1) && same(x + 1, z + 1) }

    /** The four cells of the 2 × 2 whose corner with the least coordinates is [corner]. */
    fun square(corner: BlockPos): List<BlockPos> = listOf(corner, corner.east(), corner.south(), corner.east().south())

    /**
     * CD-LUM-03, CD-CUL-13: the least corner of the one complete 2 × 2 that holds [pos] and whose cells all hold the sapling of [pos]
     * ([same]), or [pos] when no such square or more than one holds it. The Lumberjack and the Cultivator both ask this, so both name one
     * cell for a square. The answer is a fixed point: the corner resolves to itself, so a binding moved to it never moves on along a row
     * or a field of saplings (CD-LSH-36).
     */
    fun corner(pos: BlockPos, same: (BlockPos) -> Boolean): BlockPos = onlySquare(pos, same) ?: pos

    /** The least corner of the one complete 2 × 2 of [same] cells that holds [pos], or null when none or more than one holds it. */
    fun onlySquare(pos: BlockPos, same: (BlockPos) -> Boolean): BlockPos? =
        CORNERS.map { (x, z) -> pos.offset(x, 0, z) }.filter { corner -> square(corner).all(same) }.singleOrNull()

    /**
     * CD-ASG-24: the cells that a tree grown at [corner] fills at its base: [corner], and each other cell of the 2 × 2 whose least corner
     * it is that holds the same sapling or trunk ([same]).
     */
    fun growthCells(corner: BlockPos, same: (BlockPos) -> Boolean): List<BlockPos> = listOf(corner) + square(corner).drop(1).filter(same)
}
