package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings

/**
 * Work Stamina of one Pokémon, as a fraction of its capacity, so a level-up keeps the percentage (CD-STA-03). Tired is a flag:
 * it is set at 0% and stays set until 100%.
 */
class Stamina(var fraction: Double = 1.0, var tired: Boolean = false) {
    val percent: Double get() = fraction * 100

    /** [ticks] of Working (CD-STA-06). At 0%, the Pokémon is Tired (CD-STA-08). */
    fun work(ticks: Long, level: Int, s: EngineSettings) {
        fraction -= ticks / StaminaMath.capacityTicks(level, s)
        if (fraction <= EPSILON) {
            fraction = 0.0
            tired = true
        }
    }

    /**
     * [ticks] outside Working, at [rule]'s rate times the factor of the bed that the Pokémon lies on, or of no bed when [bedTier]
     * is null (CD-BED-04). At 100%, Tired clears (CD-STA-10).
     */
    fun rest(ticks: Long, level: Int, rule: RecoveryRule, bedTier: Int?, s: EngineSettings) {
        fraction += ticks * StaminaMath.bedFactor(bedTier, s) / StaminaMath.fullRecoveryTicks(level, rule, s)
        if (fraction >= 1.0 - EPSILON) {
            fraction = 1.0
            tired = false
        }
    }

    override fun toString(): String = "Stamina($percent%, tired=$tired)"

    private companion object {
        /**
         * One-tick steps add rounding error, so the last tick of a drain can leave 1e-13 instead of 0. At level 100 and the config
         * maximums, one tick is still 1 / 12,000,000 of a full drain or recovery, so the snap never takes a whole tick.
         */
        const val EPSILON = 1e-9
    }
}
