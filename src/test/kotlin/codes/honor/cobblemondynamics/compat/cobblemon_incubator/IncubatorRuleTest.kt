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
        assertEquals(upgraded, IncubatorRule.speed(own = base, upgraded = upgraded, upgradesEnabled = true, worked = true))
    }

    @Test
    fun `CD-FUEL-11 a worked incubator with a Speed Upgrade gets nothing more`() {
        assertEquals(upgraded, IncubatorRule.speed(own = upgraded, upgraded = upgraded, upgradesEnabled = true, worked = true))
        assertFalse(IncubatorRule.hasDemand(hasEgg = true, upgradesEnabled = true, hasSpeedUpgrade = true) { true })
    }

    /** R88: the worker respects the incubator's `speedUpgradeEnabled`, so a server that turns Speed Upgrades off gets none from it. */
    @Test
    fun `CD-FUEL-11 with Speed Upgrades off the worker gives no speed and the incubator has no demand`() {
        assertEquals(base, IncubatorRule.speed(own = base, upgraded = upgraded, upgradesEnabled = false, worked = true))
        assertFalse(IncubatorRule.hasDemand(hasEgg = true, upgradesEnabled = false, hasSpeedUpgrade = false) { true })
    }

    @Test
    fun `CD-FUEL-11 an incubator has demand while it incubates an egg and has no Speed Upgrade`() {
        assertTrue(IncubatorRule.hasDemand(hasEgg = true, upgradesEnabled = true, hasSpeedUpgrade = false) { true })
        assertFalse(IncubatorRule.hasDemand(hasEgg = false, upgradesEnabled = true, hasSpeedUpgrade = false) { true }, "no egg")
        assertFalse(IncubatorRule.hasDemand(hasEgg = true, upgradesEnabled = true, hasSpeedUpgrade = false) { false }, "an egg it holds")
    }

    /**
     * `EggIncubatorBlockEntity.tickServer` of the 1.1.1 jar rejects an egg that fails the filter, unless the config ignores the filter
     * on a shiny egg, and then holds an egg for an Analyse Upgrade, before it counts the egg's timer down at its speed.
     */
    @Test
    fun `CD-FUEL-11 an Analyse Upgrade or a filter that rejects the egg stops the incubation`() {
        assertTrue(IncubatorRule.incubates(analysing = false, passesFilter = true, shiny = false, ignoreFilterOnShiny = false))
        assertFalse(IncubatorRule.incubates(analysing = true, passesFilter = true, shiny = false, ignoreFilterOnShiny = false), "analyse")
        assertFalse(IncubatorRule.incubates(analysing = false, passesFilter = false, shiny = false, ignoreFilterOnShiny = false), "filter")
        assertFalse(IncubatorRule.incubates(analysing = false, passesFilter = false, shiny = true, ignoreFilterOnShiny = false), "shiny")
        assertTrue(IncubatorRule.incubates(analysing = false, passesFilter = false, shiny = true, ignoreFilterOnShiny = true), "ignored")
    }

    /** The incubator's private `passesFilter`: no filter with criteria passes every egg, and a filter passes only an egg that matches it. */
    @Test
    fun `CD-FUEL-11 the filter check mirrors the incubator's own`() {
        assertTrue(IncubatorRule.passesFilter(criteria = false, matches = null), "no Filter Upgrade, or one with no criteria")
        assertTrue(IncubatorRule.passesFilter(criteria = true, matches = true))
        assertFalse(IncubatorRule.passesFilter(criteria = true, matches = false))
        assertFalse(IncubatorRule.passesFilter(criteria = true, matches = null), "an egg with no properties")
    }

    /** The worker's speed lives only in the tick that it works, so the incubator's own speed returns at once (CD-FUEL-14). */
    @Test
    fun `CD-FUEL-13 CD-FUEL-14 an incubator that no worker works runs at its own speed`() {
        assertEquals(base, IncubatorRule.speed(own = base, upgraded = upgraded, upgradesEnabled = true, worked = false))
    }
}
