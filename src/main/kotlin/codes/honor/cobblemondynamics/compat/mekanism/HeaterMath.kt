package codes.honor.cobblemondynamics.compat.mekanism

import com.google.gson.JsonObject
import kotlin.math.ceil
import kotlin.math.roundToLong

/** The Heater's numbers on Mekanism heat. No Mekanism class, so unit tests load them. */
object HeaterMath {
    /** The temperature in kelvin above which a Thermal Evaporation Plant runs no faster (RES10). */
    const val MAX_TEMPERATURE = 3_000.0

    /**
     * M6: the Heater's heat stays below what a full plant can use, the heat at which a Thermal Evaporation Plant of height 18 stops
     * running faster, about 1,870 J/t (BM9). No role file passes it (R85).
     */
    const val FULL_PLANT_HEAT = 1_870.0

    /** CD-HTR-04: ceil(H × L ÷ 100) J per tick, with L clamped to 1..100, so level 100 gives H (CD-BAL-13). */
    fun heatPerTick(heatAt100: Double, level: Int, multiplier: Double = 1.0): Int {
        val base = ceil(heatAt100 * level.coerceIn(1, 100) / 100.0)
        if (multiplier == 1.0) return base.toInt()
        val scaled = base * multiplier
        if (scaled.isNaN() || scaled <= 0.0) return 0
        return scaled.roundToLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** CD-HTR-05: a capacitor of the target is below 3,000 K. An unformed plant has none. */
    fun hasDemand(temperatures: List<Double>): Boolean = temperatures.any { it < MAX_TEMPERATURE }

    /**
     * CD-HTR-02, CD-HTR-03: only a block of `#cobblemon_dynamics:heater_targets` that exposes `mekanism:heat_handler`. A reactor port,
     * the Heat Generator and the Entangloporter expose it too, outside the tag. The capability is asked only for a block of the tag.
     */
    fun isTarget(inTag: Boolean, exposesHeat: () -> Boolean): Boolean = inTag && exposesHeat()
}

/**
 * `heat_per_tick_at_level_100` (default 1,500) of `heater_mekanism.json`, from 0 to [HeaterMath.FULL_PLANT_HEAT], as
 * `WatermillParams` keeps its torque. Negative heat would cool the target. A key with the wrong type takes its default.
 */
data class HeaterParams(val heatPerTickAt100: Double) {
    companion object {
        private const val HEAT = "heat_per_tick_at_level_100"
        private const val DEFAULT_HEAT = 1_500.0

        fun of(params: JsonObject): HeaterParams {
            val value = params.get(HEAT)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            return HeaterParams((value ?: DEFAULT_HEAT).coerceIn(0.0, HeaterMath.FULL_PLANT_HEAT))
        }
    }
}
