package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrewingRulesTest {
    /**
     * Vanilla 1.21.1's `BrewingStandBlockEntity.serverTick`, reduced to its fuel, its brew time and the blaze powder in its fuel slot.
     * The stand ticks before the work engine, so each test calls [tick] and then the rule.
     */
    private class Stand(var fuel: Int, var powder: Int = 0) {
        var brewTime = 0
        var brews = 0

        /** The brew that the stand holds can always start again, as a pair of bottles and ingredient that brew in a loop. */
        fun tick() {
            if (fuel <= 0 && powder > 0) {
                fuel = 20
                powder--
            }
            if (brewTime > 0) {
                brewTime--
                if (brewTime == 0) brews++
            } else if (fuel > 0) {
                fuel--
                brewTime = BrewingRules.VANILLA_BREW_TICKS
            }
        }
    }

    @Test
    fun `CD-BRF-02 a stand that can brew holds at least 1 fuel, and its own fuel stays as it is`() {
        assertEquals(1, BrewingRules.fuelWhileWorking(0, canBrew = true))
        assertEquals(7, BrewingRules.fuelWhileWorking(7, canBrew = true))
        assertEquals(0, BrewingRules.fuelWhileWorking(0, canBrew = false))
    }

    @Test
    fun `CD-BRF-03 vanilla never reloads the blaze powder of a Worked stand over three brews`() {
        val worked = Stand(fuel = 0)
        worked.fuel = BrewingRules.fuelWhileWorking(worked.fuel, canBrew = true)
        worked.powder = 1
        while (worked.brews < 3) {
            worked.tick()
            worked.fuel = BrewingRules.fuelWhileWorking(worked.fuel, canBrew = true)
        }
        assertEquals(1, worked.powder)

        val alone = Stand(fuel = 0, powder = 1)
        alone.tick()
        assertEquals(0, alone.powder, "the model reloads from the powder at 0 fuel, as vanilla does")
    }

    @Test
    fun `CD-BRF-05 a stop leaves no more fuel than the stand held when the work started`() {
        assertEquals(0, BrewingRules.fuelAfterStop(1, atStart = 0))
        assertEquals(19, BrewingRules.fuelAfterStop(19, atStart = 20))
        assertEquals(20, BrewingRules.fuelAfterStop(20, atStart = 20))
        assertEquals(1, BrewingRules.fuelAfterStop(1, atStart = 1))
    }

    @Test
    fun `CD-BRW-02 a brew that a Brewer works from its start ends 200 ticks later, and 400 with no Brewer`() {
        assertEquals(200, ticksToBrew(worked = true))
        assertEquals(400, ticksToBrew(worked = false))
    }

    @Test
    fun `CD-BRW-02 the Brewer takes one extra step and never ends a brew itself`() {
        assertEquals(399, BrewingRules.brewTimeWhileWorking(400))
        assertEquals(1, BrewingRules.brewTimeWhileWorking(2))
        assertEquals(1, BrewingRules.brewTimeWhileWorking(1))
        assertEquals(0, BrewingRules.brewTimeWhileWorking(0))
    }

    /** The ticks from the stand's tick that starts a brew to the stand's tick that ends it. */
    private fun ticksToBrew(worked: Boolean): Int {
        val stand = Stand(fuel = 1)
        stand.tick()
        if (worked) stand.brewTime = BrewingRules.brewTimeWhileWorking(stand.brewTime)
        var ticks = 0
        while (stand.brews == 0) {
            ticks++
            stand.tick()
            if (worked) stand.brewTime = BrewingRules.brewTimeWhileWorking(stand.brewTime)
        }
        return ticks
    }
}
