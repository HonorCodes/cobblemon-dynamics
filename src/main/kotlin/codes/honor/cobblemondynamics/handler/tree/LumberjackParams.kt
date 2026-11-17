package codes.honor.cobblemondynamics.handler.tree

import com.google.gson.JsonObject

/** The Lumberjack's numbers in its role file (CD-LUM-26). */
data class LumberjackParams(
    val ticksPerLogAt1: Int,
    val ticksPerLogAt100: Int,
    val maxLogs: Int,
    val maxLeaves: Int,
    val blocksPerTick: Int,
) {
    companion object {
        private const val TICKS_AT_1 = "ticks_per_log_at_level_1"
        private const val TICKS_AT_100 = "ticks_per_log_at_level_100"
        private const val MAX_LOGS = "max_logs"
        private const val MAX_LEAVES = "max_leaves"
        private const val BLOCKS_PER_TICK = "blocks_per_tick"

        val DEFAULT = LumberjackParams(20, 5, 256, 1_024, 128)

        /** The five numbers of the role file, each with its default when the file lacks it or gives another type. */
        fun of(params: JsonObject): LumberjackParams = LumberjackParams(
            int(params, TICKS_AT_1) ?: DEFAULT.ticksPerLogAt1,
            int(params, TICKS_AT_100) ?: DEFAULT.ticksPerLogAt100,
            int(params, MAX_LOGS)?.coerceAtLeast(1) ?: DEFAULT.maxLogs,
            int(params, MAX_LEAVES)?.coerceAtLeast(TreeSearch.MIN_LEAVES) ?: DEFAULT.maxLeaves,
            int(params, BLOCKS_PER_TICK)?.coerceAtLeast(1) ?: DEFAULT.blocksPerTick,
        )

        private fun int(params: JsonObject, key: String): Int? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }
}
