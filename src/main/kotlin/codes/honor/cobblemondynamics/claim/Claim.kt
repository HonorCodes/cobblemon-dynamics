package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import java.util.UUID

enum class ClaimKind { WORK, BED, HEAL }

/** [pasture] is the worker's pasture, or null for the work claim of a Quick Work order, which no pasture holds (ADR-0013). */
data class Claim(val worker: UUID, val key: TargetKey, val kind: ClaimKind, val pasture: PastureKey?, val sinceTick: Long)

sealed interface ClaimResult {
    data class Granted(val claim: Claim) : ClaimResult

    data object TargetTaken : ClaimResult

    data object WorkerBusy : ClaimResult

    data object Reserved : ClaimResult
}
