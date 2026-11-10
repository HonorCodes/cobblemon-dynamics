package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.TargetHandler
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TargetScanTest {
    /** Counts block-state reads; block entities come from a fixed list. */
    private class CountingWorld(private val bes: List<Pair<BlockPos, BlockEntity>>) : ScanWorld {
        var stateReads = 0
        override fun blockEntitiesIn(area: WorkArea) = bes.asSequence()
        override fun blockState(x: Int, y: Int, z: Int): BlockState {
            stateReads++
            error("not used in this test")
        }
        override fun match(handler: TargetHandler, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): BlockPos? =
            error("not used in this test")
    }

    @Test
    fun `CD-PERF-03 a scan with only Fire-type workers reads no block state`() {
        val world = CountingWorld(emptyList())
        val scan = TargetScan(WorkArea(BlockPos.ZERO, 8, 6), roles = emptyList(), workersNeedBlockScan = false, budget = 512)
        scan.startPass(world)
        while (!scan.stepBlocks(world)) { }
        assertEquals(0, world.stateReads)
    }
}
