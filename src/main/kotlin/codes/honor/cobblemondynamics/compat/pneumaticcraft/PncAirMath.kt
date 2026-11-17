package codes.honor.cobblemondynamics.compat.pneumaticcraft

import com.google.gson.JsonObject
import kotlin.math.ceil

/** `air_per_tick_at_level_100` (50 mL) of `tailwind_pneumaticcraft.json`, an Advanced Air Compressor's output (BM10). */
data class PncAirParams(val airPerTickAt100: Double) {
    companion object {
        private const val AIR = "air_per_tick_at_level_100"
        private const val DEFAULT_AIR = 50.0

        /** A key with the wrong type takes its default. */
        fun of(params: JsonObject): PncAirParams =
            PncAirParams(params.get(AIR)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: DEFAULT_AIR)
    }
}

/** Tailwind's numbers on PneumaticCraft air. No PneumaticCraft class, so unit tests load them. */
object PncAirMath {
    /**
     * CD-FUEL-08, CD-AIR-08: the lowest of a machine's own danger pressure and those of the air handlers connected to it. PneumaticCraft
     * spreads air into connected handlers with no tier check, so a 20 bar tube beside a 5 bar machine must stop below 5 bar.
     */
    fun lowestDanger(own: Float, connected: List<Float>): Float = connected.fold(own) { lowest, next -> minOf(lowest, next) }

    /** CD-AIR-07: ceil(A × L ÷ 100) mL per tick, with L clamped to 1..100. */
    fun airPerTick(airAt100: Double, level: Int): Int = ceil(airAt100 * level.coerceIn(1, 100) / 100.0).toInt().coerceAtLeast(0)

    /**
     * CD-AIR-08: the most of [air] that leaves the pressure below [dangerPressure], 0 when none fits. PneumaticCraft's pressure is the
     * stored air over the volume, divided as floats, and `addAir` has no pressure guard (RES12), so the result is checked in floats too.
     */
    fun allowed(air: Int, stored: Int, volume: Int, dangerPressure: Float): Int {
        if (air <= 0 || volume <= 0) return 0
        val room = ceil(dangerPressure.toDouble() * volume - stored).toLong() - 1
        var add = minOf(air.toLong(), room).coerceAtLeast(0)
        while (add > 0 && (stored + add).toFloat() / volume.toFloat() >= dangerPressure) add--
        return add.toInt()
    }
}
