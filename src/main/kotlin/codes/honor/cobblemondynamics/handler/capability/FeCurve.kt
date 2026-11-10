package codes.honor.cobblemondynamics.handler.capability

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.pow
import kotlin.math.roundToLong

/** The Generator's output per tick: an exponential curve from the role file's level-1 rate to its level-100 rate. */
object FeCurve {
    private const val MIN_LEVEL = 1
    private const val MAX_LEVEL = 100

    /**
     * round(m × base × (max ÷ base)^((L − 1) ÷ 99)), rounding last (CD-GEN-02). A level outside 1 to 100 takes the nearest end, so
     * `max` stays the most that a worker gives under a raised level cap (CD-BAL-01). `IEnergyStorage` takes an int, which bounds the
     * result.
     */
    fun fePerTick(level: Int, rare: Boolean, base: Int, max: Int, rareMultiplier: Double): Int {
        if (base <= 0 || max <= 0) return 0
        val l = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        val m = if (rare) rareMultiplier else 1.0
        val raw = m * base * (max.toDouble() / base).pow((l - MIN_LEVEL).toDouble() / (MAX_LEVEL - MIN_LEVEL))
        if (raw.isNaN() || raw <= 0.0) return 0
        return raw.roundToLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** CD-GEN-03: only the listed labels count, without case, as Cobblemon's `Pokemon.hasLabels` compares them. */
    fun isRare(labels: Set<String>, rareLabels: Set<String>): Boolean =
        labels.any { label -> rareLabels.any { it.equals(label, ignoreCase = true) } }
}

/**
 * The Generator's numbers from its role file's `params` (CD-CFG-03), applied with no cap beyond them (CD-CFG-13). A key that a file
 * leaves out, or gives the wrong type, takes the value of the mod's own `generator.json`.
 */
data class GeneratorParams(val base: Int, val max: Int, val rareMultiplier: Double, val rareLabels: Set<String>) {
    /** The FE that a worker of [level] with [labels], its form's labels, offers per tick. */
    fun fePerTick(level: Int, labels: Set<String>): Int =
        FeCurve.fePerTick(level, FeCurve.isRare(labels, rareLabels), base, max, rareMultiplier)

    companion object {
        private const val BASE = "base_fe_per_tick"
        private const val MAX = "max_fe_per_tick"
        private const val RARE_MULTIPLIER = "rare_multiplier"
        private const val RARE_LABELS = "rare_labels"

        private val DEFAULT = GeneratorParams(10, 5_000, 2.0, setOf("legendary", "mythical"))

        fun of(params: JsonObject): GeneratorParams = GeneratorParams(
            base = number(params.get(BASE))?.toInt() ?: DEFAULT.base,
            max = number(params.get(MAX))?.toInt() ?: DEFAULT.max,
            rareMultiplier = number(params.get(RARE_MULTIPLIER))?.toDouble() ?: DEFAULT.rareMultiplier,
            rareLabels = labels(params.get(RARE_LABELS)) ?: DEFAULT.rareLabels,
        )

        private fun number(element: JsonElement?): Number? = element?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber

        private fun labels(element: JsonElement?): Set<String>? {
            val array = element?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            return array.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.mapTo(LinkedHashSet()) { it.asString.lowercase() }
        }
    }
}
