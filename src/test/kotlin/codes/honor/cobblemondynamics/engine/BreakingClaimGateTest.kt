package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler
import codes.honor.cobblemondynamics.handler.farming.PickerHandler
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.vanilla.FurnaceHandler
import codes.honor.cobblemondynamics.handler.water.FreezerHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreakingClaimGateTest {
    @Test
    fun `CD-TEAM-08 Harvester, Lumberjack and Freezer mark breaksBlocks true while non-breaking handlers stay false`() {
        assertTrue(HarvesterHandler.breaksBlocks)
        assertTrue(LumberjackHandler.breaksBlocks)
        assertTrue(FreezerHandler.breaksBlocks)
        assertFalse(CultivatorHandler.breaksBlocks)
        assertFalse(PickerHandler.breaksBlocks)
        assertFalse(FurnaceHandler.breaksBlocks)
    }

    @Test
    fun `CD-TEAM-08 breaking roles require both mayUse and mayBreak while non-breaking roles skip mayBreak`() {
        for (handler in listOf(HarvesterHandler, LumberjackHandler, FreezerHandler)) {
            assertFalse(BreakingClaimGate.mayClaim(handler.breaksBlocks, mayUse = true) { false }, "${handler.id}")
            assertTrue(BreakingClaimGate.mayClaim(handler.breaksBlocks, mayUse = true) { true }, "${handler.id}")
            assertFalse(BreakingClaimGate.mayClaim(handler.breaksBlocks, mayUse = false) { error("mayUse already failed") })
        }
        var breakAsked = 0
        assertTrue(
            BreakingClaimGate.mayClaim(CultivatorHandler.breaksBlocks, mayUse = true) {
                breakAsked++
                false
            },
        )
        assertEquals(0, breakAsked)
    }
}
