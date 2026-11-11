package codes.honor.cobblemondynamics.handler.item

/** When the Magnet looks for dropped items: on its first need, then at most once every [intervalTicks] ticks (CD-MAG-08). */
class QueryClock(private val intervalTicks: Int = TransferParams.BATCH_TICKS) {
    private var last: Long? = null

    fun isDue(now: Long): Boolean = last?.let { now - it >= intervalTicks } ?: true

    fun ran(now: Long) {
        last = now
    }
}
