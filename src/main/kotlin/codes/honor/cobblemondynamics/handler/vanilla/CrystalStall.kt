package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.farming.GrowthStall
import com.google.gson.JsonObject

/**
 * CD-MCH-05: the Crystal Tender's stall, the Cultivator's stall of CD-CUL-09 with its own numbers from the role file. Some budding
 * blocks grow only in some places, such as Just Dire Things' time crystal outside its dimension or Quark's corundum above y 24, so 200
 * extra ticks in a row that change no block of the target and of its faces stop its demand for that worker for 6,000 ticks. A budding
 * block with one free face grows on an extra tick about 1 time in 30, so 200 misses in a row come about 1 time in 900.
 */
object CrystalStall {
    const val DEFAULT_UNCHANGED_TICKS = 200
    const val DEFAULT_WAIT_TICKS = 6_000L
    private const val UNCHANGED_TICKS = "stall_after_unchanged_ticks"
    private const val WAIT_TICKS = "stall_wait_ticks"

    fun of(params: JsonObject): GrowthStall = GrowthStall(
        limit = number(params, UNCHANGED_TICKS)?.toInt() ?: DEFAULT_UNCHANGED_TICKS,
        waitTicks = number(params, WAIT_TICKS)?.toLong() ?: DEFAULT_WAIT_TICKS,
    )

    private fun number(params: JsonObject, key: String): Number? =
        params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber
}
