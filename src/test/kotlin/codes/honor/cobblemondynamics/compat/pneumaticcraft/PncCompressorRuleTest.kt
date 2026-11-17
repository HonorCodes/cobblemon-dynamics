package codes.honor.cobblemondynamics.compat.pneumaticcraft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PncCompressorRuleTest {
    /**
     * One tick of PneumaticCraft 8.2.24's compressor (RES12): it takes a fuel item while `burnTime` is below its fuel use, then burns
     * its fuel use when it has that much. The fuel item's own burn is left out, so the test sees only what the worker gave.
     */
    private fun pncTick(burnTime: Int, usage: Int): Pair<Boolean, Int> =
        (burnTime < usage) to (if (burnTime >= usage) burnTime - usage else burnTime)

    @Test
    fun `CD-FUEL-08 the base burn rate is 1 for the Air Compressor and 5 for the Advanced Air Compressor`() {
        assertEquals(1, PncCompressorRule.baseBurnRate(baseProduction = 10))
        assertEquals(5, PncCompressorRule.baseBurnRate(baseProduction = 50))
    }

    @Test
    fun `CD-FUEL-08 a worked tick adds the base rate, also to a compressor whose Speed Upgrades burn more`() {
        assertEquals(1, PncCompressorRule.worked(CompressorBurn(0, 0), rate = 1).time)
        assertEquals(5, PncCompressorRule.worked(CompressorBurn(0, 0), rate = 5).time)
        // With Speed Upgrades the compressor burns 2 or more per tick, and the worker still gives 1: the upgrades cost fuel.
        assertEquals(4, PncCompressorRule.worked(CompressorBurn(3, 3), rate = 1).time)
    }

    @Test
    fun `CD-FUEL-08 a compressor at or above its danger pressure gets no fuel, and one below it does`() {
        assertTrue(PncCompressorRule.hasDemand(pressure = 4.99f, dangerPressure = 5f, runs = true))
        assertFalse(PncCompressorRule.hasDemand(pressure = 5f, dangerPressure = 5f, runs = true))
        assertFalse(PncCompressorRule.hasDemand(pressure = 6.5f, dangerPressure = 5f, runs = true))
        assertTrue(PncCompressorRule.hasDemand(pressure = 19.9f, dangerPressure = 20f, runs = true))
    }

    /** The compressor's own redstone setting stops its fuel intake, so it stops the worker's fuel too. */
    @Test
    fun `CD-FUEL-08 a compressor that its redstone setting stops gets no fuel`() {
        assertFalse(PncCompressorRule.hasDemand(pressure = 0f, dangerPressure = 5f, runs = false))
    }

    @Test
    fun `CD-FUEL-08 the gauge is full while only the worker's burn remains, and a burning fuel item keeps its own gauge`() {
        assertEquals(CompressorBurn(1, 1), PncCompressorRule.worked(CompressorBurn(0, 1_600), rate = 1))
        assertEquals(CompressorBurn(5, 5), PncCompressorRule.worked(CompressorBurn(0, 0), rate = 5))
        assertEquals(CompressorBurn(1_001, 1_600), PncCompressorRule.worked(CompressorBurn(1_000, 1_600), rate = 1))
    }

    /** The engine works after the block entities tick, so each tick of the compressor starts at the worker's value. */
    @Test
    fun `CD-FUEL-13 a worked compressor never reaches its own fuel step, at either base rate`() {
        for (rate in listOf(1, 5)) {
            var burn = CompressorBurn(0, 0)
            repeat(20) { tick ->
                burn = PncCompressorRule.worked(burn, rate)
                val (tookFuel, left) = pncTick(burn.time, usage = rate)
                assertFalse(tookFuel, "tick $tick at rate $rate took a fuel item")
                burn = burn.copy(time = left)
            }
            assertEquals(0, burn.time, "a worked compressor keeps burn ticks at rate $rate")
        }
    }

    @Test
    fun `CD-FUEL-14 the burn that a worker leaves runs out within 2 ticks`() {
        for (rate in listOf(1, 5)) {
            val left = PncCompressorRule.worked(CompressorBurn(0, 0), rate).time
            val (firstTookFuel, afterFirst) = pncTick(left, usage = rate)
            assertFalse(firstTookFuel)
            val (secondTookFuel, _) = pncTick(afterFirst, usage = rate)
            assertTrue(secondTookFuel, "at rate $rate the compressor is back on its own fuel in the second tick")
        }
    }
}
