package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings

/** The Work Stamina model of ADR-0008, in ticks. */
object StaminaMath {
    const val TICKS_PER_MINUTE = 1_200

    private const val HEALER_MAX_LEVEL = 100

    /** Ticks of Working from 100% to 0%. The level alone sets it, so rarity never does (CD-STA-01, CD-STA-02). */
    fun capacityTicks(level: Int, s: EngineSettings): Double = s.ticksPerLevel.toDouble() * level.coerceAtLeast(1)

    /** Ticks from 0% to 100% outside Working, at the base rate. A worker recovers as fast as it drains (CD-STA-07). */
    fun fullRecoveryTicks(level: Int, rule: RecoveryRule, s: EngineSettings): Double = when (rule) {
        RecoveryRule.WORKER -> capacityTicks(level, s)
        RecoveryRule.HEALER -> healerMinutes(level, s) * TICKS_PER_MINUTE
    }

    /**
     * CD-HEAL-12: the healer line runs from level 1 to level 100. Cobblemon's level cap is a config value, so a higher level
     * recovers like level 100, and no recovery time reaches 0.
     */
    fun healerMinutes(level: Int, s: EngineSettings): Double {
        val l = level.coerceIn(1, HEALER_MAX_LEVEL)
        return s.healerMinutesAtLevel1 - (s.healerMinutesAtLevel1 - s.healerMinutesAtLevel100) * (l - 1) / (HEALER_MAX_LEVEL - 1.0)
    }

    /** A bed of tier 1 to 4 divides the recovery time by its configured factor. Off a bed, the factor is 1 (CD-BED-03). */
    fun bedFactor(tier: Int?, s: EngineSettings): Int = if (tier == null) 1 else s.bedFactors[tier - 1]
}
