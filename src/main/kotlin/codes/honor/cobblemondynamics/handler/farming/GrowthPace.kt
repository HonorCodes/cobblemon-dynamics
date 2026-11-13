package codes.honor.cobblemondynamics.handler.farming

/** The pace of a Cultivator's growth uses (CD-CUL-04). */
object GrowthPace {
    /**
     * CD-CUL-04: ceil(1,200 ÷ ceil(U × L ÷ 100)) ticks, with L in 1..100, so no level above 100 passes CD-BAL-08. At least 1 tick,
     * so a tick holds at most one use (CD-CRP-20).
     */
    fun intervalTicks(level: Int, usesPerMinuteAt100: Int): Int {
        val perMinute = Math.ceilDiv(usesPerMinuteAt100.coerceIn(0, TICKS_PER_MINUTE) * level.coerceIn(1, 100), 100).coerceAtLeast(1)
        return Math.ceilDiv(TICKS_PER_MINUTE, perMinute)
    }

    private const val TICKS_PER_MINUTE = 1_200
}
