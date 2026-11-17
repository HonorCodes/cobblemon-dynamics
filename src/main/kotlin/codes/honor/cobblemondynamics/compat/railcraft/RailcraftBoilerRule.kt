package codes.honor.cobblemondynamics.compat.railcraft

/**
 * The Stoker's rules on the solid-fueled boilers of Railcraft Reborn 1.2.17, over plain facts, so that unit tests load them with no
 * Railcraft class.
 *
 * `SteamBoiler.tick` has a fuel step every 16 ticks at low pressure and every 8 at high pressure. The step takes fuel items while
 * `burnTime` is below `getFuelPerCycle(tanks)`, then burns that need, or sets `burnTime` to 0 when it cannot reach it, so burn time
 * below the need is lost. The workers of one boiler therefore pool their supply. A worker holds each block of the firebox, and while
 * all of them Work, they keep `burnTime` at the need of a step at the boiler's top temperature. No step then takes a fuel item, and
 * the F workers cover each step's need, need ÷ F each (CD-FUEL-05, CD-FUEL-13).
 *
 * `SteamBoiler.checkFill` explodes a boiler that takes water while it is superheated, at 300 or more, with an empty water tank. The
 * workers never heat a boiler below half of its water (CD-FUEL-15).
 */
object RailcraftBoilerRule {
    /**
     * One boiler: its firebox blocks, the blocks that a worker of the role holds, the blocks whose worker Works, and its water and
     * the capacity of its water tank in mB.
     */
    data class Pool(val fireboxes: Int, val held: Int, val working: Int, val water: Int, val capacity: Int)

    /** The need at the top temperature, plus this much against rounding, is the burn time that the pool keeps. */
    private const val MARGIN = 1.01f

    /** CD-FUEL-15: half of the water tank or more. An unformed boiler has no tank. */
    fun hasWater(water: Int, capacity: Int): Boolean = capacity > 0 && 2L * water >= capacity

    /** CD-FUEL-05: a worker of the role holds every firebox block of a boiler that has its water. */
    fun hasDemand(pool: Pool): Boolean = pool.fireboxes > 0 && pool.held == pool.fireboxes && hasWater(pool.water, pool.capacity)

    /** CD-FUEL-05: the pool fuels the boiler only while the worker of every firebox block Works. */
    fun supplies(pool: Pool): Boolean = hasDemand(pool) && pool.working == pool.fireboxes

    /**
     * Railcraft's `getFuelPerCycle(tanks)` with its two multipliers at their default of 1, the boiler's efficiency and
     * `fuelPerSteamMultiplier` of Railcraft's server config: (8 − 0.1 × tanks + 0.8 × heat + 4 × maxTemperature ÷ 1,000) × tanks,
     * where heat is the temperature ÷ maxTemperature. In float, in the jar's order.
     */
    fun fuelPerCycle(tanks: Int, heat: Float, maxTemperature: Float): Float {
        var need = 8f
        need -= tanks.toFloat() * 8f * 0.0125f
        need += 0.8f * heat
        need += 4f * (maxTemperature / 1000f)
        return need * tanks.toFloat()
    }

    /**
     * The burn time that the pool keeps: [need], Railcraft's own need at [heat], scaled to the top temperature, where the need is
     * highest, plus 1 % against rounding. The heat rises during a cycle, so the next step can need more than the need of now. The
     * multipliers of Railcraft's need cancel out of the scale.
     */
    fun poolBurn(need: Float, tanks: Int, heat: Float, maxTemperature: Float): Float {
        val now = fuelPerCycle(tanks, heat.coerceIn(0f, 1f), maxTemperature)
        if (now <= 0f) return need * MARGIN
        return need * (fuelPerCycle(tanks, 1f, maxTemperature) / now) * MARGIN
    }
}
