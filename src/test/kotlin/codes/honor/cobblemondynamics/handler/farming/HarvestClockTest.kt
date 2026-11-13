package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HarvestClockTest {
    @Test
    fun `CD-HRV-05 the harvest falls on tick 20 and once`() {
        assertFalse(HarvestClock.isHarvestTick(since = 100, now = 119, harvestTicks = 20))
        assertTrue(HarvestClock.isHarvestTick(since = 100, now = 120, harvestTicks = 20))
        assertEquals(listOf(120L), (100L..1_000L).filter { HarvestClock.isHarvestTick(100, it, 20) })
    }

    @Test
    fun `CD-HRV-15 the clock follows the role file`() {
        assertEquals(20, HarvestClock.ticksOf(BuiltInRoles.parse("harvester").params))
        val ten = JsonObject().apply { addProperty("harvest_ticks", 10) }
        assertEquals(10, HarvestClock.ticksOf(ten))
        assertTrue(HarvestClock.isHarvestTick(since = 0, now = 10, harvestTicks = HarvestClock.ticksOf(ten)))
    }

    @Test
    fun `CD-CRP-20 no tick holds two harvests`() {
        for (ticks in 1..100) assertEquals(1, (0L..300L).count { HarvestClock.isHarvestTick(0, it, ticks) }, "harvest_ticks $ticks")
        // A file with 0 or less still harvests once, one tick after the start.
        assertEquals(listOf(1L), (0L..300L).filter { HarvestClock.isHarvestTick(0, it, 0) })
    }
}
