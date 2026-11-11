package codes.honor.cobblemondynamics.handler.create

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TailwindMathTest {
    @Test
    fun `CD-TLW-03 the bonus is floor of real sails times level over 100`() {
        assertEquals(32, TailwindMath.bonus(64, 50, 0.01, 128))
        assertEquals(12, TailwindMath.rpm(64 + 32))
        assertEquals(8, TailwindMath.bonus(8, 100, 0.01, 128))
        assertEquals(2, TailwindMath.rpm(8 + 8))
        assertEquals(64, TailwindMath.bonus(64, 100, 0.01, 128))
        assertEquals(16, TailwindMath.rpm(64 + 64))
    }

    @Test
    fun `CD-TLW-04 real and virtual sails stay within 128`() {
        assertEquals(128, 100 + TailwindMath.bonus(100, 100, 0.01, 128))
        assertEquals(127, 96 + TailwindMath.bonus(96, 33, 0.01, 128))
        assertEquals(0, TailwindMath.bonus(200, 100, 0.01, 128))
    }

    @Test
    fun `CD-TLW-03 the bonus is exact for every sail count and level, with no floating-point slip`() {
        for (sails in 8..128) {
            for (level in 1..100) {
                assertEquals(
                    minOf(sails * level / 100, maxOf(0, 128 - sails)),
                    TailwindMath.bonus(sails, level, 0.01, 128),
                    "S=$sails L=$level",
                )
            }
        }
    }

    @Test
    fun `a level above 100 adds no more than level 100`() =
        assertEquals(TailwindMath.bonus(40, 100, 0.01, 128), TailwindMath.bonus(40, 150, 0.01, 128))

    @Test
    fun `a Tailwind spot stays beside or behind the bearing's face`() {
        val bearing = BlockPos(0, 64, 0)
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.EAST, BlockPos(0, 64, 1), 1.8))
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.EAST, BlockPos(-1, 64, 0), 1.8))
        assertFalse(TailwindMath.clearOfSails(bearing, Direction.EAST, BlockPos(1, 64, 1), 0.5))
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.UP, BlockPos(1, 64, 0), 0.7))
        assertFalse(TailwindMath.clearOfSails(bearing, Direction.UP, BlockPos(1, 64, 0), 1.5))
        assertTrue(TailwindMath.clearOfSails(bearing, Direction.DOWN, BlockPos(1, 64, 0), 1.5))
        assertFalse(TailwindMath.clearOfSails(bearing, Direction.DOWN, BlockPos(1, 63, 0), 0.5))
    }
}
