package codes.honor.cobblemondynamics.engine

/** When one pasture's scan passes start. Passes never overlap, and each starts on an interval boundary. */
class ScanScheduler(val intervalTicks: Int, firstStart: Long) {
    private var nextStart = firstStart
    private var currentStart = -1L

    val running: Boolean get() = currentStart >= 0

    fun shouldStart(now: Long): Boolean = !running && now >= nextStart

    fun start(now: Long) {
        currentStart = now
    }

    /**
     * [lastWorkTick] is the tick on which the pass did its last work. The next pass starts at the first boundary
     * `start + k × interval` (k ≥ 1) after it (CD-ASG-06).
     */
    fun complete(lastWorkTick: Long) {
        check(running) { "no scan pass is running" }
        val k = (lastWorkTick - currentStart) / intervalTicks + 1
        nextStart = currentStart + k * intervalTicks
        currentStart = -1
    }

    companion object {
        /** A stagger in `0 until interval`, so pastures that load together do not scan on the same tick. */
        fun phaseFor(posHash: Long, interval: Int): Long = Math.floorMod(posHash, interval.toLong())
    }
}
