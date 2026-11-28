package codes.honor.cobblemondynamics.pasture

import java.util.UUID

/**
 * Who may control or lock a pasture under ADR-0021 (CD-TEAM-01, CD-TEAM-02, CD-TEAM-03).
 */
object PastureAccessPolicy {
    fun mayControl(
        actor: UUID,
        isOperator: Boolean,
        ownerId: UUID?,
        locked: Boolean,
        tetheredOwners: Set<UUID>,
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Boolean {
        if (isOperator) return true
        if (ownerId != null && (actor == ownerId || areTeammates(actor, ownerId))) return true
        return !locked && actor in tetheredOwners
    }

    fun mayLock(actor: UUID, isOperator: Boolean, ownerId: UUID?, areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false }): Boolean {
        if (isOperator) return true
        if (ownerId == null) return false
        return actor == ownerId || areTeammates(actor, ownerId)
    }

    fun mayUnbind(
        actor: UUID,
        isOperator: Boolean,
        pokemonOwner: UUID?,
        ownerId: UUID?,
        locked: Boolean,
        tetheredOwners: Set<UUID>,
        areTeammates: (UUID, UUID) -> Boolean = { _, _ -> false },
    ): Boolean = (pokemonOwner != null && actor == pokemonOwner) ||
        mayControl(actor, isOperator, ownerId, locked, tetheredOwners, areTeammates)
}
