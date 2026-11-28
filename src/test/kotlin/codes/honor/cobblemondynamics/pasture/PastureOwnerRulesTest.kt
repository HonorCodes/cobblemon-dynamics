package codes.honor.cobblemondynamics.pasture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PastureOwnerRulesTest {
    private val playerId = UUID.randomUUID()
    private val pcId = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `CD-TEAM-04 owner and store ids resolve from tethering pcId and playerId when live entity owner UUID is null`() {
        assertEquals(playerId, PastureOwnerRules.ownerId(playerId, pcId, liveOwnerId = null))
        assertEquals(pcId, PastureOwnerRules.ownerId(playerId = null, pcId = pcId, liveOwnerId = null))
        assertEquals(listOf(pcId, playerId), PastureOwnerRules.storeIds(pcId, playerId))
        assertEquals(listOf(playerId), PastureOwnerRules.storeIds(playerId, playerId))
        assertEquals(listOf(playerId), PastureOwnerRules.storeIds(pcId = null, playerId = playerId))
    }

    @Test
    fun `CD-TEAM-05 refOwner prefers pcId and falls back to playerId`() {
        assertEquals(pcId, PastureOwnerRules.refOwner(pcId, playerId))
        assertEquals(playerId, PastureOwnerRules.refOwner(pcId = null, playerId = playerId))
    }

    @Test
    fun `CD-TEAM-04 canWork accepts offline or cross-dimension workers whose live owner UUID is null`() {
        assertTrue(
            PastureOwnerRules.canWork(
                tetheredOwnerId = playerId,
                tetheredPcId = pcId,
                liveOwnerId = null,
                fainted = false,
                battleClone = false,
                removed = false,
                sameLevel = true,
            ),
        )
        assertTrue(
            PastureOwnerRules.canWork(
                tetheredOwnerId = playerId,
                tetheredPcId = pcId,
                liveOwnerId = pcId,
                fainted = false,
                battleClone = false,
                removed = false,
                sameLevel = true,
            ),
        )
        assertFalse(
            PastureOwnerRules.canWork(
                tetheredOwnerId = playerId,
                tetheredPcId = pcId,
                liveOwnerId = other,
                fainted = false,
                battleClone = false,
                removed = false,
                sameLevel = true,
            ),
        )
        assertFalse(
            PastureOwnerRules.canWork(
                tetheredOwnerId = playerId,
                tetheredPcId = pcId,
                liveOwnerId = null,
                fainted = true,
                battleClone = false,
                removed = false,
                sameLevel = true,
            ),
        )
        assertFalse(
            PastureOwnerRules.canWork(
                tetheredOwnerId = playerId,
                tetheredPcId = pcId,
                liveOwnerId = null,
                fainted = false,
                battleClone = true,
                removed = false,
                sameLevel = true,
            ),
        )
        assertFalse(
            PastureOwnerRules.canWork(
                tetheredOwnerId = null,
                tetheredPcId = null,
                liveOwnerId = null,
                fainted = false,
                battleClone = false,
                removed = false,
                sameLevel = true,
            ),
        )
    }
}
