package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArrivalTest {
    private val spot = BlockPos(6, 2, 10)

    @Test
    fun `CD-ASG-11 a worker narrower than a block arrives at the spot's center`() {
        assertTrue(Arrival.isAt(6.5, 2.0, 10.5, 0.6, spot))
        assertTrue(Arrival.isAt(7.2, 2.0, 10.5, 0.6, spot))
        assertFalse(Arrival.isAt(7.3, 2.0, 10.5, 0.6, spot))
    }

    /**
     * Navigation steers a Dragonair, 1.1 blocks wide, to the spot's corner, `node + (int)(1.1 + 1) / 2`, and stops it within
     * 0.55 of the corner on each axis. Such a stop is up to 1.48 blocks from the spot's center.
     */
    @Test
    fun `CD-ASG-11 a worker 1 to 2 blocks wide arrives where navigation stops it`() {
        assertTrue(Arrival.isAt(7.5, 2.0, 11.5, 1.1, spot))
        assertTrue(Arrival.isAt(6.5, 2.0, 10.5, 1.1, spot), "the snap to the spot's center keeps it arrived")
        assertFalse(Arrival.isAt(7.9, 2.0, 11.0, 1.1, spot))
    }

    @Test
    fun `a Wailord, 8 blocks wide, stays arrived after the snap to the spot's center`() {
        assertTrue(Arrival.isAt(6.5, 2.0, 10.5, 8.0, spot))
    }

    @Test
    fun `a worker 1_5 blocks above or below the spot has not arrived`() {
        assertTrue(Arrival.isAt(6.5, 3.4, 10.5, 0.6, spot))
        assertFalse(Arrival.isAt(6.5, 3.5, 10.5, 0.6, spot))
        assertFalse(Arrival.isAt(6.5, 0.5, 10.5, 0.6, spot))
    }
}
