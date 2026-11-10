package codes.honor.cobblemondynamics.handler.vanilla

/** Work that repeats at a fixed interval, counted from the tick that the work started. */
object TickClock {
    /** On the tick [since] and every [intervalTicks] ticks after it (CD-CRY-02). An interval below 1 counts as 1. */
    fun every(intervalTicks: Int, since: Long, now: Long): Boolean = now >= since && (now - since) % maxOf(1, intervalTicks) == 0L
}
