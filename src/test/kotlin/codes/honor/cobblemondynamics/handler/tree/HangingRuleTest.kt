package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** R82: the blocks that hold up an item frame or a painting, from its box as vanilla places it. */
class HangingRuleTest {
    @Test
    fun `CD-LUM-30 an item frame on the east face of a log hangs on that log`() {
        // A frame in cell (12, 4, 9) that faces east lies against the west side of its cell, 1/16 thick and 12/16 wide.
        val box = AABB(12.0, 4.125, 9.125, 12.0625, 4.875, 9.875)
        assertEquals(listOf(BlockPos(11, 4, 9)), HangingRule.supports(box, Direction.EAST))
    }

    @Test
    fun `CD-LUM-30 an item frame on top of a block hangs on the block below it`() {
        val box = AABB(5.125, 65.0, 5.125, 5.875, 65.0625, 5.875)
        assertEquals(listOf(BlockPos(5, 64, 5)), HangingRule.supports(box, Direction.UP))
    }

    @Test
    fun `CD-LUM-30 a painting 2 blocks wide that faces south hangs on the 2 blocks north of it`() {
        val box = AABB(3.0, 64.0, 7.0, 5.0, 65.0, 7.0625)
        assertEquals(setOf(BlockPos(3, 64, 6), BlockPos(4, 64, 6)), HangingRule.supports(box, Direction.SOUTH).toSet())
    }
}
