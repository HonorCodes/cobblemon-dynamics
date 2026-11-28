package codes.honor.cobblemondynamics.pasture

import java.util.UUID

/** Who may set a pasture's Work Mode or Access Lock, from where, and how often. */
object WorkModePolicy {
    /** Cobblemon links a player to a pasture only within this distance (CD-SEC-04). */
    const val MAX_DISTANCE = 10.0

    sealed interface Decision {
        data object Apply : Decision

        data object Ignore : Decision

        data class Reject(val reason: String) : Decision
    }

    fun mayToggle(
        requester: UUID,
        isOperator: Boolean,
        placer: UUID?,
        locked: Boolean = false,
        tetheredOwners: Set<UUID> = emptySet(),
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Boolean = PastureAccessPolicy.mayControl(requester, isOperator, placer, locked, tetheredOwners, areTeammates)

    /** The checks run in this order: access policy, then distance and dimension, then rate. */
    fun decide(
        requester: UUID,
        isOperator: Boolean,
        placer: UUID?,
        distance: Double,
        sameDimension: Boolean,
        now: Long,
        lastAccepted: Long?,
        cooldownTicks: Int,
        locked: Boolean = false,
        tetheredOwners: Set<UUID> = emptySet(),
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Decision = when {
        !mayToggle(requester, isOperator, placer, locked, tetheredOwners, areTeammates) -> Decision.Reject("not_owner")
        !sameDimension || distance > MAX_DISTANCE -> Decision.Reject("too_far")
        lastAccepted != null && now - lastAccepted < cooldownTicks -> Decision.Ignore
        else -> Decision.Apply
    }

    fun decideLock(
        requester: UUID,
        isOperator: Boolean,
        placer: UUID?,
        distance: Double,
        sameDimension: Boolean,
        now: Long,
        lastAccepted: Long?,
        cooldownTicks: Int,
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Decision = when {
        !PastureAccessPolicy.mayLock(requester, isOperator, placer, areTeammates) -> Decision.Reject("not_owner")
        !sameDimension || distance > MAX_DISTANCE -> Decision.Reject("too_far")
        lastAccepted != null && now - lastAccepted < cooldownTicks -> Decision.Ignore
        else -> Decision.Apply
    }

    fun isValidRadiusStep(radius: Int, maxHorizontalRadius: Int): Boolean =
        radius in WorkModeData.RADIUS_STEPS && radius <= maxHorizontalRadius

    fun allowedRadiusSteps(maxHorizontalRadius: Int): List<Int> = WorkModeData.RADIUS_STEPS.filter { it <= maxHorizontalRadius }

    fun decideRadius(
        requester: UUID,
        isOperator: Boolean,
        placer: UUID?,
        radius: Int,
        maxHorizontalRadius: Int,
        distance: Double,
        sameDimension: Boolean,
        now: Long,
        lastAccepted: Long?,
        cooldownTicks: Int,
        locked: Boolean = false,
        tetheredOwners: Set<UUID> = emptySet(),
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Decision = when {
        !isValidRadiusStep(radius, maxHorizontalRadius) -> Decision.Reject("invalid_radius")
        !mayToggle(requester, isOperator, placer, locked, tetheredOwners, areTeammates) -> Decision.Reject("not_owner")
        !sameDimension || distance > MAX_DISTANCE -> Decision.Reject("too_far")
        lastAccepted != null && now - lastAccepted < cooldownTicks -> Decision.Ignore
        else -> Decision.Apply
    }
}
