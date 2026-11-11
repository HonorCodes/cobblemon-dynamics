package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.util.UUID

/** The engine's state of one pasture in Work Mode: its workers, its scan schedule and its running scan pass. */
class PastureRuntime(override val level: ServerLevel, val key: PastureKey, now: Long, scanIntervalTicks: Int) : WorkSite {
    val pos: BlockPos = BlockPos.of(key.pos)

    override val pastureKey: PastureKey get() = key

    override val slots = LinkedHashMap<UUID, WorkerSlot>()

    /** Centered on the pasture block and clamped to Cobblemon's `pastureMaxWanderDistance` (CD-CFG-14). */
    override fun area(settings: EngineSettings): WorkArea = WorkArea.of(pos, settings, PastureAdapter.maxWanderDistance())

    var scan: TargetScan? = null
    var scheduler: ScanScheduler = scheduler(scanIntervalTicks, now)
        private set

    /**
     * The UUID of the pasture's placer, as the last poll read it, or null for a pasture with no placer. Every pasture check of the
     * land claim gate asks about it, online or not (CD-LND-05).
     */
    var placer: UUID? = null

    private val pollPhase = ScanScheduler.phaseFor(key.pos, POLL_TICKS)
    private var polled = false

    /** The first tick of a new runtime polls at once. After that, polls are staggered by the pasture's position. */
    fun isPollDue(now: Long): Boolean = !polled || Math.floorMod(now - pollPhase, POLL_TICKS.toLong()) == 0L

    fun markPolled() {
        polled = true
    }

    /** The next engine tick polls this pasture, for example after a new leash binding. */
    fun requestPoll() {
        polled = false
    }

    /** A changed scan interval takes effect at the first boundary of the new interval after the running pass. */
    fun syncScanInterval(intervalTicks: Int, now: Long) {
        if (scan == null && scheduler.intervalTicks != intervalTicks) scheduler = scheduler(intervalTicks, now)
    }

    private fun scheduler(intervalTicks: Int, now: Long): ScanScheduler {
        val phase = ScanScheduler.phaseFor(key.pos, intervalTicks)
        return ScanScheduler(intervalTicks, firstStart = now + Math.floorMod(phase - now, intervalTicks.toLong()))
    }

    companion object {
        /** Cobblemon fires no event when a Pokémon leaves a pasture, so the engine reads the tethered list this often (CD-ASG-15). */
        const val POLL_TICKS = 20
    }
}
