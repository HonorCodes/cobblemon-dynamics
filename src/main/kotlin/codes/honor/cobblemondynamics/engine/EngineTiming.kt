package codes.honor.cobblemondynamics.engine

import java.util.function.LongSupplier
import kotlin.math.ceil

/** The Dynamics time of the last [capacity] recorded ticks, in nanoseconds (CD-PERF-01, CD-PERF-02). */
class TickTimes(val capacity: Int) {
    private val nanos = LongArray(capacity)
    private var next = 0

    /** The number of ticks held, at most [capacity]. */
    var count = 0
        private set

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun add(tickNanos: Long) {
        nanos[next] = tickNanos
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** The held ticks, oldest first. */
    fun values(): LongArray = LongArray(count) { nanos[(next - count + it + capacity) % capacity] }

    fun meanMillis(): Double {
        check(count > 0) { "no tick recorded" }
        return values().sum() / count.toDouble() / NANOS_PER_MILLI
    }

    /** The nearest-rank percentile: the smallest value that at least [fraction] of the ticks do not exceed. */
    fun percentileMillis(fraction: Double): Double {
        check(count > 0) { "no tick recorded" }
        val sorted = values().sorted()
        // The epsilon keeps a product such as 0.95 * 6000 that lands just above a whole number on that rank.
        val rank = ceil(fraction * count - RANK_EPSILON).toInt().coerceIn(1, count)
        return sorted[rank - 1] / NANOS_PER_MILLI
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
        const val RANK_EPSILON = 1e-9
    }
}

/**
 * Adds up the time of the timed sections of each tick while a recording runs. A section inside another counts once, in the outer
 * one. One thread only.
 */
open class SectionTimer(@PublishedApi internal val clock: LongSupplier) {
    @PublishedApi internal var recording: TickTimes? = null

    @PublishedApi internal var inSection = false

    @PublishedApi internal var tickNanos = 0L

    private var pending = 0

    /** The recording that runs, or null. */
    val recorded: TickTimes? get() = recording

    inline fun <T> time(block: () -> T): T {
        if (recording == null || inSection) return block()
        inSection = true
        val start = clock.asLong
        try {
            return block()
        } finally {
            tickNanos += clock.asLong - start
            inSection = false
        }
    }

    /** The end of a tick. A recording that [startRecording] asked for starts here, so it holds only whole ticks. */
    fun endTick() {
        recording?.add(tickNanos)
        tickNanos = 0
        if (pending > 0) {
            recording = TickTimes(pending)
            pending = 0
        }
    }

    /** Records the totals of the last [ticks] ticks, from the next whole tick on. */
    fun startRecording(ticks: Int) {
        require(ticks > 0) { "ticks must be positive" }
        pending = ticks
    }

    fun stopRecording(): TickTimes? {
        val stopped = recording
        recording = null
        pending = 0
        tickNanos = 0
        return stopped
    }
}

/**
 * The server time of Dynamics' per-tick code (CD-PERF-01, CD-PERF-02): the engine's level ticks, the leash index check, the panel
 * sends and the work Brain tasks. `EngineEvents` ends each tick after the server tick. Server thread only.
 */
object EngineTiming : SectionTimer(LongSupplier { System.nanoTime() })
