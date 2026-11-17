package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation

/** One block of a fell: its cell, its block id when the plan was made, and whether it is of the shell (CD-LUM-14). */
data class FellStep(val pos: BlockPos, val blockId: ResourceLocation, val shell: Boolean)

/**
 * A fell (ADR-0017, decisions 4, 7 and 8). [steps] go in order: the shell, then the leaves, then the logs, each from the top. The shell
 * goes with no shape update, and the rest with one. [questions] names one block for each chunk and block id of the fell, the first one
 * in the order of the steps, because each claim mod decides by chunk and the edit whitelist of FTB Chunks by block (CD-LUM-12).
 */
data class FellPlan(
    val base: BlockPos,
    val steps: List<FellStep>,
    val questions: List<BlockPos>,
    val twoByTwo: Boolean,
    /** CD-LUM-27: the `ChunkPos.asLong` key of each chunk that the fell changes or drops into. */
    val chunks: Set<Long> = emptySet(),
) {
    /** The steps of each tick: at most [blocksPerTick] (CD-LUM-14). */
    fun batches(blocksPerTick: Int): List<List<FellStep>> = steps.chunked(blocksPerTick.coerceAtLeast(1))

    /** One chunk and one block id, which a claim mod answers alike for each block. */
    private data class Question(val chunkX: Int, val chunkZ: Int, val blockId: ResourceLocation)

    companion object {
        /** From the top, then by the least coordinates. */
        private val TOP_DOWN: Comparator<BlockPos> = compareByDescending<BlockPos> { it.y }.thenBy { it.x }.thenBy { it.z }

        /**
         * The fell of [tree] and its [shell]. [hanging] gives the cell and the entity type of each item frame and painting that hangs on
         * the fell, which the questions name too (CD-LUM-30). [blockIdOf] reads each cell's block id.
         */
        fun of(
            tree: Tree,
            shell: Set<BlockPos>,
            hanging: List<Pair<BlockPos, ResourceLocation>> = emptyList(),
            blockIdOf: (BlockPos) -> ResourceLocation,
        ): FellPlan {
            val steps = ArrayList<FellStep>(shell.size + tree.leaves.size + tree.logs.size)
            for (pos in shell.sortedWith(TOP_DOWN)) steps += FellStep(pos, blockIdOf(pos), shell = true)
            for (part in listOf(tree.leaves, tree.logs)) {
                for (pos in part.sortedWith(TOP_DOWN)) steps += FellStep(pos, blockIdOf(pos), shell = false)
            }
            val questions = LinkedHashMap<Question, BlockPos>()
            for (step in steps) questions.putIfAbsent(Question(step.pos.x shr 4, step.pos.z shr 4, step.blockId), step.pos)
            for ((cell, type) in hanging) questions.putIfAbsent(Question(cell.x shr 4, cell.z shr 4, type), cell)
            val twoByTwo = TreeRules.square(tree.base).all { it in tree.logs }
            // The drops pop out of a face of the base cell, which can lie in the next chunk.
            val cells = steps.map { it.pos } + hanging.map { it.first } + Direction.Plane.HORIZONTAL.map(tree.base::relative)
            val chunks = cells.mapTo(LinkedHashSet()) { chunkKey(it.x shr 4, it.z shr 4) }
            return FellPlan(tree.base, steps, questions.values.toList(), twoByTwo, chunks)
        }

        /** The key of chunk ([x], [z]), as `ChunkPos.asLong` packs it. */
        fun chunkKey(x: Int, z: Int): Long = (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)

        /**
         * CD-LUM-06: [step] still holds the block that the plan took, read again as [cell] when the step comes. A leaf that a player
         * placed there since stays, and so does a cell whose block changed.
         */
        fun stillHolds(step: FellStep, cell: CellFacts): Boolean = cell.blockId == step.blockId && (step.shell || !cell.persistent)

        /**
         * CD-LUM-12: the use question and the break question of each chunk and block id of [plan], and the use question of its base cell
         * for the replant. The first refusal ends the questions.
         */
        fun allowed(plan: FellPlan, mayChange: (BlockPos) -> Boolean, mayBreak: (BlockPos) -> Boolean): Boolean =
            plan.questions.all { mayChange(it) && mayBreak(it) } && mayChange(plan.base)

        /**
         * CD-LUM-16: equal stacks of [loot] merged in the order of their first drop, less the [taken] items of the replant. [same] tells
         * equal stacks. A stack with no items left drops out.
         */
        fun <K> merged(
            loot: List<Pair<K, Int>>,
            taken: List<Pair<K, Int>> = emptyList(),
            same: (K, K) -> Boolean = { a, b -> a == b },
        ): List<Pair<K, Int>> {
            val merged = ArrayList<Pair<K, Int>>()
            fun add(item: K, count: Int) {
                val i = merged.indexOfFirst { same(it.first, item) }
                if (i < 0) merged += item to count else merged[i] = merged[i].first to merged[i].second + count
            }
            for ((item, count) in loot) add(item, count)
            for ((item, count) in taken) if (merged.any { same(it.first, item) }) add(item, -count)
            return merged.filter { it.second > 0 }
        }
    }
}
