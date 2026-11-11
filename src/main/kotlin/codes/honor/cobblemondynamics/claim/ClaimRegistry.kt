package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import java.util.UUID

/** The Work Leash bindings, seen from the claim registry: the worker that a binding of an exclusive role reserves a target for. */
fun interface ReservationView {
    fun reservedFor(key: TargetKey): UUID?
}

/**
 * In-memory only (CD-CLM-07). Scans rebuild every claim after a restart. A target holds at most one exclusive claim (CD-CLM-01) and,
 * apart from it, up to [sharedCap] shared claims of logistics roles (CD-CLM-10). The two kinds never block each other (CD-CLM-11).
 */
class ClaimRegistry(private val sharedCap: () -> Int = { Int.MAX_VALUE }, private val reservations: ReservationView) {
    private val byKey = HashMap<TargetKey, Claim>()
    private val sharedByKey = HashMap<TargetKey, LinkedHashMap<UUID, Claim>>()
    private val byWorker = HashMap<UUID, Claim>()

    /**
     * [pasture] is null for a Quick Work order, whose claim no pasture holds, so no release by pasture ends it. Only a work claim is
     * [shared].
     */
    fun tryClaim(worker: UUID, key: TargetKey, kind: ClaimKind, pasture: PastureKey?, now: Long, shared: Boolean = false): ClaimResult {
        require(!shared || kind == ClaimKind.WORK) { "a $kind claim is never shared" }
        if (worker in byWorker) return ClaimResult.WorkerBusy
        if (shared) {
            if (!hasPlace(key)) return ClaimResult.Full
        } else {
            if (key in byKey) return ClaimResult.TargetTaken
            // Only work claims check reservations (CD-CLM-06). A reservation holds no worker entry, so the bound worker can still
            // take a bed or heal claim (CD-CLM-09).
            if (kind == ClaimKind.WORK) {
                val bound = reservations.reservedFor(key)
                if (bound != null && bound != worker) return ClaimResult.Reserved
            }
        }
        val claim = Claim(worker, key, kind, pasture, now, shared)
        if (shared) sharedByKey.getOrPut(key) { LinkedHashMap() }[worker] = claim else byKey[key] = claim
        byWorker[worker] = claim
        return ClaimResult.Granted(claim)
    }

    /** CD-CLM-10: whether [key] takes one more shared claim. */
    fun hasPlace(key: TargetKey): Boolean = (sharedByKey[key]?.size ?: 0) < sharedCap()

    fun claimOf(worker: UUID): Claim? = byWorker[worker]

    /** The exclusive claim on [key]. The shared claims are [sharedOn]. */
    fun holderOf(key: TargetKey): Claim? = byKey[key]

    /** The shared claims on [key], oldest first. */
    fun sharedOn(key: TargetKey): List<Claim> = sharedByKey[key]?.values?.toList().orEmpty()

    /** True when [key] has no exclusive claim and no reservation for a worker other than [forWorker]. Shared claims never count. */
    fun isFree(key: TargetKey, forWorker: UUID): Boolean {
        if (key in byKey) return false
        val bound = reservations.reservedFor(key)
        return bound == null || bound == forWorker
    }

    fun release(worker: UUID): Claim? = byWorker.remove(worker)?.also(::unindex)

    fun releaseAll(predicate: (Claim) -> Boolean): List<Claim> = all().filter(predicate).onEach {
        byWorker.remove(it.worker)
        unindex(it)
    }

    /** Every claim: the exclusive ones, then the shared ones. */
    fun all(): List<Claim> = byKey.values + sharedByKey.values.flatMap { it.values }

    fun clear() {
        byKey.clear()
        sharedByKey.clear()
        byWorker.clear()
    }

    private fun unindex(claim: Claim) {
        if (!claim.shared) {
            byKey.remove(claim.key)
            return
        }
        val holders = sharedByKey[claim.key] ?: return
        holders.remove(claim.worker)
        if (holders.isEmpty()) sharedByKey.remove(claim.key)
    }
}
