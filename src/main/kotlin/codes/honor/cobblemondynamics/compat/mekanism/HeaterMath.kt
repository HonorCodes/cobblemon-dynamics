package codes.honor.cobblemondynamics.compat.mekanism

import com.google.gson.JsonObject
import kotlin.math.ceil

/** The Heater's numbers on Mekanism heat. No Mekanism class, so unit tests load them. */
object HeaterMath {
    /** The temperature in kelvin above which a Thermal Evaporation Plant runs no faster (RES10). */
    const val MAX_TEMPERATURE = 3_000.0

    /** CD-HTR-04: ceil(H × L ÷ 100) J per tick, with L clamped to 1..100, so level 100 gives H (CD-BAL-13). */
    fun heatPerTick(heatAt100: Double, level: Int): Int = ceil(heatAt100 * level.coerceIn(1, 100) / 100.0).toInt()

    /** CD-HTR-05: a capacitor of the target is below 3,000 K. An unformed plant has none. */
    fun hasDemand(temperatures: List<Double>): Boolean = temperatures.any { it < MAX_TEMPERATURE }

    /**
     * CD-HTR-02, CD-HTR-03: only a block of `#cobblemon_dynamics:heater_targets` that exposes `mekanism:heat_handler`. A reactor port,
     * the Heat Generator and the Entangloporter expose it too, outside the tag. The capability is asked only for a block of the tag.
     */
    fun isTarget(inTag: Boolean, exposesHeat: () -> Boolean): Boolean = inTag && exposesHeat()
}

/** `heat_per_tick_at_level_100` (default 1,500) of `heater_mekanism.json`. A key with the wrong type takes its default. */
data class HeaterParams(val heatPerTickAt100: Double) {
    companion object {
        private const val HEAT = "heat_per_tick_at_level_100"
        private const val DEFAULT_HEAT = 1_500.0

        fun of(params: JsonObject): HeaterParams =
            HeaterParams(params.get(HEAT)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: DEFAULT_HEAT)
    }
}
