package codes.honor.cobblemondynamics.compat.mekanism

/**
 * The Stoker's rules on Mekanism's Fuelwood Heater, over plain facts, so that unit tests load them with no Mekanism class. The
 * heater's tick takes a fuel item only while `burnTime` is 0, and otherwise burns up to `fuelwoodTickMultiplier` of it and makes
 * `heatPerFuelTick` J of heat for each (RES10). The engine works after the block entities tick, so a worker that leaves `burnTime`
 * at 2 fuels the heater at its own rate, and its fuel slot stays full (CD-FUEL-06, CD-FUEL-13).
 */
object FuelwoodRule {
    /** The burn that a worker leaves after each tick, which runs out within 2 ticks once it stops (CD-FUEL-14). */
    const val BURN_TICKS = 2

    /** CD-FUEL-06: with no neighbour that takes heat, the heat goes nowhere, so the worker waits. */
    fun hasDemand(neighbourTakesHeat: Boolean): Boolean = neighbourTakesHeat

    /**
     * The heater's `burnTime` and `maxBurnTime` after a worked tick: 2 each, so the flame gauge stays full. A heater that still burns
     * fuel of its own keeps its own values.
     */
    fun worked(burnTime: Int, maxBurnTime: Int): Pair<Int, Int> =
        if (burnTime < BURN_TICKS) BURN_TICKS to BURN_TICKS else burnTime to maxBurnTime
}
