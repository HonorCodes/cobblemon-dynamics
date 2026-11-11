package codes.honor.cobblemondynamics.cell

import java.util.Locale

/** The Charge Cell's numbers and rules. They name no game type, so unit tests cover them. */
object ChargeCellRules {
    /** CD-GEN-08: the FE that one cell holds. */
    const val CAPACITY = 50_000

    /** CD-GEN-10: 0 when empty, else 1 + floor(14 × stored ÷ capacity), so 15 means full, as a vanilla container's signal. */
    fun comparatorSignal(stored: Int, capacity: Int = CAPACITY): Int {
        if (stored <= 0 || capacity <= 0) return 0
        return (1 + 14L * minOf(stored, capacity) / capacity).toInt()
    }

    /**
     * CD-GEN-09: splits [total] FE between faces that take at most [limits] each. Each face gets an equal share, and what a face
     * cannot take goes to the others, so a face gets less than another only when it takes no more. FE that does not divide evenly
     * goes one each to the faces in turn, from index [first] on.
     */
    fun split(total: Int, limits: IntArray, first: Int): IntArray {
        val given = IntArray(limits.size)
        var left = total
        var open = limits.indices.filter { limits[it] > 0 }
        while (left > 0 && open.isNotEmpty()) {
            val share = left / open.size
            if (share == 0) {
                // Each open face has room for 1 more, and fewer FE are left than open faces.
                open.sortedBy { Math.floorMod(it - first, limits.size) }.take(left).forEach { given[it]++ }
                break
            }
            for (i in open) {
                val give = minOf(share, limits[i] - given[i])
                given[i] += give
                left -= give
            }
            open = open.filter { given[it] < limits[it] }
        }
        return given
    }

    /**
     * CD-GEN-09: the FE that a cell holding [mine] gives each neighbouring cell holding [theirs], so that it and every cell that
     * holds less end at one level, and the giver keeps at least that level. FE then flows only into a cell that holds less, never
     * back, so it cannot loop between cells. A cell that holds as much or more gets nothing.
     */
    fun levelWith(mine: Int, theirs: IntArray): IntArray {
        var sum = mine.toLong()
        var count = 1L
        for (fe in theirs.sorted()) {
            if (fe * count >= sum) break
            sum += fe
            count++
        }
        val level = sum / count
        return IntArray(theirs.size) { maxOf(0L, level - theirs[it]).toInt() }
    }

    /** CD-GEN-11: the tooltip's FE count, with a comma between thousands in every language. */
    fun formatFe(fe: Int): String = String.format(Locale.ROOT, "%,d", fe)
}

/** CD-GEN-08: the FE of one cell, from 0 to [capacity]. */
class CellStore(val capacity: Int = ChargeCellRules.CAPACITY) {
    var stored: Int = 0
        private set

    /** Sets the FE from a save or an item, kept inside 0 to [capacity]. */
    fun load(fe: Int) {
        stored = fe.coerceIn(0, capacity)
    }

    /** Takes up to [max] FE, or only counts it when [simulate] is true, and returns the FE taken. */
    fun receive(max: Int, simulate: Boolean): Int {
        val taken = max.coerceIn(0, capacity - stored)
        if (!simulate) stored += taken
        return taken
    }

    /** Gives up to [max] FE, or only counts it when [simulate] is true, and returns the FE given. */
    fun extract(max: Int, simulate: Boolean): Int {
        val given = max.coerceIn(0, stored)
        if (!simulate) stored -= given
        return given
    }
}
