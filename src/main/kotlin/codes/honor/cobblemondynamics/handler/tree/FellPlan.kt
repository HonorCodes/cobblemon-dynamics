package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/** One block of a fell: its cell, its block id when the plan was made, and whether it is of the shell (CD-LUM-14). */
data class FellStep(val pos: BlockPos, val blockId: ResourceLocation, val shell: Boolean)

/**
 * A fell (ADR-0017, decisions 4, 7 and 8). [steps] go in order: the shell, then the leaves, then the logs, each from the top. The shell
 * goes with no shape update, and the rest with one. [questions] names one block for each chunk and block id of the fell, the first one
 * in the order of the steps, because each claim mod decides by chunk and the edit whitelist of FTB Chunks by block (CD-LUM-12).
 */
data class FellPlan(val base: BlockPos, val steps: List<FellStep>, val questions: List<BlockPos>, val twoByTwo: Boolean) {
    /** The steps of each tick: at most [blocksPerTick] (CD-LUM-14). */
    fun batches(blocksPerTick: Int): List<List<FellStep>> = steps.chunked(blocksPerTick.coerceAtLeast(1))

    /** One chunk and one block id, which a claim mod answers alike for each block. */
    private data class Question(val chunkX: Int, val chunkZ: Int, val blockId: ResourceLocation)

    companion object {
        /** From the top, then by the least coordinates. */
        private val TOP_DOWN: Comparator<BlockPos> = compareByDescending<BlockPos> { it.y }.thenBy { it.x }.thenBy { it.z }

        /** The fell of [tree] and its [shell]. [blockIdOf] reads each cell's block id. */
        fun of(tree: Tree, shell: Set<BlockPos>, blockIdOf: (BlockPos) -> ResourceLocation): FellPlan {
            val steps = ArrayList<FellStep>(shell.size + tree.leaves.size + tree.logs.size)
            for (pos in shell.sortedWith(TOP_DOWN)) steps += FellStep(pos, blockIdOf(pos), shell = true)
            for (part in listOf(tree.leaves, tree.logs)) {
                for (pos in part.sortedWith(TOP_DOWN)) steps += FellStep(pos, blockIdOf(pos), shell = false)
            }
            val questions = LinkedHashMap<Question, BlockPos>()
            for (step in steps) questions.putIfAbsent(Question(step.pos.x shr 4, step.pos.z shr 4, step.blockId), step.pos)
            val twoByTwo = TreeRules.square(tree.base).all { it in tree.logs }
            return FellPlan(tree.base, steps, questions.values.toList(), twoByTwo)
        }

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
