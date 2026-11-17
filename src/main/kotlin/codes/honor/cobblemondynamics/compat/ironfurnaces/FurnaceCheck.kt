package codes.honor.cobblemondynamics.compat.ironfurnaces

/**
 * The recipe lookup of one furnace of Iron Furnaces: a cached check [check] of its own for its recipe type [type], as the furnace keeps
 * its own, and the result of the last tick that read it. A check that every furnace shared would lose its cached recipe whenever two
 * furnaces smelt different items, and the demand and the work of one tick would each run the lookup (R88). Server thread only.
 */
class FurnaceCheck<T, C>(val type: T, private val check: C) {
    private var tick: Long? = null
    private var canSmelt = false

    /** The result of [lookup] over the furnace's own check, run at most once per tick [now]. */
    fun canSmelt(now: Long, lookup: (C) -> Boolean): Boolean {
        if (tick != now) {
            canSmelt = lookup(check)
            tick = now
        }
        return canSmelt
    }

    companion object {
        /** [current] while it holds the furnace's recipe type [type], or a new check from [create], as an augment changes the type. */
        fun <T, C> forType(current: FurnaceCheck<T, C>?, type: T, create: (T) -> C): FurnaceCheck<T, C> =
            current?.takeIf { it.type == type } ?: FurnaceCheck(type, create(type))
    }
}
