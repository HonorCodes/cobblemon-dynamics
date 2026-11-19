package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    /**
     * The Brew Stoker's part of one tick, after the stand's: it Works while it has demand and stops when the demand ends. Returns
     * whether it Worked.
     */
    private fun stoke(stand: Stand, state: IntArray): Boolean {
        val demand = BrewingRules.stokerDemand(canBrew = true, brewTime = stand.brewTime, fuel = stand.fuel)
        if (demand) {
            if (state[0] < 0) state[0] = stand.fuel
            stand.fuel = BrewingRules.fuelWhileWorking(stand.fuel, canBrew = true)
        } else if (state[0] >= 0) {
            stand.fuel = BrewingRules.fuelAfterStop(stand.fuel, state[0])
            state[0] = -1
        }
        return demand
    }

    @Test
    fun `CD-BRF-04 a Brew Stoker has demand only while a brew waits for fuel`() {
        assertTrue(BrewingRules.stokerDemand(canBrew = true, brewTime = 0, fuel = 0))
        assertFalse(BrewingRules.stokerDemand(canBrew = true, brewTime = 400, fuel = 0), "a running brew needs no fuel")
        assertFalse(BrewingRules.stokerDemand(canBrew = true, brewTime = 0, fuel = 1), "the stand starts the brew from its own fuel")
        assertFalse(BrewingRules.stokerDemand(canBrew = false, brewTime = 0, fuel = 0), "nothing to brew")
    }

    /** Decision 20 of the 1.2.1 plan: the Brew Stoker lights each brew on one tick of Work, and the brews run with no fuel. */
    @Test
    fun `CD-BRF-02 CD-BRF-05 a Brew Stoker lights three brews from fuel 0 and leaves fuel 0`() {
        val stand = Stand(fuel = 0)
        val state = intArrayOf(-1)
        var worked = 0
        while (true) {
            stand.tick()
            if (stand.brews == 3) break
            if (stoke(stand, state)) worked++
        }
        assertEquals(3, worked, "one Working tick for each brew")
        assertEquals(0, stand.fuel)
    }

    /**
     * Vanilla turns the powder into 20 fuel at 0 fuel, before the engine's tick. The Brew Stoker then has no demand, so it adds no fuel
     * and takes no powder: the stand brews from the powder's 20 as it would with no worker.
     */
    @Test
    fun `CD-BRF-03 a stand with blaze powder fuels itself and the Brew Stoker never Works there`() {
        val stand = Stand(fuel = 0, powder = 1)
        val state = intArrayOf(-1)
        var worked = 0
        while (true) {
            stand.tick()
            if (stand.brews == 3) break
            if (stoke(stand, state)) worked++
        }
        assertEquals(0, worked)
        assertEquals(0, stand.powder, "the model reloads from the powder at 0 fuel, as vanilla does")
        assertEquals(17, stand.fuel, "three brews from the powder's 20 fuel")
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
