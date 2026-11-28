package codes.honor.cobblemondynamics.handler.cobblemon

import com.google.gson.JsonObject

/** `recharge_multiplier_at_level_100` (default 4) of `generator_healing_machine.json`. A key with the wrong type takes its default. */
data class HealingParams(val multiplierAt100: Double) {
    companion object {
        private const val MULTIPLIER = "recharge_multiplier_at_level_100"
        private const val DEFAULT_MULTIPLIER = 4.0

        fun of(params: JsonObject): HealingParams = HealingParams(number(params, MULTIPLIER) ?: DEFAULT_MULTIPLIER)

        private fun number(params: JsonObject, key: String): Double? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
    }
}

/**
 * The Generator's numbers on Cobblemon's Healing Machine, over plain facts, so that unit tests load them with no world. While no player
 * heals at it, Cobblemon 1.8.4's machine recharges `maxCharge` ÷ (`secondsToChargeHealingMachine` × 20) per tick, at most to
 * `maxCharge`, and only that own tick updates the block's charge level and its comparator signal (BM13).
 */
object HealingCharge {
    /** The machine's own recharge per tick, or 0 when the config's recharge time is 0 or less, as Cobblemon's own tick reads it. */
    fun ownPerTick(maxCharge: Float, secondsToCharge: Double): Double {
        val ticks = secondsToCharge.coerceAtLeast(0.0) * 20
        return if (ticks > 0) maxCharge / ticks else 0.0
    }

    /** CD-MCH-08, CD-BAL-16: G × L ÷ 100 times the machine's own rate, with L clamped to 1..100, so level 100 gives G times. */
    fun workerPerTick(multiplierAt100: Double, level: Int, own: Double, multiplier: Double = 1.0): Double =
        multiplierAt100 * level.coerceIn(1, 100) / 100.0 * own * multiplier

    /**
     * CD-MCH-08: the charge that a worker adds in one tick to a machine at [charge]. Nothing while the machine does not recharge itself,
     * because a player heals at it or it never runs out. The worker stops one own step below [maxCharge], so the machine's own tick
     * makes the last step and shows the full charge on its block.
     */
    fun added(charge: Float, maxCharge: Float, own: Double, perTick: Double, recharges: Boolean): Float {
        if (!recharges || own <= 0.0 || perTick <= 0.0) return 0f
        val room = maxCharge - own.toFloat() - charge
        return if (room <= 0f) 0f else minOf(perTick.toFloat(), room)
    }
}
