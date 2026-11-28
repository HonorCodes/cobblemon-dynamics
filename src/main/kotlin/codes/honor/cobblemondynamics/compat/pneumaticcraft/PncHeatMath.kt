package codes.honor.cobblemondynamics.compat.pneumaticcraft

import com.google.gson.JsonObject

/**
 * `source_temperature` (1,300 K) and `thermal_resistance` (100) of `heater_pneumaticcraft.json`: the lava block that the Heater copies
 * (BM10). A key with the wrong type takes its default.
 */
data class PncHeaterParams(val sourceTemperature: Double, val resistance: Double) {
    companion object {
        private const val SOURCE = "source_temperature"
        private const val RESISTANCE = "thermal_resistance"
        private const val DEFAULT_SOURCE = 1_300.0
        private const val DEFAULT_RESISTANCE = 100.0

        fun of(params: JsonObject): PncHeaterParams =
            PncHeaterParams(number(params, SOURCE) ?: DEFAULT_SOURCE, number(params, RESISTANCE) ?: DEFAULT_RESISTANCE)

        private fun number(params: JsonObject, key: String): Double? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
    }
}

/** The Heater's numbers on PneumaticCraft heat (R87). No PneumaticCraft class, so unit tests load them. */
object PncHeatMath {
    /**
     * CD-HTR-07: max(0, (S − T) ÷ R) × L ÷ 100 heat per tick into an exchanger at T kelvin, with L clamped to 1..100. Level 100 copies
     * one lava block, so the Heater never warms a machine past the lava's temperature (CD-BAL-13).
     */
    fun heatPerTick(params: PncHeaterParams, temperature: Double, level: Int, multiplier: Double = 1.0): Double {
        if (params.resistance <= 0.0 || multiplier <= 0.0) return 0.0
        return maxOf(0.0, (params.sourceTemperature - temperature) / params.resistance) * level.coerceIn(1, 100) / 100.0 * multiplier
    }

    /** CD-HTR-07: an exchanger colder than the source. */
    fun hasDemand(params: PncHeaterParams, temperature: Double, level: Int): Boolean = heatPerTick(params, temperature, level) > 0.0
}
