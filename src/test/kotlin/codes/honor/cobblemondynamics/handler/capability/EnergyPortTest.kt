package codes.honor.cobblemondynamics.handler.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyPortTest {
    /** A battery that records every real offer. */
    private class FakeBattery(private val capacity: Int, var stored: Int = 0) : EnergyPort {
        val offers = mutableListOf<Int>()

        override fun simulate(fe: Int): Int = minOf(fe, capacity - stored)

        override fun push(fe: Int): Int {
            offers += fe
            val took = minOf(fe, capacity - stored)
            stored += took
            return took
        }
    }

    @Test
    fun `CD-GEN-05 a full battery refuses 217 FE, and once it empties the next tick offers 217 FE`() {
        val battery = FakeBattery(capacity = 1_000, stored = 1_000)
        assertEquals(0, FeTick.work(battery, 217))
        battery.stored = 0
        assertEquals(217, FeTick.work(battery, 217))
        assertEquals(listOf(217, 217), battery.offers)
        assertEquals(217, battery.stored)
    }

    @Test
    fun `CD-GEN-05 the part of an offer that a battery refuses never comes back`() {
        val battery = FakeBattery(capacity = 300)
        assertEquals(217, FeTick.work(battery, 217))
        assertEquals(83, FeTick.work(battery, 217))
        battery.stored = 0
        assertEquals(217, FeTick.work(battery, 217))
        assertEquals(listOf(217, 217, 217), battery.offers)
    }

    @Test
    fun `CD-GEN-06 demand only while a simulated insert of 1 FE succeeds, and a check never pushes`() {
        val battery = FakeBattery(capacity = 1_000, stored = 999)
        assertTrue(FeTick.hasDemand(battery, 217))
        battery.stored = 1_000
        assertFalse(FeTick.hasDemand(battery, 217))
        assertEquals(emptyList<Int>(), battery.offers)
    }

    @Test
    fun `a worker that gives no FE has no demand and offers nothing`() {
        val battery = FakeBattery(capacity = 1_000)
        assertFalse(FeTick.hasDemand(battery, 0))
        assertEquals(0, FeTick.work(battery, 0))
        assertEquals(emptyList<Int>(), battery.offers)
    }
}
