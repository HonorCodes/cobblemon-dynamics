package codes.honor.cobblemondynamics.compat.ars_nouveau

import com.google.gson.JsonObject

/** The pure rules of the Stoker on the Volcanic Sourcelink of Ars Nouveau 5.13.5 (CD-FUEL-12). */
object SourcelinkRule {
    /** A vanilla furnace burns one coal in 1,600 ticks, so the worker is one fuel stream: one coal per 1,600 ticks of Working. */
    const val DEFAULT_INTERVAL_TICKS = 1_600
    private const val INTERVAL_TICKS = "interval_ticks"

    /** The Working ticks counted toward the next gift, and whether this tick gives one. */
    data class Step(val worked: Int, val gift: Boolean)

    /** One tick of Working after [worked] counted ticks: every [intervalTicks]th tick gives, and the count starts again. */
    fun step(worked: Int, intervalTicks: Int): Step {
        val next = worked + 1
        return if (next >= maxOf(1, intervalTicks)) Step(0, gift = true) else Step(next, gift = false)
    }

    /**
     * The Source of a gift: the value that the sourcelink's own `getSourceValue` gives one coal, read only on a tick that gives, because
     * that call also counts the burn toward the sourcelink's own progress, as a coal burned on a pedestal does.
     */
    fun gift(step: Step, coalSource: () -> Int): Int = if (step.gift) coalSource() else 0

    /** A full sourcelink takes no Source, and a player turns one off with redstone. */
    fun hasDemand(canAcceptSource: Boolean, disabled: Boolean): Boolean = canAcceptSource && !disabled

    fun intervalOf(params: JsonObject): Int =
        params.get(INTERVAL_TICKS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_INTERVAL_TICKS
}
