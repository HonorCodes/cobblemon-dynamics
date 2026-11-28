package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoneTetherRuleTest {
    private val area = WorkArea(BlockPos(10, 2, 10), horizontal = 8, vertical = 6)

    @Test
    fun `CD-NAV-05 positions inside workArea plus 2 stay unforced and outside trigger a return inside workArea`() {
        assertTrue(ZoneTether.isInsideArea(Vec3(18.5, 2.0, 10.5), area))
        assertFalse(ZoneTether.isInsideArea(Vec3(19.5, 2.0, 10.5), area))
        assertTrue(ZoneTether.isInsideMargin(Vec3(20.9, 8.5, 20.9), area))
        assertFalse(ZoneTether.isInsideMargin(Vec3(21.1, 2.0, 10.5), area))
        assertFalse(ZoneTether.isInsideMargin(Vec3(10.5, 11.1, 10.5), area))

        val goal = ZoneTether.returnGoal(Vec3(28.5, 2.0, 10.5), area)
        assertTrue(ZoneTether.isInsideArea(goal, area), "returnGoal $goal must lie inside workArea")
    }

    @Test
    fun `CD-NAV-05 suppresses owner follow and out-of-area idle walk targets only when Work Mode is on`() {
        assertTrue(ZoneTether.shouldSuppressIdleWalk(workMode = true, entityTarget = true, targetPos = BlockPos(12, 2, 12), area = area))
        assertTrue(ZoneTether.shouldSuppressIdleWalk(workMode = true, entityTarget = false, targetPos = BlockPos(25, 2, 10), area = area))
        assertFalse(ZoneTether.shouldSuppressIdleWalk(workMode = true, entityTarget = false, targetPos = BlockPos(14, 2, 12), area = area))
        assertFalse(ZoneTether.shouldSuppressIdleWalk(workMode = false, entityTarget = true, targetPos = BlockPos(12, 2, 12), area = area))
        assertFalse(ZoneTether.shouldSuppressIdleWalk(workMode = false, entityTarget = false, targetPos = BlockPos(30, 2, 10), area = area))
    }
}
