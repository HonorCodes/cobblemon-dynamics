package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.util.UUID

/** The engine's state of one pasture in Work Mode: its workers, its scan schedule and its running scan pass. */
class PastureRuntime(val level: ServerLevel, val key: PastureKey, now: Long, scanIntervalTicks: Int) {
    /**
     * One worker with the live objects that its tick needs. [role] is set while the worker holds a work claim, and [focus]
     * while it is Working. [binding] is the leash binding that each poll reads from the Pokémon, when it names this pasture.
     */
    class Slot(val worker: Worker, var entity: PokemonEntity, var pokemon: Pokemon) {
        var role: ActiveRole? = null
        var focus: WorkFocusEntity? = null
        var binding: Binding? = null

        /** How the worker recovers, from its types and its binding at each poll (CD-HEAL-13). */
        var rule: RecoveryRule = RecoveryRule.WORKER

        /** The state and tick of the last flush of Work Stamina, null before the first one. */
        var flushedStatus: WorkerStatus? = null
        var flushedAt: Long = 0

        /** The last `stamina_view` that the engine set, and the entity it set it on. A new entity has none yet. */
        var viewEntity: PokemonEntity? = null
        var viewPercent: Double = 0.0
        var viewStatus: WorkerStatus = WorkerStatus.IDLE
    }

    val pos: BlockPos = BlockPos.of(key.pos)

    /** In the order the workers joined. */
    val slots = LinkedHashMap<UUID, Slot>()

    var scan: TargetScan? = null
    var scheduler: ScanScheduler = scheduler(scanIntervalTicks, now)
        private set

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
