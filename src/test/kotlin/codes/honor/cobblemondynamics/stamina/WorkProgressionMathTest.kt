package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.EngineSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkProgressionMathTest {
    @Test
    fun `CD-PRG-11 CD-PRG-12 Work XP grants 4 plus level over 15 below maxPokemonLevel via non-battle sidemod source`() {
        val s = EngineSettings.DEFAULT.copy(workXpEnabled = true)
        assertEquals(4, WorkProgression.xpGrant(1, 100, s))
        assertEquals(4, WorkProgression.xpGrant(14, 100, s))
        assertEquals(5, WorkProgression.xpGrant(15, 100, s))
        assertEquals(5, WorkProgression.xpGrant(29, 100, s))
        assertEquals(10, WorkProgression.xpGrant(90, 100, s))
        assertEquals(0, WorkProgression.xpGrant(100, 100, s))
        assertEquals(10, WorkProgression.xpGrant(100, 120, s))
        assertEquals(10, WorkProgression.xpGrant(104, 120, s))
        assertEquals(0, WorkProgression.xpGrant(50, 100, s.copy(workXpEnabled = false)))

        assertEquals(MOD_ID, WorkProgression.EXPERIENCE_SOURCE.sidemodId)
        assertTrue(WorkProgression.EXPERIENCE_SOURCE.isSidemod())
        assertFalse(WorkProgression.EXPERIENCE_SOURCE.isBattle())
    }

    @Test
    fun `CD-PRG-13 passive Friendship grants +1 alone, +2 with Mate or owner within 16 blocks, and 0 when disabled`() {
        val s = EngineSettings.DEFAULT.copy(friendshipEnabled = true)
        assertEquals(1, WorkProgression.friendshipGain(hasMate = false, ownerSameLevel = false, ownerDistanceSqr = Double.MAX_VALUE, s))
        assertEquals(2, WorkProgression.friendshipGain(hasMate = true, ownerSameLevel = false, ownerDistanceSqr = Double.MAX_VALUE, s))
        assertEquals(2, WorkProgression.friendshipGain(hasMate = false, ownerSameLevel = true, ownerDistanceSqr = 16.0 * 16.0, s))
        assertEquals(1, WorkProgression.friendshipGain(hasMate = false, ownerSameLevel = true, ownerDistanceSqr = 16.1 * 16.1, s))
        assertEquals(1, WorkProgression.friendshipGain(hasMate = false, ownerSameLevel = false, ownerDistanceSqr = 4.0, s))
        assertEquals(
            0,
            WorkProgression.friendshipGain(
                hasMate = true,
                ownerSameLevel = true,
                ownerDistanceSqr = 0.0,
                s.copy(friendshipEnabled = false),
            ),
        )
    }

    @Test
    fun `CD-PRG-11 CD-PRG-13 disabled or level-capped progression never banks ticks and 1200th tick awards XP`() {
        val enabled = EngineSettings.DEFAULT.copy(workXpEnabled = true, friendshipEnabled = true)
        val disabled = EngineSettings.DEFAULT.copy(workXpEnabled = false, friendshipEnabled = false)

        val partialXp = WorkProgression.advanceXpTicks(0L, 1_199L, level = 5, maxPokemonLevel = 100, s = enabled)
        assertEquals(1_199L, partialXp.nextTicks)
        assertEquals(0, partialXp.xpAwarded)

        val resetDisabledXp = WorkProgression.advanceXpTicks(partialXp.nextTicks, 1L, level = 5, maxPokemonLevel = 100, s = disabled)
        assertEquals(0L, resetDisabledXp.nextTicks)
        assertEquals(0, resetDisabledXp.xpAwarded)

        val resetCappedXp = WorkProgression.advanceXpTicks(1_199L, 1L, level = 100, maxPokemonLevel = 100, s = enabled)
        assertEquals(0L, resetCappedXp.nextTicks)
        assertEquals(0, resetCappedXp.xpAwarded)

        val finalTickXp = WorkProgression.advanceXpTicks(1_199L, 1L, level = 5, maxPokemonLevel = 100, s = enabled)
        assertEquals(0L, finalTickXp.nextTicks)
        assertEquals(4, finalTickXp.xpAwarded)

        val partialFriend = WorkProgression.advanceFriendshipTicks(0L, 11_999L, false, false, Double.MAX_VALUE, enabled)
        assertEquals(11_999L, partialFriend.nextTicks)
        assertEquals(0, partialFriend.friendshipAwarded)

        val resetDisabledFriend = WorkProgression.advanceFriendshipTicks(partialFriend.nextTicks, 1L, true, true, 0.0, disabled)
        assertEquals(0L, resetDisabledFriend.nextTicks)
        assertEquals(0, resetDisabledFriend.friendshipAwarded)
    }
}
