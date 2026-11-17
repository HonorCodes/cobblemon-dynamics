package codes.honor.cobblemondynamics.handler.tree

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR-0017, decision 6: the fell comes T ticks of Working for each log after the work starts, and T falls with the level. */
class FellClockTest {
    private val defaults = LumberjackParams.DEFAULT

    /** The ticks after [start] on which a clock started there for a tree of [logs] logs fells, up to [until]. */
    private fun fellTicks(
        level: Int,
        logs: Int,
        params: LumberjackParams = defaults,
        start: Long = 1_000,
        until: Long = 3_000,
    ): List<Long> {
        val clock = FellClock()
        clock.start(start, FellClock.ticksPerLog(level, params), logs)
        return (start..start + until).filter(clock::isFellTick).map { it - start }
    }

    @Test
    fun `CD-LUM-11 a level 100 worker fells an oak of 5 logs on tick 25 after the start, and not on tick 24`() {
        assertEquals(listOf(25L), fellTicks(level = 100, logs = 5))
    }

    @Test
    fun `CD-LUM-11 a level 1 worker fells an oak of 5 logs on tick 100 after the start`() {
        assertEquals(listOf(100L), fellTicks(level = 1, logs = 5))
    }

    @Test
    fun `CD-LUM-11 T is round(A + (B - A) x (L - 1) over 99), so a level 67 worker takes 10 ticks a log, as an iron axe`() {
        assertEquals(20, FellClock.ticksPerLog(1, defaults))
        assertEquals(10, FellClock.ticksPerLog(67, defaults))
        assertEquals(13, FellClock.ticksPerLog(50, defaults))
        assertEquals(5, FellClock.ticksPerLog(100, defaults))
        // The level is clamped to 1 to 100, and T is at least 1 tick.
        assertEquals(5, FellClock.ticksPerLog(250, defaults))
        assertEquals(1, FellClock.ticksPerLog(100, defaults.copy(ticksPerLogAt100 = 0)))
    }

    /** The fell comes once for each start of the work: a waiting worker starts again when the next tree stands (CD-LUM-10). */
    @Test
    fun `CD-LUM-11 the fell comes once, never twice for one start, and a new start sets a new fell`() {
        val clock = FellClock()
        clock.start(0, 5, 5)
        assertEquals(listOf(25L), (0L..5_000L).filter(clock::isFellTick))
        clock.start(6_000, 5, 6)
        assertEquals(listOf(6_030L), (6_000L..9_000L).filter(clock::isFellTick))
    }

    @Test
    fun `CD-LUM-11 a fell that its tick finds no worker for comes on the next tick of Working`() {
        val clock = FellClock()
        clock.start(0, 5, 5)
        assertFalse(clock.isFellTick(24))
        assertTrue(clock.isFellTick(26))
        assertFalse(clock.isFellTick(27))
    }

    @Test
    fun `CD-LUM-13 a refused fell sets a stall of 6,000 ticks through the stall of the work context, and no fell before then`() {
        val clock = FellClock()
        val stalls = ArrayList<Long>()
        clock.start(0, 5, 5)
        assertTrue(clock.isFellTick(25))
        clock.refuse(25) { stalls += it }
        assertEquals(listOf(6_000L), stalls)
        assertTrue(clock.isRefused(25) && clock.isRefused(6_024), "the refusal ends early")
        assertFalse(clock.isRefused(6_025), "the refusal lasts more than 6,000 ticks")
        assertEquals(emptyList<Long>(), (26L..7_000L).filter(clock::isFellTick), "a refused fell came again")
    }

    @Test
    fun `CD-LUM-26 a file with 10 ticks per log at level 100 fells an oak of 5 logs on tick 50`() {
        val params = LumberjackParams.of(JsonParser.parseString("""{ "ticks_per_log_at_level_100": 10 }""").asJsonObject)
        assertEquals(listOf(50L), fellTicks(level = 100, logs = 5, params = params))
        assertEquals(20, params.ticksPerLogAt1)
    }

    @Test
    fun `CD-LUM-26 each number takes its default when the file lacks it or gives another type`() {
        assertEquals(defaults, LumberjackParams.of(JsonParser.parseString("{}").asJsonObject))
        val wrong = """{ "max_logs": "many", "max_leaves": [1], "blocks_per_tick": true, "ticks_per_log_at_level_1": {} }"""
        assertEquals(defaults, LumberjackParams.of(JsonParser.parseString(wrong).asJsonObject))
        assertEquals(LumberjackParams(20, 5, 256, 1_024, 128), defaults)
    }
}
