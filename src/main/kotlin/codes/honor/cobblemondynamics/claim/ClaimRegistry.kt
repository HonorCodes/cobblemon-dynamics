package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import java.util.UUID

/** The Work Leash bindings, seen from the claim registry: the worker that a target is reserved for, if any. */
fun interface ReservationView {
    fun reservedFor(key: TargetKey): UUID?
}

/** In-memory only (CD-CLM-07). Scans rebuild every claim after a restart. */
class ClaimRegistry(private val reservations: ReservationView) {
    private val byKey = HashMap<TargetKey, Claim>()
    private val byWorker = HashMap<UUID, Claim>()

    /** [pasture] is null for a Quick Work order, whose claim no pasture holds, so no release by pasture ends it. */
    fun tryClaim(worker: UUID, key: TargetKey, kind: ClaimKind, pasture: PastureKey?, now: Long): ClaimResult {
        if (worker in byWorker) return ClaimResult.WorkerBusy
        if (key in byKey) return ClaimResult.TargetTaken
        // Only work claims check reservations (CD-CLM-06). A reservation holds no worker entry, so the bound worker can still
        // take a bed or heal claim (CD-CLM-09).
        if (kind == ClaimKind.WORK) {
            val bound = reservations.reservedFor(key)
            if (bound != null && bound != worker) return ClaimResult.Reserved
        }
        val claim = Claim(worker, key, kind, pasture, now)
        byKey[key] = claim
        byWorker[worker] = claim
        return ClaimResult.Granted(claim)
    }

    fun claimOf(worker: UUID): Claim? = byWorker[worker]

    fun holderOf(key: TargetKey): Claim? = byKey[key]

    /** True when [key] has no claim and no reservation for a worker other than [forWorker]. */
    fun isFree(key: TargetKey, forWorker: UUID): Boolean {
        if (key in byKey) return false
        val bound = reservations.reservedFor(key)
        return bound == null || bound == forWorker
    }

    fun release(worker: UUID): Claim? = byWorker.remove(worker)?.also { byKey.remove(it.key) }

    fun releaseTarget(key: TargetKey): Claim? = byKey.remove(key)?.also { byWorker.remove(it.worker) }

    fun releaseAll(predicate: (Claim) -> Boolean): List<Claim> = byKey.values.filter(predicate).onEach {
        byKey.remove(it.key)
        byWorker.remove(it.worker)
    }

    fun all(): List<Claim> = byKey.values.toList()

    fun clear() {
        byKey.clear()
        byWorker.clear()
    }
}
