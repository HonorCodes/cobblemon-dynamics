package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import kotlin.math.abs

/**
 * Hand-built cells for the tree rules (ADR-0017). A cell that the grid does not hold reads as air. Each shape returns the cells that
 * it set, so that a test names them.
 */
class TreeGrid {
    private val cells = LinkedHashMap<BlockPos, CellFacts>()

    /** The facts of each cell, as `TreeFactsReader` reads them from a level. */
    val read: (BlockPos) -> CellFacts = { cells[it] ?: CellFacts.AIR }

    /** The cells that hold [id]. */
    fun all(id: String): Set<BlockPos> = cells.filterValues { it.blockId == rl(id) }.keys

    fun set(pos: BlockPos, facts: CellFacts): BlockPos = pos.immutable().also { cells[it] = facts }

    /** A log of the Lumberjack's target tag, which is also in `#minecraft:logs`. */
    fun log(pos: BlockPos, id: String = OAK_LOG, never: Boolean = false): BlockPos =
        set(pos, CellFacts(rl(id), logTarget = true, anyLog = true, never = never))

    fun leaf(pos: BlockPos, id: String = OAK_LEAVES, persistent: Boolean = false, never: Boolean = false): BlockPos =
        set(pos, CellFacts(rl(id), leaves = true, persistent = persistent, never = never))

    /** A block that is neither a log nor a leaf, such as dirt, cocoa or a bee nest. */
    fun block(pos: BlockPos, id: String, never: Boolean = false): BlockPos = set(pos, CellFacts(rl(id), never = never))

    fun sapling(pos: BlockPos, id: String = OAK_SAPLING): BlockPos = set(pos, CellFacts(rl(id), sapling = true))

    /** A column of [height] logs from [base] up. */
    fun trunk(base: BlockPos, height: Int, id: String = OAK_LOG, never: Boolean = false): List<BlockPos> =
        (0 until height).map { log(base.above(it), id, never) }

    /** Leaves in the square of [radius] around [center], less its corners when [round], in every cell that holds nothing yet. */
    fun layer(center: BlockPos, radius: Int, round: Boolean = false, id: String = OAK_LEAVES, never: Boolean = false): List<BlockPos> {
        val placed = ArrayList<BlockPos>()
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (round && radius > 0 && abs(dx) == radius && abs(dz) == radius) continue
                val pos = center.offset(dx, 0, dz)
                if (pos !in cells) placed += leaf(pos, id, never = never)
            }
        }
        return placed
    }

    /** [center] and its four horizontal neighbours, as leaves. */
    fun cross(center: BlockPos, id: String = OAK_LEAVES): List<BlockPos> = layer(center, 0, id = id) +
        listOf(center.north(), center.south(), center.east(), center.west()).filter { it !in cells }.map { leaf(it, id) }

    /** An oak as world generation grows it: 5 logs, two round layers of radius 2, a layer of radius 1 and a cross on top: 53 leaves. */
    fun oak(base: BlockPos, logId: String = OAK_LOG, leafId: String = OAK_LEAVES, never: Boolean = false): Shape {
        val logs = trunk(base, 5, logId, never)
        val leaves = layer(base.above(2), 2, round = true, id = leafId, never = never) +
            layer(base.above(3), 2, round = true, id = leafId, never = never) +
            layer(base.above(4), 1, id = leafId, never = never) +
            cross(base.above(5), leafId)
        return Shape(logs, leaves)
    }

    /** A fancy oak: a trunk of 7 logs, a branch that steps through corners and one that steps through edges, with leaves at each end. */
    fun fancyOak(base: BlockPos): Shape {
        val logs = trunk(base, 7) +
            listOf(log(base.offset(1, 4, 1)), log(base.offset(2, 5, 2))) +
            listOf(log(base.offset(-1, 5, 0)), log(base.offset(-2, 6, 0)))
        val leaves = listOf(base.offset(2, 6, 2), base.offset(-2, 7, 0), base.above(7)).flatMap { end ->
            layer(end, 2, round = true) + layer(end.above(), 1)
        }
        return Shape(logs, leaves)
    }

    /** An acacia: 3 logs up, then a branch that steps diagonally east and one that steps diagonally north-west, with flat leaves. */
    fun acacia(base: BlockPos): Shape {
        val logs = trunk(base, 3, ACACIA_LOG) +
            listOf(log(base.offset(1, 3, 0), ACACIA_LOG), log(base.offset(2, 4, 0), ACACIA_LOG)) +
            listOf(log(base.offset(-1, 3, -1), ACACIA_LOG), log(base.offset(-2, 4, -2), ACACIA_LOG))
        val leaves = listOf(base.offset(2, 5, 0), base.offset(-2, 5, -2)).flatMap { top ->
            layer(top, 2, round = true, id = ACACIA_LEAVES) + layer(top.above(), 1, id = ACACIA_LEAVES)
        }
        return Shape(logs, leaves)
    }

    /** A dark oak with a 2 × 2 trunk of 6 logs from [corner] toward east and south. */
    fun darkOak(corner: BlockPos): Shape {
        val logs = listOf(corner, corner.east(), corner.south(), corner.east().south()).flatMap { trunk(it, 6, DARK_OAK_LOG) }
        val leaves = layer(corner.above(4), 3, round = true, id = DARK_OAK_LEAVES) +
            layer(corner.above(5), 3, round = true, id = DARK_OAK_LEAVES) +
            layer(corner.above(6), 2, id = DARK_OAK_LEAVES)
        return Shape(logs, leaves)
    }

    /** A jungle tree of 6 logs with two layers of radius 2 and one of radius 1. */
    fun jungle(base: BlockPos): Shape {
        val logs = trunk(base, 6, JUNGLE_LOG)
        val leaves = layer(base.above(4), 2, round = true, id = JUNGLE_LEAVES) +
            layer(base.above(5), 2, round = true, id = JUNGLE_LEAVES) +
            layer(base.above(6), 1, id = JUNGLE_LEAVES)
        return Shape(logs, leaves)
    }

    /** The logs and the leaves that a shape set. */
    data class Shape(val logs: List<BlockPos>, val leaves: List<BlockPos>)

    companion object {
        const val OAK_LOG = "minecraft:oak_log"
        const val OAK_LEAVES = "minecraft:oak_leaves"
        const val OAK_SAPLING = "minecraft:oak_sapling"
        const val STRIPPED_OAK_LOG = "minecraft:stripped_oak_log"
        const val ACACIA_LOG = "minecraft:acacia_log"
        const val ACACIA_LEAVES = "minecraft:acacia_leaves"
        const val DARK_OAK_LOG = "minecraft:dark_oak_log"
        const val DARK_OAK_LEAVES = "minecraft:dark_oak_leaves"
        const val DARK_OAK_SAPLING = "minecraft:dark_oak_sapling"
        const val JUNGLE_LOG = "minecraft:jungle_log"
        const val JUNGLE_LEAVES = "minecraft:jungle_leaves"
        const val SPRUCE_SAPLING = "minecraft:spruce_sapling"

        /** The limits of the default role file (CD-LUM-26). */
        const val MAX_LOGS = 256
        const val MAX_LEAVES = 1_024

        fun rl(id: String): ResourceLocation = ResourceLocation.parse(id)
    }
}
