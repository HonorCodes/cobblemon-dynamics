package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdleReasonRulesTest {
    @Test
    fun `CD-SHF-06 active and slacking workers report NONE and tired or resting workers report RESTING`() {
        for (active in listOf(WorkerStatus.WORKING, WorkerStatus.HEALING, WorkerStatus.SLACKING, WorkerStatus.TRAVELING)) {
            assertEquals(IdleReason.NONE, IdleReasonRules.resolve(status = active, tired = false))
        }
        assertEquals(IdleReason.RESTING, IdleReasonRules.resolve(status = WorkerStatus.IDLE, tired = true))
        for (resting in listOf(WorkerStatus.TO_BED, WorkerStatus.SLEEPING, WorkerStatus.RESTING_IN_PLACE)) {
            assertEquals(IdleReason.RESTING, IdleReasonRules.resolve(status = resting))
        }
    }

    @Test
    fun `CD-SHF-06 standby workers report CANNOT_REACH on missed arrival LEVEL_TOO_LOW when level gated and NONE otherwise`() {
        assertEquals(IdleReason.NONE, IdleReasonRules.resolve(status = WorkerStatus.STANDBY))
        assertEquals(
            IdleReason.CANNOT_REACH,
            IdleReasonRules.resolve(status = WorkerStatus.STANDBY, missedArrival = true, standbyLevelTooLow = true),
        )
        assertEquals(
            IdleReason.LEVEL_TOO_LOW,
            IdleReasonRules.resolve(status = WorkerStatus.STANDBY, missedArrival = false, standbyLevelTooLow = true),
        )
    }

    @Test
    fun `CD-SHF-06 idle worker candidate precedence resolves every reason deterministically`() {
        assertEquals(
            IdleReason.NEEDS_LEASH,
            IdleReasonRules.resolve(status = WorkerStatus.IDLE, leashed = false, hasOnlyLeashRoles = true),
        )
        assertEquals(
            IdleReason.NEEDS_LEASH,
            IdleReasonRules.resolve(status = WorkerStatus.IDLE, leashed = false, hasLeashTargetInArea = true),
        )
        assertEquals(
            IdleReason.NO_TARGET,
            IdleReasonRules.resolve(status = WorkerStatus.IDLE, leashed = false, hasLeashTargetInArea = false),
        )

        assertEquals(
            IdleReason.CLAIM_DENIED,
            IdleReasonRules.resolve(
                status = WorkerStatus.IDLE,
                candidates = listOf(
                    IdleCandidateFacts(cannotReach = true),
                    IdleCandidateFacts(targetTaken = true),
                    IdleCandidateFacts(levelTooLow = true),
                    IdleCandidateFacts(claimDenied = true),
                ),
            ),
        )
        assertEquals(
            IdleReason.LEVEL_TOO_LOW,
            IdleReasonRules.resolve(
                status = WorkerStatus.IDLE,
                candidates = listOf(
                    IdleCandidateFacts(cannotReach = true),
                    IdleCandidateFacts(targetTaken = true),
                    IdleCandidateFacts(levelTooLow = true),
                ),
            ),
        )
        assertEquals(
            IdleReason.TARGET_TAKEN,
            IdleReasonRules.resolve(
                status = WorkerStatus.IDLE,
                candidates = listOf(
                    IdleCandidateFacts(cannotReach = true),
                    IdleCandidateFacts(targetTaken = true),
                ),
            ),
        )
        assertEquals(
            IdleReason.CANNOT_REACH,
            IdleReasonRules.resolve(
                status = WorkerStatus.IDLE,
                hasLeashTargetInArea = true,
                candidates = listOf(IdleCandidateFacts(cannotReach = true)),
            ),
        )
    }
}
