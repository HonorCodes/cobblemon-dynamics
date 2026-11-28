package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.HarvestKind
import codes.honor.cobblemondynamics.handler.farming.HarvestPlans
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler
import codes.honor.cobblemondynamics.handler.farming.PickerHandler
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.vanilla.FurnaceHandler
import codes.honor.cobblemondynamics.handler.water.FreezerHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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

    @Test
    fun `CD-TEAM-08 HarvestPlans_breakCells maps gourd fruit, upper-half, whole two-block, single crop and non-breaking picks`() {
        val base = BlockPos(5, 64, 5)
        assertEquals(
            listOf(base.east()),
            HarvestPlans.breakCells(base, HarvestKind.GOURD, upperPresent = false, facing = Direction.EAST),
            "gourd stem breaks only the fruit cell beside it",
        )
        assertEquals(
            listOf(base.above()),
            HarvestPlans.breakCells(base, HarvestKind.UPPER_HALF, upperPresent = true, facing = null),
            "upper-half crop breaks only canonical.above()",
        )
        assertEquals(
            listOf(base, base.above()),
            HarvestPlans.breakCells(base, HarvestKind.CROP, upperPresent = true, facing = null),
            "whole two-block crop breaks canonical and canonical.above()",
        )
        assertEquals(
            listOf(base, base.above()),
            HarvestPlans.breakCells(base, HarvestKind.BREAK_AND_REPLANT, upperPresent = true, facing = null),
        )
        assertEquals(
            listOf(base),
            HarvestPlans.breakCells(base, HarvestKind.CROP, upperPresent = false, facing = null),
            "single-block breaking crop breaks canonical",
        )
        for (nonBreaking in listOf(
            HarvestKind.PICK_TO_AGE_0,
            HarvestKind.PICK_TO_AGE_1,
            HarvestKind.BERRY_TREE,
            HarvestKind.CAVE_VINES,
            HarvestKind.CAVE_VINES_TAG,
            HarvestKind.PICK_BY_LOOT_TABLE,
            HarvestKind.NEVER,
            null,
        )) {
            assertEquals(emptyList<BlockPos>(), HarvestPlans.breakCells(base, nonBreaking, upperPresent = false, facing = null))
        }

        val twoCells = listOf(base, base.above())
        assertTrue(BreakingClaimGate.mayClaimCells(mayUse = true, twoCells) { true })
        assertFalse(BreakingClaimGate.mayClaimCells(mayUse = true, twoCells) { it != base.above() })
        assertTrue(BreakingClaimGate.mayClaimCells(mayUse = true, emptyList()) { error("empty breakTargets asks nothing") })
    }
}
