package codes.honor.cobblemondynamics.handler.capability

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NearestFacesTest {
    /** A target in the floor. A worker beside it stands on the floor, half a block above the target's center. */
    private val target = BlockPos(0, 1, 0)

    @Test
    fun `CD-GEN-04 a worker to the east gets the east face first and the west face last`() {
        val order = NearestFaces.of(target, Vec3(3.5, 2.0, 0.5))
        assertEquals(Direction.EAST, order.first())
        assertEquals(Direction.WEST, order.last())
        assertEquals(Direction.entries.toSet(), order.toSet())
        assertEquals(6, order.size)
    }

    @Test
    fun `CD-GEN-04 a worker on top of the target gets the top face first`() {
        val order = NearestFaces.of(target, Vec3(0.5, 2.0, 0.5))
        assertEquals(Direction.UP, order.first())
        assertEquals(Direction.DOWN, order.last())
    }

    @Test
    fun `CD-GEN-04 after the nearest face come the next nearest, and ties go to the lower ordinal`() {
        assertEquals(
            listOf(Direction.EAST, Direction.SOUTH, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.WEST),
            NearestFaces.of(target, Vec3(3.5, 1.5, 1.5)),
        )
        val diagonal = NearestFaces.of(target, Vec3(2.5, 1.5, -1.5))
        assertEquals(listOf(Direction.NORTH, Direction.EAST), diagonal.take(2))
    }
}
