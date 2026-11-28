package codes.honor.cobblemondynamics.engine

enum class IdleReason(val langKey: String?) {
    NONE(null),
    NO_TARGET("cobblemon_dynamics.idle.no_target"),
    NEEDS_LEASH("cobblemon_dynamics.idle.needs_leash"),
    TARGET_TAKEN("cobblemon_dynamics.idle.target_taken"),
    CANNOT_REACH("cobblemon_dynamics.idle.cannot_reach"),
    CLAIM_DENIED("cobblemon_dynamics.idle.claim_denied"),
    LEVEL_TOO_LOW("cobblemon_dynamics.idle.level_too_low"),
    RESTING("cobblemon_dynamics.idle.resting"),
}

data class IdleCandidateFacts(
    val claimDenied: Boolean = false,
    val levelTooLow: Boolean = false,
    val targetTaken: Boolean = false,
    val cannotReach: Boolean = false,
)

object IdleReasonRules {
    fun resolve(
        status: WorkerStatus,
        tired: Boolean = false,
        missedArrival: Boolean = false,
        standbyLevelTooLow: Boolean = false,
        leashed: Boolean = false,
        hasOnlyLeashRoles: Boolean = false,
        hasLeashTargetInArea: Boolean = false,
        candidates: Iterable<IdleCandidateFacts> = emptyList(),
    ): IdleReason {
        if (status == WorkerStatus.WORKING ||
            status == WorkerStatus.HEALING ||
            status == WorkerStatus.SLACKING ||
            status == WorkerStatus.TRAVELING
        ) {
            return IdleReason.NONE
        }
        if (tired || status.isResting) return IdleReason.RESTING
        if (status == WorkerStatus.STANDBY) {
            return when {
                missedArrival -> IdleReason.CANNOT_REACH
                standbyLevelTooLow -> IdleReason.LEVEL_TOO_LOW
                else -> IdleReason.NONE
            }
        }
        if (!leashed && hasOnlyLeashRoles) return IdleReason.NEEDS_LEASH
        var claimDenied = false
        var levelTooLow = false
        var targetTaken = false
        var cannotReach = false
        for (candidate in candidates) {
            if (candidate.claimDenied) claimDenied = true
            if (candidate.levelTooLow) levelTooLow = true
            if (candidate.targetTaken) targetTaken = true
            if (candidate.cannotReach) cannotReach = true
        }
        return when {
            claimDenied -> IdleReason.CLAIM_DENIED
            levelTooLow -> IdleReason.LEVEL_TOO_LOW
            targetTaken -> IdleReason.TARGET_TAKEN
            cannotReach -> IdleReason.CANNOT_REACH
            !leashed && hasLeashTargetInArea -> IdleReason.NEEDS_LEASH
            else -> IdleReason.NO_TARGET
        }
    }
}
