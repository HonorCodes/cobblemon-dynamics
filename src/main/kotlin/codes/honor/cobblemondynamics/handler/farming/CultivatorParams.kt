package codes.honor.cobblemondynamics.handler.farming

import com.google.gson.JsonObject

/** The Cultivator's numbers in its role file (CD-CUL-08). */
data class CultivatorParams(val usesPerMinuteAt100: Int, val staminaTicksPerUse: Int) {
    companion object {
        private const val USES = "uses_per_minute_at_level_100"
        private const val STAMINA = "stamina_ticks_per_use"
        private val DEFAULT = CultivatorParams(30, 100)

        /** `uses_per_minute_at_level_100` (default 30) and `stamina_ticks_per_use` (default 100). A key of another type takes its default. */
        fun of(params: JsonObject): CultivatorParams = CultivatorParams(
            int(params, USES) ?: DEFAULT.usesPerMinuteAt100,
            int(params, STAMINA)?.coerceAtLeast(0) ?: DEFAULT.staminaTicksPerUse,
        )

        private fun int(params: JsonObject, key: String): Int? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }
}
