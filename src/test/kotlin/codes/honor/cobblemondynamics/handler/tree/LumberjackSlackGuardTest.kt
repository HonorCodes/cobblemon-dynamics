package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.ClaimMemo
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LumberjackSlackGuardTest {
    @Test
    fun `CD-PRG-14 LumberjackHandler canSlack is false while a FellRun is active and true once idle`() {
        val memo = ClaimMemo()
        assertTrue(LumberjackHandler.canSlack(memo))

        val logId = ResourceLocation.withDefaultNamespace("oak_log")
        val plan = FellPlan(
            base = BlockPos.ZERO,
            steps = listOf(FellStep(BlockPos.ZERO, logId, shell = false)),
            questions = emptyList(),
            twoByTwo = false,
        )
        val run = FellRun(plan, blocksPerTick = 1)
        LumberjackHandler.attachRunForTest(memo, run)
        assertFalse(LumberjackHandler.canSlack(memo))

        run.finish {}
        LumberjackHandler.attachRunForTest(memo, null)
        assertTrue(LumberjackHandler.canSlack(memo))
    }
}
