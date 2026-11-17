package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.railcraft.RailcraftBoilerRule.Pool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RailcraftBoilerRuleTest {
    /**
     * One boiler pattern of Railcraft Reborn 1.2.17 (`SteamBoilerBlockEntity.buildPatterns`): w × w tanks h high over a firebox of
     * w × w blocks. Its fuel step comes every 8 ticks at high pressure and every 16 at low pressure, and its top temperature is 1,000
     * or 500.
     */
    private data class Pattern(val width: Int, val height: Int, val high: Boolean) {
        val tanks = width * width * height
        val fireboxes = width * width
        val ticksPerCycle = if (high) 8 else 16
        val maxTemperature = if (high) 1_000f else 500f
    }

    private val oneTank = Pattern(1, 1, high = false)
    private val lowTwoByTwo = Pattern(2, 2, high = false)
    private val highThreeByFour = Pattern(3, 4, high = true)

    /** Every pattern of the jar: the sizes 3 × 3 × 4, 3 × 3 × 3, 3 × 3 × 2, 2 × 2 × 3, 2 × 2 × 2 and 1 × 1 × 1, at both pressures. */
    private val patterns = listOf(3 to 4, 3 to 3, 3 to 2, 2 to 3, 2 to 2, 1 to 1).flatMap { (width, height) ->
        listOf(Pattern(width, height, high = true), Pattern(width, height, high = false))
    }

    /** A full pool of each firebox block's worker, Working, over half a tank of water: 2,000 mB for each tank. */
    private fun fullPool(pattern: Pattern) =
        Pool(pattern.fireboxes, pattern.fireboxes, pattern.fireboxes, pattern.tanks * 2_000, pattern.tanks * TANK)

    /**
     * A copy of Railcraft's `SteamBoiler.tick(tanks)` (1.2.17) and its heat, with the solid firebox's heat step of 0.05. At each fuel
     * step it takes fuel items while `burnTime` is below `getFuelPerCycle`, then burns that need, or sets `burnTime` to 0 when it
     * cannot reach it. [offset] starts its cycle part way, so that the pool starts at any point of a cycle.
     */
    private class Boiler(val pattern: Pattern, var fuelItems: Int, offset: Int = 0, var temperature: Float = COLD) {
        var burnTime = 0f
        var fuelTaken = 0
        var steps = 0

        /** What the last fuel step burned. */
        var lastNeed = 0f
        private var burning = false
        private var burnCycle = offset

        val heat: Float get() = temperature / pattern.maxTemperature

        fun need(): Float = jarNeed(pattern.tanks, heat, pattern.maxTemperature)

        fun tick() {
            burnCycle++
            if (burnCycle >= pattern.ticksPerCycle) {
                burnCycle = 0
                val need = need()
                while (burnTime < need && fuelItems > 0) {
                    fuelItems--
                    fuelTaken++
                    burnTime += COAL
                }
                burning = burnTime >= need
                burnTime = if (burning) burnTime - need else 0f
                lastNeed = need
                steps++
            }
            if (burning) increaseHeat() else reduceHeat()
        }

        /** `increaseHeat(tanks)`: nothing at the top, else the heat step plus 3 times its share of the gap to the top. */
        private fun increaseHeat() {
            val max = pattern.maxTemperature
            if (temperature == max) return
            val change = (HEAT_STEP + (max - temperature) / max * HEAT_STEP * 3f) / pattern.tanks
            temperature = (temperature + change).coerceIn(COLD, max)
        }

        /** `reduceHeat(tanks)`: nothing when cold, else the heat step plus 3 times its share of the heat. */
        private fun reduceHeat() {
            if (temperature == COLD) return
            val change = (HEAT_STEP + temperature / pattern.maxTemperature * HEAT_STEP * 3f) / pattern.tanks
            temperature = (temperature - change).coerceIn(COLD, pattern.maxTemperature)
        }
    }

    /** One fuel step of a run: what the pool gave since the step before, and what this step burned. */
    private data class Step(val gift: Float, val need: Float)

    /**
     * The engine works after the block entities tick, so from the tick that the pool starts, its top-up to
     * [RailcraftBoilerRule.poolBurn] comes before each later tick of the boiler.
     */
    private fun run(boiler: Boiler, pool: Pool, steps: Int): List<Step> {
        val done = mutableListOf<Step>()
        var gift = 0f
        while (boiler.steps < steps) {
            if (RailcraftBoilerRule.supplies(pool)) {
                val pattern = boiler.pattern
                val burn = RailcraftBoilerRule.poolBurn(boiler.need(), pattern.tanks, boiler.heat, pattern.maxTemperature)
                if (boiler.burnTime < burn) {
                    gift += burn - boiler.burnTime
                    boiler.burnTime = burn
                }
            }
            val before = boiler.steps
            boiler.tick()
            if (boiler.steps > before) {
                done += Step(gift, boiler.lastNeed)
                gift = 0f
            }
        }
        return done
    }

    @Test
    fun `CD-FUEL-05 the rule's need is the jar's getFuelPerCycle, about 41 burn ticks per tick for 36 high-pressure tanks`() {
        // (8 − 0.1 × T + 0.8 × heat + 4 × M ÷ 1,000) × T at the top temperature (BM14).
        assertEquals(10.7f, RailcraftBoilerRule.fuelPerCycle(1, 1f, 500f), 1e-4f)
        assertEquals(80f, RailcraftBoilerRule.fuelPerCycle(8, 1f, 500f), 1e-3f)
        assertEquals(331.2f, RailcraftBoilerRule.fuelPerCycle(36, 1f, 1_000f), 1e-3f)
        assertEquals(41.4f, RailcraftBoilerRule.fuelPerCycle(36, 1f, 1_000f) / 8, 1e-3f)
        // A cold boiler sits at 20, 4 % of a low-pressure boiler's top.
        assertEquals(9.932f, RailcraftBoilerRule.fuelPerCycle(1, 0.04f, 500f), 1e-4f)
        for (pattern in patterns) {
            for (heat in listOf(0.02f, 0.04f, 0.5f, 0.97f, 1f)) {
                val tanks = pattern.tanks
                assertEquals(
                    jarNeed(tanks, heat, pattern.maxTemperature),
                    RailcraftBoilerRule.fuelPerCycle(tanks, heat, pattern.maxTemperature),
                )
            }
        }
    }

    @Test
    fun `CD-FUEL-05 the burn that the pool keeps covers the need at the boiler's top temperature, whatever its heat now`() {
        for (pattern in patterns) {
            val top = jarNeed(pattern.tanks, 1f, pattern.maxTemperature)
            for (heat in listOf(0.02f, 0.3f, 0.99f, 1f)) {
                val burn = RailcraftBoilerRule.poolBurn(
                    jarNeed(pattern.tanks, heat, pattern.maxTemperature),
                    pattern.tanks,
                    heat,
                    pattern.maxTemperature,
                )
                assertTrue(burn >= top, "$pattern at heat $heat keeps $burn of $top")
                assertTrue(burn <= top * 1.02f, "$pattern at heat $heat keeps $burn, more than its top need $top")
            }
        }
    }

    /**
     * The boilers of R85: 1 low-pressure tank over 1 firebox block, 2 × 2 × 2 low-pressure tanks over 4 and 3 × 3 × 4 high-pressure
     * tanks over 9. From cold, at any point of a cycle, the full pool keeps each one from its fuel step for 100 steps, so its 1 coal
     * stays in the fuel slot.
     */
    @Test
    fun `CD-FUEL-05 CD-FUEL-13 a full pool keeps the boilers of R85 from their fuel step, from cold, with 1 coal in the slot`() {
        for (pattern in listOf(oneTank, lowTwoByTwo, highThreeByFour)) {
            for (offset in 0 until pattern.ticksPerCycle) {
                val boiler = Boiler(pattern, fuelItems = 1, offset = offset)
                run(boiler, fullPool(pattern), steps = 100)
                assertEquals(0, boiler.fuelTaken, "$pattern from offset $offset took a fuel item")
                assertEquals(1, boiler.fuelItems)
            }
        }
    }

    @Test
    fun `CD-FUEL-13 a full pool keeps every boiler pattern of the jar from its fuel step`() {
        for (pattern in patterns) {
            val boiler = Boiler(pattern, fuelItems = 1, offset = 5)
            run(boiler, fullPool(pattern), steps = 200)
            assertEquals(0, boiler.fuelTaken, "$pattern took a fuel item")
        }
    }

    /**
     * After its first fill, the pool gives back at each fuel step what the step before burned, so it supplies a boiler's need and no
     * more. At the top temperature that is need ÷ F for each of the F workers (BM14).
     */
    @Test
    fun `CD-FUEL-05 each worker of a hot boiler covers need divided by its firebox blocks at each fuel step`() {
        val shares = mapOf(oneTank to 10.7f, lowTwoByTwo to 20f, highThreeByFour to 36.8f)
        for ((pattern, share) in shares) {
            val boiler = Boiler(pattern, fuelItems = 0, temperature = pattern.maxTemperature)
            val steps = run(boiler, fullPool(pattern), steps = 40)
            for (k in 1 until steps.size) assertEquals(steps[k - 1].need, steps[k].gift, 1e-3f, "$pattern at step $k")
            assertEquals(share, steps.last().gift / pattern.fireboxes, 1e-3f, "$pattern")
            assertEquals(0, boiler.fuelTaken)
        }
    }

    @Test
    fun `CD-FUEL-05 with a firebox block short of its worker there is no demand, and the boiler burns its own fuel`() {
        val pattern = highThreeByFour
        val short = fullPool(pattern).copy(held = 8, working = 8)
        assertFalse(RailcraftBoilerRule.hasDemand(short))
        assertFalse(RailcraftBoilerRule.supplies(short))
        val boiler = Boiler(pattern, fuelItems = 1)
        run(boiler, short, steps = 1)
        assertEquals(1, boiler.fuelTaken, "the boiler burns its own coal at its first fuel step")
        assertFalse(RailcraftBoilerRule.hasDemand(Pool(0, 0, 0, 2_000, TANK)), "an unformed boiler has no firebox block")
    }

    @Test
    fun `CD-FUEL-05 the pool fuels its boiler only while the worker of every firebox block Works`() {
        val pool = fullPool(lowTwoByTwo)
        assertTrue(RailcraftBoilerRule.hasDemand(pool))
        assertTrue(RailcraftBoilerRule.supplies(pool))
        // A worker still on its way holds its block, so the others Work, but nobody gives fuel until it Works too.
        val walking = pool.copy(working = 3)
        assertTrue(RailcraftBoilerRule.hasDemand(walking))
        assertFalse(RailcraftBoilerRule.supplies(walking))
    }

    /**
     * Railcraft takes a boiler's fuel only at its fuel steps, so the burn time that a pool leaves when it stops, at most its top-up,
     * pays one fuel step at most: 16 ticks at low pressure and 8 at high (R85). From any point of a cycle, the boiler then burns its
     * own coal at its next fuel step.
     */
    @Test
    fun `CD-FUEL-14 the burn time that a stopped pool leaves pays one fuel step of the boiler at most`() {
        for (pattern in patterns) {
            for (temperature in listOf(COLD, pattern.maxTemperature)) {
                for (offset in 0 until pattern.ticksPerCycle) {
                    val boiler = Boiler(pattern, fuelItems = 1, offset = offset, temperature = temperature)
                    boiler.burnTime = RailcraftBoilerRule.poolBurn(boiler.need(), pattern.tanks, boiler.heat, pattern.maxTemperature)
                    while (boiler.steps < 2) boiler.tick()
                    assertEquals(1, boiler.fuelTaken, "$pattern at $temperature, stopped at offset $offset")
                }
            }
        }
    }

    @Test
    fun `CD-FUEL-15 a boiler below half of its water has no demand and gets no fuel, hot or cold`() {
        assertFalse(RailcraftBoilerRule.hasWater(1_999, TANK))
        assertTrue(RailcraftBoilerRule.hasWater(2_000, TANK))
        assertTrue(RailcraftBoilerRule.hasWater(TANK, TANK))
        assertFalse(RailcraftBoilerRule.hasWater(0, TANK), "an empty boiler explodes when water reaches it superheated")
        assertFalse(RailcraftBoilerRule.hasWater(0, 0), "an unformed boiler has no tank")
        val dry = fullPool(oneTank).copy(water = 1_999)
        assertFalse(RailcraftBoilerRule.hasDemand(dry))
        assertFalse(RailcraftBoilerRule.supplies(dry))
        // A superheated boiler, at 300 or more, gets nothing below half of its water either.
        val hot = Boiler(oneTank, fuelItems = 0, temperature = 400f)
        run(hot, dry, steps = 3)
        assertEquals(0f, hot.burnTime)
        assertTrue(RailcraftBoilerRule.hasDemand(fullPool(highThreeByFour).copy(water = 36 * 2_000)))
        assertFalse(RailcraftBoilerRule.hasDemand(fullPool(highThreeByFour).copy(water = 36 * 2_000 - 1)))
    }

    private companion object {
        /** `SteamBoilerModule.update` gives the water tank 4,000 mB for each tank. */
        const val TANK = 4_000

        /** A coal's burn value in a solid firebox, with Railcraft's `fuelMultiplier` at its default of 1. */
        const val COAL = 1_600f

        /** `SolidFuelProvider.getHeatStep()` for every fuel but refined firestone. */
        const val HEAT_STEP = 0.05f

        /** A boiler's lowest temperature, at which it starts. */
        const val COLD = 20f

        /**
         * Railcraft's `SteamBoiler.getFuelPerCycle(tanks)`, written down from its bytecode, in float, with the boiler's efficiency and
         * `fuelPerSteamMultiplier` at their default of 1.
         */
        fun jarNeed(tanks: Int, heat: Float, maxTemperature: Float): Float {
            var need = 8.0f
            need -= tanks.toFloat() * 8.0f * 0.0125f
            need += 0.8f * heat
            need += 4.0f * (maxTemperature / 1000.0f)
            need *= tanks.toFloat()
            return need
        }
    }
}
