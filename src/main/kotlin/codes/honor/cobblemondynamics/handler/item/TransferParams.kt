package codes.honor.cobblemondynamics.handler.item

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import kotlin.math.abs

/** The numbers of Telekinesis and the Magnet, from a role file's `params` (ARCHITECTURE section 9). */
data class TransferParams(val itemsPerSecondAtLevel100: Int, val destinationRange: Int) {
    /** ceil(rate × L ÷ 100) items per 20-tick batch, with L clamped to 1..100 (CD-TLK-03, CD-MAG-03, CD-BAL-05). */
    fun itemsPerBatch(level: Int): Int {
        val rate = itemsPerSecondAtLevel100.coerceAtLeast(0).toLong()
        return ((rate * level.coerceIn(1, 100) + 99) / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** CD-TLK-12: [destination] lies at most [destinationRange] blocks from [pasture] on each axis. */
    fun inReach(pasture: BlockPos, destination: BlockPos): Boolean = abs(destination.x - pasture.x) <= destinationRange &&
        abs(destination.y - pasture.y) <= destinationRange &&
        abs(destination.z - pasture.z) <= destinationRange

    companion object {
        /** R52: one batch every 20 ticks, so the rate per batch is the rate per second. */
        const val BATCH_TICKS = 20

        private const val RATE = "items_per_second_at_level_100"
        private const val RANGE = "destination_range"
        private val DEFAULT = TransferParams(64, 32)

        /**
         * `items_per_second_at_level_100` (default 64) and `destination_range` (default 32). A key with the wrong type takes its
         * default.
         */
        fun of(params: JsonObject): TransferParams = TransferParams(
            int(params, RATE) ?: DEFAULT.itemsPerSecondAtLevel100,
            int(params, RANGE) ?: DEFAULT.destinationRange,
        )

        private fun int(params: JsonObject, key: String): Int? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }
}
