package codes.honor.cobblemondynamics.handler.water

import com.google.gson.JsonObject

/** The Freezer's numbers in its role file (CD-FRZ-13). */
data class FreezerParams(val cellsPerMinuteAt100: Int, val reach: Double) {
    companion object {
        private const val CELLS = "cells_per_minute_at_level_100"
        private const val REACH = "reach"

        /** A freeze searches the cube around its worker, so the reach is capped. */
        const val MAX_REACH = 16.0
        private val DEFAULT = FreezerParams(60, 5.0)

        /** `cells_per_minute_at_level_100` (default 60) and `reach` (default 5.0, at most 16). A key of another type takes its default. */
        fun of(params: JsonObject): FreezerParams = FreezerParams(
            number(params, CELLS)?.asInt ?: DEFAULT.cellsPerMinuteAt100,
            number(params, REACH)?.asDouble?.coerceIn(0.0, MAX_REACH) ?: DEFAULT.reach,
        )

        private fun number(params: JsonObject, key: String) =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asJsonPrimitive
    }
}
