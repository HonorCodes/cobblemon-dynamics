package codes.honor.cobblemondynamics.pasture

import java.util.UUID

/** Who may set a pasture's Work Mode, from where, and how often. */
object WorkModePolicy {
    /** Cobblemon links a player to a pasture only within this distance (CD-SEC-04). */
    const val MAX_DISTANCE = 10.0

    sealed interface Decision {
        data object Apply : Decision

        data object Ignore : Decision

        data class Reject(val reason: String) : Decision
    }

    /** The checks run in this order: owner, then distance and dimension, then rate. */
    fun decide(
        requester: UUID,
        isOperator: Boolean,
        placer: UUID?,
        distance: Double,
        sameDimension: Boolean,
        now: Long,
        lastAccepted: Long?,
        cooldownTicks: Int,
    ): Decision = when {
        !isOperator && (placer == null || placer != requester) -> Decision.Reject("not_owner")
        !sameDimension || distance > MAX_DISTANCE -> Decision.Reject("too_far")
        lastAccepted != null && now - lastAccepted < cooldownTicks -> Decision.Ignore
        else -> Decision.Apply
    }
}
