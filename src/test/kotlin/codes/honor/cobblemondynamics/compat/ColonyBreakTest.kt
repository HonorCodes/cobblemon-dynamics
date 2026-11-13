package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.compat.minecolonies.ColonyBreak
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The break rule of a colony, as `ColonyPermissionEventHandler.on(BlockEvent.BreakEvent)` of MineColonies 1.1.1420 decides it. */
class ColonyBreakTest {
    private val refused = ColonyBreak.Facts(protection = true, inColony = true, rankBreaks = false)

    @Test
    fun `CD-HRV-19 a colony refuses a break only to a rank with no BREAK_BLOCKS`() {
        assertFalse(ColonyBreak.allows(refused))
        assertTrue(ColonyBreak.allows(refused.copy(rankBreaks = true)), "a rank with BREAK_BLOCKS")
        assertTrue(ColonyBreak.allows(refused.copy(inColony = false)), "a block outside the colony")
        assertTrue(ColonyBreak.allows(refused.copy(protection = false)), "a colony with no protection")
    }
}
