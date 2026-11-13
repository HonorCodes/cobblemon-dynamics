package codes.honor.cobblemondynamics.handler.farming

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrowthStallTest {
    @Test
    fun `CD-CUL-09 the 20th unchanged use stops the demand for 6,000 ticks`() {
        val stall = GrowthStall()
        for (use in 0 until 19) assertFalse(stall.record(changed = false, now = use * 40L), "use ${use + 1} stalled")
        assertFalse(stall.isStalled(19 * 40L))
        assertTrue(stall.record(changed = false, now = 800), "the 20th unchanged use did not stall")
        assertTrue(stall.isStalled(800))
        assertTrue(stall.isStalled(800 + 5_999))
        assertFalse(stall.isStalled(800 + 6_000), "the stall outlived 6,000 ticks")
    }

    @Test
    fun `CD-CUL-09 a changed use resets the count`() {
        val stall = GrowthStall()
        for (use in 0 until 19) stall.record(changed = false, now = use.toLong())
        stall.record(changed = true, now = 19)
        for (use in 0 until 19) assertFalse(stall.record(changed = false, now = 20L + use))
        assertFalse(stall.isStalled(39))
        assertTrue(stall.record(changed = false, now = 40))
    }
}
