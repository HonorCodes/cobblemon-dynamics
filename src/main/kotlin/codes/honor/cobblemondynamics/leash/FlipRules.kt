package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.pasture.PastureAccessPolicy
import java.util.UUID

/**
 * Who may flip a driven target with the Work Leash (CD-WHL-05, CD-SEC-10, CD-TEAM-01, CD-TEAM-02). A pasture worker's target follows
 * current [PastureAccessPolicy.mayControl]; a Quick Work order's target allows its [quickWorkOwner] or an operator.
 */
object FlipRules {
    fun mayFlip(
        actor: UUID,
        isOperator: Boolean,
        placer: UUID?,
        locked: Boolean = false,
        tetheredOwners: Set<UUID> = emptySet(),
        hasPasture: Boolean = true,
        quickWorkOwner: UUID? = null,
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Boolean = if (hasPasture) {
        PastureAccessPolicy.mayControl(actor, isOperator, placer, locked, tetheredOwners, areTeammates)
    } else {
        isOperator || (quickWorkOwner != null && actor == quickWorkOwner)
    }
}
