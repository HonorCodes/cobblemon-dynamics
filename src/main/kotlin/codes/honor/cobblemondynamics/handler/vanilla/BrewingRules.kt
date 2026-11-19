package codes.honor.cobblemondynamics.handler.vanilla

/** What the Brew Stoker and the Brewer write to a brewing stand's `fuel` and `brewTime`, apart from the stand itself. */
object BrewingRules {
    /** Vanilla sets `brewTime` to this when a brew starts, and the stand counts it down by 1 per tick. */
    const val VANILLA_BREW_TICKS = 400

    /**
     * CD-BRF-02, CD-BRF-03: at least 1 fuel while an ingredient can brew. Vanilla reloads from the blaze powder only at 0 fuel, and the
     * unit that a starting brew spends comes back on the engine's next tick, so the powder stays. The stand's own fuel stays as it is.
     */
    fun fuelWhileWorking(fuel: Int, canBrew: Boolean): Int = if (canBrew && fuel < 1) 1 else fuel

    /**
     * CD-BRF-04, decision 20 of the 1.2.1 plan: a brew waits to start, which only fuel does: the stand can brew, no brew runs, and its
     * fuel is below 1. A running brew needs no fuel, so the Brew Stoker waits in Standby through it, and a Poison/Fire worker speeds it
     * as the Brewer (CD-ASG-21).
     */
    fun stokerDemand(canBrew: Boolean, brewTime: Int, fuel: Int): Boolean = canBrew && brewTime == 0 && fuel < 1

    /** CD-BRF-05: a Brew Stoker that stops leaves the stand no more fuel than it held when the work started. */
    fun fuelAfterStop(fuel: Int, atStart: Int): Int = minOf(fuel, atStart)

    /**
     * CD-BRW-02: one extra step per tick and never below 1, so the stand's own step ends the brew. A brew that a Brewer works from its
     * start ends 200 ticks after it.
     */
    fun brewTimeWhileWorking(brewTime: Int): Int = if (brewTime > 1) brewTime - 1 else brewTime
}
