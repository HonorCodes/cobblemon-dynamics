package codes.honor.cobblemondynamics.handler.xp

import com.google.gson.JsonObject

/** The numbers of the Soul Collector, from its role file's `params` (CD-SOU-10, ARCHITECTURE section 9). */
data class SoulCollectorParams(val orbUnitsPerSecondAtLevel100: Int, val playerDistance: Double) {
    /** CD-SOU-06: ceil(U × L ÷ 100) orb units per 20-tick batch, with L clamped to 1..100, so level 100 takes as a player does (BM7). */
    fun unitsPerBatch(level: Int): Int {
        val rate = orbUnitsPerSecondAtLevel100.coerceAtLeast(0).toLong()
        return ((rate * level.coerceIn(1, 100) + 99) / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        /** One batch every 20 ticks, so the units per batch are the units per second (R52). */
        const val BATCH_TICKS = 20

        private const val RATE = "orb_units_per_second_at_level_100"
        private const val DISTANCE = "player_distance"

        /** 8 blocks is the radius at which vanilla makes an orb follow a player (RES9). */
        val DEFAULT = SoulCollectorParams(10, 8.0)

        /** `orb_units_per_second_at_level_100` (default 10) and `player_distance` (default 8). A key with the wrong type takes its default. */
        fun of(params: JsonObject): SoulCollectorParams = SoulCollectorParams(
            number(params, RATE)?.asInt ?: DEFAULT.orbUnitsPerSecondAtLevel100,
            number(params, DISTANCE)?.asDouble ?: DEFAULT.playerDistance,
        )

        private fun number(params: JsonObject, key: String) =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asJsonPrimitive
    }
}
