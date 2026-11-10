package codes.honor.cobblemondynamics.handler.create

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrankClockTest {
    private val since = 1_000L

    @Test
    fun `CD-CRK-02 a Cranker turns at ticks 0, 5 and 10, and none at tick 4`() {
        assertTrue(CrankClock.shouldTurn(since, since))
        assertTrue(CrankClock.shouldTurn(since, since + 5))
        assertTrue(CrankClock.shouldTurn(since, since + 10))
        assertFalse(CrankClock.shouldTurn(since, since + 4))
    }

    @Test
    fun `CD-CRK-02 every interval is 5 ticks`() {
        val turns = (since - 20..since + 200).filter { CrankClock.shouldTurn(since, it) }
        assertEquals(since, turns.first())
        assertTrue(turns.zipWithNext().all { (a, b) -> b - a == 5L }, "turns at $turns")
    }
}
