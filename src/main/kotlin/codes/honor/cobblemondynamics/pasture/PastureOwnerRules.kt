package codes.honor.cobblemondynamics.pasture

import java.util.UUID

/**
 * Offline- and cross-dimension-safe resolution of tethered worker ownership and store ids (CD-TEAM-04, CD-TEAM-05).
 */
object PastureOwnerRules {
    fun ownerId(playerId: UUID?, pcId: UUID?, liveOwnerId: UUID?): UUID? = playerId ?: pcId ?: liveOwnerId

    fun refOwner(pcId: UUID?, playerId: UUID?): UUID? = pcId ?: playerId

    fun storeIds(pcId: UUID?, playerId: UUID?): List<UUID> = listOfNotNull(pcId, playerId).distinct()

    fun canWork(
        tetheredOwnerId: UUID?,
        tetheredPcId: UUID?,
        liveOwnerId: UUID?,
        fainted: Boolean,
        battleClone: Boolean,
        removed: Boolean,
        sameLevel: Boolean,
    ): Boolean {
        if (tetheredOwnerId == null || fainted || battleClone || removed || !sameLevel) return false
        return liveOwnerId == null || liveOwnerId == tetheredOwnerId || liveOwnerId == tetheredPcId
    }
}
