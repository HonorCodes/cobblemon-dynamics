package codes.honor.cobblemondynamics.handler.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FluidPortTest {
    /** A tank of [capacity] mB that takes at most [maxFill] mB per fill and holds [contents] until it takes water. */
    private class FakeTank(val capacity: Int, var stored: Int = 0, val maxFill: Int = Int.MAX_VALUE, var contents: String = "water") :
        FluidPort {
        val fills = mutableListOf<Int>()

        private fun room(mb: Int): Int = if (contents != "water" && stored > 0) 0 else minOf(mb, maxFill, capacity - stored)

        override fun simulate(mb: Int): Int = room(mb)

        override fun fill(mb: Int): Int {
            fills += mb
            val taken = room(mb)
            stored += taken
            if (taken > 0) contents = "water"
            return taken
        }

        override fun holdsWater(): Boolean = contents == "water" && stored > 0
    }

    @Test
    fun `CD-PMP-05 a target has demand while a simulated fill of 1 mB of water succeeds`() {
        assertTrue(FluidTick.hasDemand(FakeTank(capacity = 8_000), 50), "an empty tank")
        assertTrue(FluidTick.hasDemand(FakeTank(capacity = 8_000, stored = 7_999), 50), "1 mB of room")
        assertFalse(FluidTick.hasDemand(FakeTank(capacity = 8_000, stored = 8_000), 50), "a full tank")
        assertFalse(FluidTick.hasDemand(FakeTank(capacity = 8_000, stored = 500, contents = "lava"), 50), "a tank of lava")
        assertFalse(FluidTick.hasDemand(FakeTank(capacity = 8_000), 0), "a worker that gives no water")
    }

    @Test
    fun `CD-PMP-03 one tick offers the rate once, and what the target refuses is gone`() {
        val tank = FakeTank(capacity = 8_000, stored = 7_800)
        assertEquals(200, FluidTick.work(tank, 500))
        assertEquals(listOf(500), tank.fills)
        assertEquals(8_000, tank.stored)
        assertEquals(0, FluidTick.work(tank, 500))
        assertEquals(listOf(500, 500), tank.fills, "the refused 300 mB are not offered again")

        val slow = FakeTank(capacity = 8_000, maxFill = 100)
        assertEquals(100, FluidTick.work(slow, 500))
        assertEquals(100, slow.stored)
    }

    @Test
    fun `CD-PMP-01 a target takes water now or holds water already, and a tank of another fluid takes none`() {
        assertTrue(FluidTick.acceptsWater(FakeTank(capacity = 1_000)), "an empty tank")
        assertTrue(FluidTick.acceptsWater(FakeTank(capacity = 1_000, stored = 1_000)), "a full water tank")
        assertFalse(FluidTick.acceptsWater(FakeTank(capacity = 1_000, stored = 1_000, contents = "lava")), "a full lava tank")
        assertFalse(FluidTick.acceptsWater(FakeTank(capacity = 1_000, stored = 10, contents = "lava")), "a lava tank with room")
    }
}
