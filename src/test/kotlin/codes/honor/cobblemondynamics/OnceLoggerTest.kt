package codes.honor.cobblemondynamics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnceLoggerTest {
    @Test
    fun `CD-PERF-05 a cause that comes up on every tick logs one warning`() {
        val lines = mutableListOf<String>()
        val log = OnceLogger(lines::add)
        var built = 0
        repeat(6_000) { tick -> log.warn("cobblemon:ditto") { "warning ${++built} at tick $tick" } }
        assertEquals(listOf("warning 1 at tick 0"), lines)
        assertEquals(1, built, "the message of a cause that already logged is never built")
    }

    @Test
    fun `CD-PERF-05 each cause logs once, and causes are counted apart`() {
        val lines = mutableListOf<String>()
        val log = OnceLogger(lines::add)
        assertTrue(log.warn("a") { "first a" })
        assertTrue(log.warn("b") { "first b" })
        assertFalse(log.warn("a") { "second a" })
        assertEquals(listOf("first a", "first b"), lines)
    }

    @Test
    fun `two loggers keep their own causes`() {
        val lines = mutableListOf<String>()
        OnceLogger(lines::add).warn("a") { "one" }
        OnceLogger(lines::add).warn("a") { "two" }
        assertEquals(listOf("one", "two"), lines)
    }
}
