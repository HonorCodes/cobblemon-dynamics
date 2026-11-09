package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.pasture.PastureKey
import java.util.UUID

/** The engine's runtime state of one tethered Pokémon. [WorkerMachine] makes every status change. */
class Worker(val pokemonId: UUID, var pasture: PastureKey) {
    var status: WorkerStatus = WorkerStatus.IDLE
    var tired: Boolean = false
    var leashed: Boolean = false

    /** The tick of the current work claim. */
    var claimTick: Long = -1

    /** The tick of the last status change. */
    var statusSince: Long = 0

    /** The leashed retry after a missed arrival, or -1 when the worker is not waiting. */
    var retryAt: Long = -1
    var missedArrival: Boolean = false

    /** Targets that this worker skips until a tick, after a travel timeout (CD-ASG-14). */
    val skipUntil: MutableMap<TargetKey, Long> = HashMap()

    fun isSkipped(key: TargetKey, now: Long): Boolean = now < (skipUntil[key] ?: Long.MIN_VALUE)
}
