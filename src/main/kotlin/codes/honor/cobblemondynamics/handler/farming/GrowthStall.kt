package codes.honor.cobblemondynamics.handler.farming

/** CD-CUL-09: 20 growth uses in a row that change no block of the crop stop its demand for 6,000 ticks. */
class GrowthStall(private val limit: Int = 20, val waitTicks: Long = 6_000) {
    private var unchanged = 0
    private var stalledAt = Long.MIN_VALUE

    /** Records one growth use at [now]. Returns true when this use stalls the crop. */
    fun record(changed: Boolean, now: Long): Boolean {
        if (changed) {
            unchanged = 0
            return false
        }
        unchanged++
        if (unchanged < limit) return false
        unchanged = 0
        stalledAt = now
        return true
    }

    fun isStalled(now: Long): Boolean = stalledAt != Long.MIN_VALUE && now - stalledAt < waitTicks
}
