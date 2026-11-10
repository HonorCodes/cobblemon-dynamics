package codes.honor.cobblemondynamics.handler.capability

import com.google.gson.JsonObject
import kotlin.math.roundToLong

/**
 * The Pump's rate from its role file's `params`, applied with no cap beyond it (CD-CFG-13). A key that a file leaves out, or gives
 * the wrong type, takes the value of the mod's own `pump.json`.
 */
data class PumpParams(val mbPerLevel: Double) {
    /** CD-PMP-03: `mb_per_level` × L mB of water per tick, rounded, inside an int because `IFluidHandler` takes one. */
    fun mbPerTick(level: Int): Int {
        val raw = mbPerLevel * level.coerceAtLeast(0)
        if (raw.isNaN() || raw <= 0.0) return 0
        return raw.roundToLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        private const val MB_PER_LEVEL = "mb_per_level"

        private val DEFAULT = PumpParams(5.0)

        fun of(params: JsonObject): PumpParams {
            val rate = params.get(MB_PER_LEVEL)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            return PumpParams(rate ?: DEFAULT.mbPerLevel)
        }
    }
}
