package codes.honor.cobblemondynamics.compat.irons_spellbooks

import com.google.gson.JsonObject

/** `extra_steps_per_tick` (default 1) of `brewer_irons_spellbooks.json`. A key with the wrong type takes its default. */
data class CauldronParams(val extraStepsPerTick: Int) {
    companion object {
        private const val STEPS = "extra_steps_per_tick"
        private const val DEFAULT_STEPS = 1

        fun of(params: JsonObject): CauldronParams =
            CauldronParams(params.get(STEPS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_STEPS)
    }
}

/**
 * The Brewer's rules on the Alchemist Cauldron of Iron's Spells 'n Spellbooks, over plain cook times, so that unit tests load them with
 * no class of that mod. Each tick, the cauldron counts the cook time of each slot that holds an item up by 1 while it boils, and
 * melts the item in the tick that takes the count past [END] (RES11).
 */
object CauldronRule {
    /** The last cook time before an item melts: 100 ticks per item. */
    const val END = 100

    /** A cook time that runs and can take one more step before its end. */
    private fun running(cooktime: Int): Boolean = cooktime in 1 until END

    /** CD-MCH-07: the demand: a cook time runs below its end. */
    fun cooking(cooktimes: IntArray): Boolean = cooktimes.any(::running)

    /**
     * CD-MCH-07: each running cook time [steps] further, never past [END], so the item melts on the cauldron's own step, as the
     * Brewer leaves the last step of a brew to vanilla (CD-BRW-02).
     */
    fun advance(cooktimes: IntArray, steps: Int) {
        for (slot in cooktimes.indices) {
            if (running(cooktimes[slot])) cooktimes[slot] = minOf(END, cooktimes[slot] + steps.coerceAtLeast(0))
        }
    }
}
