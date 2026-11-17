package codes.honor.cobblemondynamics.compat.ae2

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Ae2CrankRuleTest {
    private val interval = Ae2CrankRule.DEFAULT_INTERVAL_TICKS

    @Test
    fun `CD-KIN-06 one turn every 18 ticks from the start of the work`() {
        val since = 1_000L
        val turns = (since until since + 60).filter {
            Ae2CrankRule.shouldTurn(canTurn = true, since = since, now = it, intervalTicks = interval)
        }
        assertEquals(listOf(1_000L, 1_018L, 1_036L, 1_054L), turns)
        assertFalse(Ae2CrankRule.shouldTurn(canTurn = true, since = since, now = since + 17, intervalTicks = interval))
    }

    @Test
    fun `CD-KIN-06 no turn while the machine cannot take one`() {
        for (now in 1_000L until 1_100L) {
            assertFalse(Ae2CrankRule.shouldTurn(canTurn = false, since = 1_000L, now = now, intervalTicks = interval), "a turn at $now")
        }
    }

    @Test
    fun `CD-KIN-06 the role file's interval sets the pace, and a missing one falls back to 18`() {
        assertEquals(18, Ae2CrankRule.intervalOf(JsonObject()))
        val params = JsonObject().apply { addProperty("interval_ticks", 36) }
        assertEquals(36, Ae2CrankRule.intervalOf(params))
        assertTrue(Ae2CrankRule.shouldTurn(canTurn = true, since = 0, now = 36, intervalTicks = Ae2CrankRule.intervalOf(params)))
        assertFalse(Ae2CrankRule.shouldTurn(canTurn = true, since = 0, now = 18, intervalTicks = Ae2CrankRule.intervalOf(params)))
    }
}
