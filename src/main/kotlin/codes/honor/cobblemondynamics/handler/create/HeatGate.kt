package codes.honor.cobblemondynamics.handler.create

import com.google.gson.JsonObject

/**
 * The heat that a burner role's worker gives, from the role's params: `heat` from `min_level` up (0 when absent), and
 * `below_min_level_heat` under it. Each is the name of a Create heat level, as Create writes it (`kindled`, `seething`).
 */
object HeatGate {
    private const val HEAT = "heat"
    private const val MIN_LEVEL = "min_level"
    private const val BELOW_MIN_LEVEL_HEAT = "below_min_level_heat"

    /** Null when the params give no heat at [level]. */
    fun heatFor(params: JsonObject, level: Int): String? {
        val minLevel = params.get(MIN_LEVEL)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
        return string(params, if (level >= minLevel) HEAT else BELOW_MIN_LEVEL_HEAT)
    }

    private fun string(params: JsonObject, key: String): String? =
        params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.lowercase()
}
