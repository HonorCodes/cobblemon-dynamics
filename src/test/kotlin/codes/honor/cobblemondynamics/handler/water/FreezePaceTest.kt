package codes.honor.cobblemondynamics.handler.water

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreezePaceTest {
    private fun params(json: String): FreezerParams = FreezerParams.of(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `CD-FRZ-04 the interval is 1,200 ticks at level 1, 40 at level 50 and 20 at level 100`() {
        assertEquals(listOf(1_200, 40, 20), listOf(1, 50, 100).map { FreezePace.intervalTicks(it, 60) })
    }

    @Test
    fun `CD-FRZ-04 a level above 100 freezes as level 100, and a level below 1 as level 1`() {
        assertEquals(20, FreezePace.intervalTicks(150, 60))
        assertEquals(1_200, FreezePace.intervalTicks(0, 60))
    }

    @Test
    fun `CD-FRZ-13 a role file with 30 cells per minute gives 40 ticks at level 100`() {
        assertEquals(40, FreezePace.intervalTicks(100, params("""{ "cells_per_minute_at_level_100": 30 }""").cellsPerMinuteAt100))
    }

    @Test
    fun `CD-FRZ-13 a file without the keys, or with keys of another type, takes 60 cells and 5 blocks`() {
        assertEquals(FreezerParams(60, 5.0), params("{}"))
        assertEquals(FreezerParams(60, 5.0), params("""{ "cells_per_minute_at_level_100": "fast", "reach": [] }"""))
        assertEquals(FreezerParams(12, 3.5), params("""{ "cells_per_minute_at_level_100": 12, "reach": 3.5 }"""))
    }

    /** R84: a pace of 0 or less freezes nothing, so the role has no demand, and a fractional pace counts as its ceiling. */
    @Test
    fun `CD-FRZ-04 a pace of 0 or less freezes nothing, and a fractional pace counts as its ceiling`() {
        assertFalse(params("""{ "cells_per_minute_at_level_100": 0 }""").freezes)
        assertFalse(params("""{ "cells_per_minute_at_level_100": -5 }""").freezes)
        assertTrue(params("{}").freezes)
        assertEquals(Int.MAX_VALUE, FreezePace.intervalTicks(100, 0), "no freeze ever comes")
        assertEquals(Int.MAX_VALUE, FreezePace.intervalTicks(100, -5))
        assertEquals(1, params("""{ "cells_per_minute_at_level_100": 0.5 }""").cellsPerMinuteAt100)
        assertEquals(60, params("""{ "cells_per_minute_at_level_100": 59.2 }""").cellsPerMinuteAt100)
        assertTrue(params("""{ "cells_per_minute_at_level_100": 0.5 }""").freezes)
        assertEquals(1_200, FreezePace.intervalTicks(100, 1), "1 cell per minute at level 100")
    }

    /** The search box of a freeze grows with the cube of the reach, so a role file cannot make it larger than the cap. */
    @Test
    fun `CD-FRZ-13 the reach stays between 0 and 16 blocks`() {
        assertEquals(16.0, params("""{ "reach": 1000 }""").reach)
        assertEquals(0.0, params("""{ "reach": -2 }""").reach)
    }

    /**
     * The first freeze of a claim comes one interval after its work starts. A worker that goes cell to cell starts a new claim each
     * time, so no claim freezes sooner than the pace allows (CD-BAL-12).
     */
    @Test
    fun `CD-FRZ-04 a freeze comes one interval after the work starts and every interval after it`() {
        val since = 500L
        val ticks = (since..since + 100).filter { FreezePace.isFreezeTick(since, it, 20) }
        assertEquals(listOf(520L, 540L, 560L, 580L, 600L), ticks)
        assertFalse(FreezePace.isFreezeTick(since, since, 20), "a freeze on the first Working tick")
        assertTrue(FreezePace.isFreezeTick(since, since + 1, 0), "an interval below 1 counts as 1")
    }
}
