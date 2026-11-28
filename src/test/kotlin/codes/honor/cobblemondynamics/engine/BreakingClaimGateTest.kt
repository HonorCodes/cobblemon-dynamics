package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.HarvestKind
import codes.honor.cobblemondynamics.handler.farming.HarvestPlans
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler
import codes.honor.cobblemondynamics.handler.farming.PickerHandler
import codes.honor.cobblemondynamics.handler.tree.FellPlan
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.tree.TreeGrid
import codes.honor.cobblemondynamics.handler.tree.TreeSearch
import codes.honor.cobblemondynamics.handler.vanilla.FurnaceHandler
import codes.honor.cobblemondynamics.handler.water.FreezerHandler
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.EffectDefinition
import codes.honor.cobblemondynamics.role.RoleDefinition
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
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

    @Test
    fun `CD-TEAM-08 FellPlan_breakCells and LumberjackHandler_breakTargets include every felled leaf so a second-leaf denial refuses`() {
        val grid = TreeGrid()
        val base = BlockPos(8, 64, 8)
        val oak = grid.oak(base)
        val torch = grid.block(base.above().west(), "minecraft:wall_torch")
        val frameA = base.above(2).east()
        val frameB = base.above(3).east()
        val frameType = ResourceLocation.parse("minecraft:item_frame")
        val role = lumberjackRole(maxLogs = 256, maxLeaves = 1_024)

        val tree = TreeSearch.find(grid.read, base, 256, 1_024) ?: error("missing tree")
        val plan = FellPlan.of(tree, setOf(torch), listOf(frameA to frameType, frameB to frameType)) { grid.read(it).blockId }
        val secondLeafInSameChunk = oak.leaves.first { it !in plan.questions }
        assertTrue(FellPlan.allowed(plan, { true }, { it != secondLeafInSameChunk }), "questions deduplicates same-chunk leaves")

        val targets = LumberjackHandler.breakTargets(
            cells = grid.read,
            canonical = base,
            role = role,
            shellOf = { setOf(torch) },
            hangingOf = { _, _ -> listOf(frameA to frameType, frameB to frameType) },
        )
        assertEquals((oak.logs + oak.leaves + torch + frameA + frameB).toSet(), targets.toSet())
        assertTrue(secondLeafInSameChunk in targets, "second leaf in same chunk missing from breakTargets")
        assertTrue(frameB in targets, "second item frame in same chunk missing from breakTargets")
        assertFalse(
            BreakingClaimGate.mayClaimCells(mayUse = true, targets) { it != secondLeafInSameChunk },
            "break-denied second leaf in same chunk must refuse claim",
        )
        assertFalse(
            BreakingClaimGate.mayClaimCells(mayUse = true, targets) { it != frameB },
            "break-denied second hanging entity in same chunk must refuse claim",
        )
    }

    @Test
    fun `CD-TEAM-08 LumberjackHandler_breakTargets reads max_logs and max_leaves from the passed RoleDefinition`() {
        val grid = TreeGrid()
        val base = BlockPos(8, 64, 8)
        val oak = grid.oak(base)
        val saplingPos = grid.sapling(BlockPos(2, 64, 2))

        val highLogsTightLeaves = lumberjackRole(maxLogs = 512, maxLeaves = 20)
        val workerRole = lumberjackRole(maxLogs = 16, maxLeaves = 64)
        val tightLogsRole = lumberjackRole(maxLogs = 3, maxLeaves = 256)

        assertEquals(emptyList<BlockPos>(), LumberjackHandler.breakTargets(grid.read, saplingPos, workerRole))
        assertEquals(
            (oak.logs + oak.leaves).toSet(),
            LumberjackHandler.breakTargets(grid.read, base, workerRole).toSet(),
            "passed workerRole with max_leaves=64 should plan all 58 cells of the 5-log, 53-leaf oak",
        )
        assertEquals(
            listOf(base),
            LumberjackHandler.breakTargets(grid.read, base, highLogsTightLeaves),
            "role with max_leaves=20 cannot fell 53-leaf oak even when max_logs=512",
        )
        assertEquals(
            listOf(base),
            LumberjackHandler.breakTargets(grid.read, base, tightLogsRole),
            "role with max_logs=3 cannot fell 5-log oak",
        )
    }

    private fun lumberjackRole(maxLogs: Int, maxLeaves: Int): RoleDefinition = RoleDefinition(
        id = id("lumberjack"),
        handler = LumberjackHandler.id,
        types = setOf("bug"),
        autoAssign = false,
        priority = 10,
        range = 4.0,
        params = JsonObject().apply {
            addProperty("max_logs", maxLogs)
            addProperty("max_leaves", maxLeaves)
        },
        effect = EffectDefinition(20, 10, emptyList(), emptyList(), emptyList()),
    )
}
