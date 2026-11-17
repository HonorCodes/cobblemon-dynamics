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

    /** A compressor's air handler of [volume] mL that makes [perTick] mL per tick of burn. */
    private fun air(stored: Int, volume: Int = 5_000, perTick: Float = 10f, buffer: Float = 0f) =
        CompressorAir(stored, volume, perTick, buffer)

    @Test
    fun `CD-FUEL-08 a compressor at or above its danger pressure gets no fuel, and one below it does`() {
        assertTrue(PncCompressorRule.hasDemand(air(stored = 24_000), dangerPressure = 5f, runs = true))
        assertFalse(PncCompressorRule.hasDemand(air(stored = 25_000), dangerPressure = 5f, runs = true))
        assertFalse(PncCompressorRule.hasDemand(air(stored = 32_500), dangerPressure = 5f, runs = true))
        assertTrue(PncCompressorRule.hasDemand(air(stored = 99_000, perTick = 50f), dangerPressure = 20f, runs = true))
        assertFalse(PncCompressorRule.hasDemand(air(stored = 0, volume = 0), dangerPressure = 5f, runs = true))
    }

    /**
     * PneumaticCraft 8.2.24 (javap): a tick that burns adds `airPerTick` to the private `airBuffer`, then adds the whole mL of the
     * buffer to the air. Burn that the worker gives in one tick makes at most ceil(airBuffer + airPerTick) mL in the next, so the worker
     * stops while that air would take the pressure to the danger pressure.
     */
    @Test
    fun `CD-FUEL-08 a compressor gets no fuel when the air of its next tick would reach its danger pressure`() {
        // 4.99 bar is below 5 bar, but the next 10 mL make 5 bar.
        assertFalse(PncCompressorRule.hasDemand(air(stored = 4_990, volume = 1_000), dangerPressure = 5f, runs = true))
        assertTrue(PncCompressorRule.hasDemand(air(stored = 4_989, volume = 1_000), dangerPressure = 5f, runs = true))
        // The buffer's fraction rounds up: 0.5 + 9.6 mL can add 11 mL.
        assertFalse(PncCompressorRule.hasDemand(air(4_989, 1_000, perTick = 9.6f, buffer = 0.5f), dangerPressure = 5f, runs = true))
        assertTrue(PncCompressorRule.hasDemand(air(4_988, 1_000, perTick = 9.6f, buffer = 0.5f), dangerPressure = 5f, runs = true))
        // Without the mixin that reads the buffer, the bound of 1 mL takes its place.
        assertFalse(PncCompressorRule.hasDemand(air(4_989, 1_000, buffer = PncCompressorRule.UNKNOWN_BUFFER), 5f, runs = true))
        assertTrue(PncCompressorRule.hasDemand(air(4_988, 1_000, buffer = PncCompressorRule.UNKNOWN_BUFFER), 5f, runs = true))
    }

    /** A tier 1 machine of 5 bar beside an Advanced Air Compressor of 20 bar takes its air with no tier check. */
    @Test
    fun `CD-FUEL-08 the danger pressure is the lowest of the compressor and the machines connected to it`() {
        val danger = PncAirMath.lowestDanger(20f, listOf(20f, 5f))
        assertEquals(5f, danger)
        assertEquals(20f, PncAirMath.lowestDanger(20f, emptyList()))
        assertFalse(PncCompressorRule.hasDemand(air(stored = 4_960, volume = 1_000, perTick = 50f), danger, runs = true))
        assertTrue(PncCompressorRule.hasDemand(air(stored = 4_949, volume = 1_000, perTick = 50f), danger, runs = true))
        assertTrue(PncCompressorRule.hasDemand(air(stored = 4_960, volume = 1_000, perTick = 50f), dangerPressure = 20f, runs = true))
    }

    /** PneumaticCraft's tick and then the engine's, for 2,000 ticks: the pressure never reaches the danger pressure. */
    @Test
    fun `CD-FUEL-08 worked tick after tick, a compressor never reaches its danger pressure`() {
        for (perTick in listOf(10f, 9.6f, 37.5f, 50f)) {
            var stored = 0
            var buffer = 0f
            var burn = 0
            repeat(2_000) { tick ->
                if (burn >= 1) {
                    burn--
                    buffer += perTick
                    val whole = buffer.toInt()
                    stored += whole
                    buffer -= whole
                }
                assertTrue(stored.toFloat() / 1_000f < 5f, "at $perTick mL per tick, tick $tick reached ${stored / 1_000f} bar")
                if (PncCompressorRule.hasDemand(CompressorAir(stored, 1_000, perTick, buffer), 5f, runs = true)) burn++
            }
            assertTrue(stored > 4_900, "at $perTick mL per tick the compressor stopped at $stored mL")
        }
    }

    /** The compressor's own redstone setting stops its fuel intake, so it stops the worker's fuel too. */
    @Test
    fun `CD-FUEL-08 a compressor that its redstone setting stops gets no fuel`() {
        assertFalse(PncCompressorRule.hasDemand(air(stored = 0), dangerPressure = 5f, runs = false))
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
