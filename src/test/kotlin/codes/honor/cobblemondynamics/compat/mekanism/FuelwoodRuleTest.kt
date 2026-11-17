package codes.honor.cobblemondynamics.compat.mekanism

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuelwoodRuleTest {
    /** One tick of Mekanism's heater: it takes a fuel item only at a `burnTime` of 0, then burns up to its multiplier (RES10). */
    private fun mekanismTick(burnTime: Int, multiplier: Int): Pair<Boolean, Int> =
        (burnTime == 0) to (burnTime - minOf(burnTime, multiplier))

    @Test
    fun `CD-FUEL-06 a heater has demand only with a neighbour that takes heat`() {
        assertTrue(FuelwoodRule.hasDemand(neighbourTakesHeat = true))
        assertFalse(FuelwoodRule.hasDemand(neighbourTakesHeat = false))
    }

    @Test
    fun `CD-FUEL-06 a worked heater keeps burnTime at 2 and maxBurnTime equal`() {
        assertEquals(2 to 2, FuelwoodRule.worked(burnTime = 0, maxBurnTime = 0))
        assertEquals(2 to 2, FuelwoodRule.worked(burnTime = 1, maxBurnTime = 1_600))
        assertEquals(2 to 2, FuelwoodRule.worked(burnTime = 2, maxBurnTime = 2))
        // A heater that still burns a log of its own keeps its own values.
        assertEquals(900 to 1_600, FuelwoodRule.worked(burnTime = 900, maxBurnTime = 1_600))
    }

    /** The engine works after the block entities tick, so each tick of the heater starts at the worker's value. */
    @Test
    fun `CD-FUEL-13 a worked heater never reaches Mekanism's fuel step, at any tick multiplier`() {
        for (multiplier in 1..4) {
            var burn = 0
            repeat(10) { tick ->
                burn = FuelwoodRule.worked(burn, burn).first
                val (tookFuel, left) = mekanismTick(burn, multiplier)
                assertFalse(tookFuel, "tick $tick at multiplier $multiplier took a fuel item")
                burn = left
            }
        }
    }

    @Test
    fun `CD-FUEL-14 the burn that a worker leaves runs out within 2 ticks`() {
        var burn = FuelwoodRule.worked(burnTime = 0, maxBurnTime = 0).first
        repeat(2) { burn = mekanismTick(burn, multiplier = 1).second }
        assertEquals(0, burn)
    }
}
