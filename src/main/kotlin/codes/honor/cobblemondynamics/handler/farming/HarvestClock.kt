package codes.honor.cobblemondynamics.handler.farming

import com.google.gson.JsonObject

/** When a Harvester harvests: once, `harvest_ticks` after its work started (CD-HRV-05). */
object HarvestClock {
    private const val HARVEST_TICKS = "harvest_ticks"
    private const val DEFAULT_TICKS = 20

    /** True only on the tick [since] + [harvestTicks]. A value below 1 counts as 1, so a tick holds at most one harvest (CD-CRP-20). */
    fun isHarvestTick(since: Long, now: Long, harvestTicks: Int): Boolean = now == since + harvestTicks.coerceAtLeast(1)

    /** CD-HRV-15: `harvest_ticks` of the role file (default 20). A key of another type takes the default. */
    fun ticksOf(params: JsonObject): Int =
        params.get(HARVEST_TICKS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_TICKS
}
