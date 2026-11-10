package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import java.util.UUID

enum class ClaimKind { WORK, BED, HEAL }

data class Claim(val worker: UUID, val key: TargetKey, val kind: ClaimKind, val pasture: PastureKey, val sinceTick: Long)

sealed interface ClaimResult {
    data class Granted(val claim: Claim) : ClaimResult

    data object TargetTaken : ClaimResult

    data object WorkerBusy : ClaimResult

    data object Reserved : ClaimResult
}
