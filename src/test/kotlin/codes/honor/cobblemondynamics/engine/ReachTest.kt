package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachTest {
    /** A worker east of the target at (0, 1, 0), with Cobblemon's follow range of 32 blocks. */
    private val from = Vec3(1.5, 1.0, 0.5)
    private val near = BlockPos(1, 1, 0)
    private val far = BlockPos(40, 1, 0)

    private fun check(plan: (BlockPos) -> Boolean?) = Reach.Check(from, 32.0, plan)

    @Test
    fun `CD-ASG-09 a spot with a whole path is reachable and a spot with a partial path is not`() {
        val check = check { it == near }
        assertTrue(check(near))
        assertFalse(check(BlockPos(0, 1, 1)))
        assertFalse(check.cannotPlan)
    }

    @Test
    fun `CD-ASG-09 a navigation that cannot plan refuses the spot and every spot after it`() {
        val planned = mutableListOf<BlockPos>()
        val check = check {
            planned += it
            null
        }
        assertFalse(check(near))
        assertTrue(check.cannotPlan)
        assertFalse(check(BlockPos(0, 1, 1)))
        assertFalse(check(far), "a spot beyond the follow range was accepted after the navigation could not plan")
        assertEquals(listOf(near), planned)
    }

    @Test
    fun `CD-ASG-09 a spot beyond the follow range counts as reachable with no plan`() {
        val check = check { throw AssertionError("a path was planned to $it") }
        assertTrue(check(far))
        assertFalse(check.cannotPlan)
    }

    @Test
    fun `CD-ASG-09 a worker whose navigation cannot plan gets no spot and the claim learns why`() {
        val target = BlockPos(0, 1, 0)
        val check = check { null }
        val spot = WorkSpots.findReachable(
            GridFixture(emptySet(), target),
            WorkArea(target, 8, 6),
            TargetShape.of(target),
            Vec3.atCenterOf(target),
            6.0,
            from,
            check,
        )
        assertNull(spot)
        assertTrue(check.cannotPlan)
    }
}
