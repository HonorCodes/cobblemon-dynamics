package codes.honor.cobblemondynamics.xp

import java.util.Locale

/**
 * The XP of one Experience Collector, from 0 up to [capacity] (CD-XPC-03). A store that holds more than a lowered capacity keeps its
 * XP and takes no more until it holds less. It names no game type, so the unit tests, the block entity, its outputs and the Soul
 * Collector share it.
 */
class CollectorStore(stored: Int = 0, val capacity: () -> Int) {
    var stored: Int = maxOf(0, stored)
        private set

    /** The XP that the store takes now: none at or above the capacity. */
    val room: Int get() = roomUnder(capacity())

    /**
     * The XP that the store takes under [limit], a capacity read once, so that the inserts of one batch see one capacity whatever a config
     * reload does meanwhile (CD-SOU-07).
     */
    fun roomUnder(limit: Int): Int = maxOf(0, limit - stored)

    /** Sets the XP from a save or an item. It keeps XP above the capacity, so a lowered capacity loses none. */
    fun load(xp: Int) {
        stored = maxOf(0, xp)
    }

    /** Takes up to [amount], never past [limit], the capacity unless the caller read it once for a batch, and returns what it took. */
    fun insert(amount: Int, limit: Int = capacity()): Int {
        val taken = amount.coerceIn(0, roomUnder(limit))
        stored += taken
        return taken
    }

    /** Removes up to [amount], or only counts it when [simulate] is true, and returns what it removed. */
    fun extract(amount: Int, simulate: Boolean): Int {
        val given = amount.coerceIn(0, stored)
        if (!simulate) stored -= given
        return given
    }

    /** CD-XPC-12: 0 when empty, else 1 + floor(14 × stored ÷ capacity), so 15 means full, as for the Charge Cell (CD-GEN-10). */
    fun comparator(): Int {
        val capacity = capacity()
        if (stored <= 0 || capacity <= 0) return 0
        return (1 + 14L * minOf(stored, capacity) / capacity).toInt()
    }

    companion object {
        /** An XP count with a comma between thousands in every language, as in the tooltip "12,345 / 100,000 XP" (CD-XPC-13). */
        fun format(xp: Int): String = String.format(Locale.ROOT, "%,d", xp)
    }
}
