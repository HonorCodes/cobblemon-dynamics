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

    /**
     * The walk to the spot ends once the mob stands in the spot's cell, because the work order's walk target has a close-enough distance
     * of 0. A Dragonair 1.1 blocks wide that enters the cell from the side away from the corner stops up to 1 block from the corner.
     */
    @Test
    fun `CD-ASG-11 a worker 1 to 2 blocks wide that stands in the spot's cell has arrived`() {
        assertTrue(Arrival.isAt(6.05, 2.0, 10.05, 1.1, spot))
        assertTrue(Arrival.isAt(6.95, 2.9, 10.01, 1.1, spot))
        assertFalse(Arrival.isAt(5.95, 2.0, 10.05, 1.1, spot), "the cell to the west, beyond the corner's reach")
        assertFalse(Arrival.isAt(6.05, 3.0, 10.05, 1.1, spot), "the cell above")
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
