package codes.honor.cobblemondynamics.handler.water

/** The pace of a Freezer's freezes (CD-FRZ-04). */
object FreezePace {
    private const val TICKS_PER_MINUTE = 1_200

    /**
     * CD-FRZ-04: ceil(1,200 ÷ ceil(F × L ÷ 100)) ticks, with L in 1..100, so no level above 100 passes CD-BAL-12. A pace of 1 or more
     * gives at least 1 cell per minute at every level. With F at 0 or less no freeze ever comes, and the role has no demand (R84).
     */
    fun intervalTicks(level: Int, cellsPerMinuteAt100: Int): Int {
        if (cellsPerMinuteAt100 <= 0) return Int.MAX_VALUE
        val perMinute = Math.ceilDiv(cellsPerMinuteAt100.coerceAtMost(TICKS_PER_MINUTE) * level.coerceIn(1, 100), 100)
        return Math.ceilDiv(TICKS_PER_MINUTE, perMinute)
    }

    /**
     * A freeze comes [intervalTicks] ticks after the work started at [since], and every [intervalTicks] ticks after that. A worker that
     * goes cell to cell starts a new claim each time, so no claim freezes sooner than the pace allows (CD-BAL-12). An interval below 1
     * counts as 1.
     */
    fun isFreezeTick(since: Long, now: Long, intervalTicks: Int): Boolean = now > since && (now - since) % maxOf(1, intervalTicks) == 0L
}
