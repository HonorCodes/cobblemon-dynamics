package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The stall of the Crystal Tender's default file, which counts extra ticks, one every 10 ticks of work. */
class CrystalStallTest {
    private val params = BuiltInRoles.parse("crystal_tender").params

    @Test
    fun `CD-MCH-05 the default file stops a target after 200 unchanged extra ticks, for 6,000 ticks`() {
        assertEquals(200, params.get("stall_after_unchanged_ticks")?.asInt)
        assertEquals(6_000, params.get("stall_wait_ticks")?.asInt)
        val stall = CrystalStall.of(params)
        for (tick in 0 until 199) assertFalse(stall.record(changed = false, now = tick * 10L), "extra tick ${tick + 1} stopped the target")
        assertFalse(stall.isStalled(1_990), "199 unchanged extra ticks stopped the target")
        assertTrue(stall.record(changed = false, now = 1_990), "the 200th unchanged extra tick did not stop the target")
        assertTrue(stall.isStalled(1_990))
        assertTrue(stall.isStalled(1_990 + 5_999))
        assertFalse(stall.isStalled(1_990 + 6_000), "the stop outlived 6,000 ticks")
        assertEquals(6_000L, stall.waitTicks)
    }

    @Test
    fun `CD-MCH-05 a changed block resets the count`() {
        val stall = CrystalStall.of(params)
        for (tick in 0 until 199) stall.record(changed = false, now = tick.toLong())
        stall.record(changed = true, now = 199)
        for (tick in 0 until 199) assertFalse(stall.record(changed = false, now = 200L + tick), "the count did not start again")
        assertTrue(stall.record(changed = false, now = 399))
    }

    @Test
    fun `CD-MCH-05 a file with no stall keys gets the defaults`() {
        val stall = CrystalStall.of(JsonObject())
        assertEquals(CrystalStall.DEFAULT_WAIT_TICKS, stall.waitTicks)
        repeat(CrystalStall.DEFAULT_UNCHANGED_TICKS - 1) { assertFalse(stall.record(changed = false, now = it.toLong())) }
        assertTrue(stall.record(changed = false, now = 1_000))
    }
}
