package codes.honor.cobblemondynamics.compat.cobblemon_incubator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The incubator's own speeds come from its config: `baseSpeed` without a Speed Upgrade and `upgradedSpeed` with one. */
class IncubatorRuleTest {
    private val base = 1
    private val upgraded = 3

    @Test
    fun `CD-FUEL-11 a worked incubator with no Speed Upgrade runs at the speed of one`() {
        assertEquals(upgraded, IncubatorRule.speed(own = base, upgraded = upgraded, worked = true))
    }

    @Test
    fun `CD-FUEL-11 a worked incubator with a Speed Upgrade gets nothing more`() {
        assertEquals(upgraded, IncubatorRule.speed(own = upgraded, upgraded = upgraded, worked = true))
        assertFalse(IncubatorRule.hasDemand(hasEgg = true, hasSpeedUpgrade = true))
    }

    @Test
    fun `CD-FUEL-11 an incubator has demand while it holds an egg and no Speed Upgrade`() {
        assertTrue(IncubatorRule.hasDemand(hasEgg = true, hasSpeedUpgrade = false))
        assertFalse(IncubatorRule.hasDemand(hasEgg = false, hasSpeedUpgrade = false))
    }

    /** The worker's speed lives only in the tick that it works, so the incubator's own speed returns at once (CD-FUEL-14). */
    @Test
    fun `CD-FUEL-13 CD-FUEL-14 an incubator that no worker works runs at its own speed`() {
        assertEquals(base, IncubatorRule.speed(own = base, upgraded = upgraded, worked = false))
    }
}
