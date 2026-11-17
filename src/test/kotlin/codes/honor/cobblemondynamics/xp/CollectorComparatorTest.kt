package codes.honor.cobblemondynamics.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectorComparatorTest {
    @Test
    fun `CD-XPC-12 0, 1, 50,000, 99,999 and 100,000 XP of 100,000 give 0, 1, 8, 14 and 15`() {
        val cases = mapOf(0 to 0, 1 to 1, 50_000 to 8, 99_999 to 14, 100_000 to 15)
        for ((xp, signal) in cases) assertEquals(signal, CollectorStore(xp) { 100_000 }.comparator(), "$xp XP")
    }

    @Test
    fun `CD-XPC-12 the signal never falls as the collector fills, and a collector above a lowered capacity reads 15`() {
        val signals = (0..100_000 step 7).map { CollectorStore(it) { 100_000 }.comparator() }
        assertTrue(signals.zipWithNext().all { (a, b) -> b == a || b == a + 1 }, "the signal skips or falls")
        assertEquals(15, CollectorStore(120_000) { 100_000 }.comparator())
    }
}
