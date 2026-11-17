package codes.honor.cobblemondynamics.compat.ae2

import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import com.google.gson.JsonObject

/** The pure rule of the Cranker on the AE2 Crank (CD-KIN-06). */
object Ae2CrankRule {
    /** A player's turn spins AE2 19.2.19's crank for 18 ticks before the machine gets it, 160 AE each (`CrankBlockEntity`). */
    const val DEFAULT_INTERVAL_TICKS = 18
    private const val INTERVAL_TICKS = "interval_ticks"

    /** CD-KIN-06: a turn on the tick that the work starts and every [intervalTicks] after it, while the machine takes one. */
    fun shouldTurn(canTurn: Boolean, since: Long, now: Long, intervalTicks: Int): Boolean =
        canTurn && TickClock.every(intervalTicks, since, now)

    fun intervalOf(params: JsonObject): Int =
        params.get(INTERVAL_TICKS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_INTERVAL_TICKS
}
