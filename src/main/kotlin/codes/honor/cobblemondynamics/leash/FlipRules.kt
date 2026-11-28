package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.pasture.PastureAccessPolicy
import java.util.UUID

/**
 * Who may flip a driven target with the Work Leash (CD-WHL-05, CD-SEC-10, CD-TEAM-01, CD-TEAM-02). While [locked] is false, authorized
 * pasture controllers and the worker's [binder] may flip; while [locked] is true, only the placer, teammates of the placer, and operators
 * may flip.
 */
object FlipRules {
    fun mayFlip(
        actor: UUID,
        isOperator: Boolean,
        binder: UUID?,
        placer: UUID?,
        locked: Boolean = false,
        tetheredOwners: Set<UUID> = emptySet(),
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Boolean {
        if (PastureAccessPolicy.mayControl(actor, isOperator, placer, locked, tetheredOwners, areTeammates)) return true
        return !locked && binder != null && actor == binder
    }
}
