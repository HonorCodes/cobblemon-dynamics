package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings

/** The Work Stamina model of ADR-0008, in ticks. */
object StaminaMath {
    const val TICKS_PER_MINUTE = 1_200
    const val NOCTURNAL_DRAIN_FACTOR = 0.5

    private const val HEALER_MAX_LEVEL = 100

    const val ALPHA_CAPACITY_MULTIPLIER = 1.25
    const val MAJESTIC_DRAIN_MULTIPLIER = 0.85

    /** Ticks of Working from 100% to 0%. Level sets the base, Alpha adds 25%, and rarity never changes it (CD-STA-01, CD-STA-02, CD-PRG-03). */
    fun capacityTicks(level: Int, s: EngineSettings, isAlpha: Boolean = false): Double =
        s.ticksPerLevel.toDouble() * level.coerceAtLeast(1) * if (isAlpha) ALPHA_CAPACITY_MULTIPLIER else 1.0

    /** Drain multiplier per Working tick (CD-PRG-04, CD-PRG-10, CD-SNT-05). */
    fun drainScale(
        isMajestic: Boolean = false,
        roleMultiplier: Double = 1.0,
        machineCapped: Boolean = false,
        hasDarkType: Boolean = false,
        isNight: Boolean = false,
    ): Double {
        val majestic = if (isMajestic) MAJESTIC_DRAIN_MULTIPLIER else 1.0
        val nocturnal = nocturnalDrainFactor(hasDarkType, isNight)
        val divisor = if (machineCapped && roleMultiplier > 0.0) roleMultiplier else 1.0
        return majestic * nocturnal / divisor
    }

    /** Stamina fraction spent per completed heal (CD-HEAL-04, CD-PRG-10). */
    fun healCost(s: EngineSettings, multiplier: Double = 1.0, isMajestic: Boolean = false): Double =
        s.healCost * drainScale(isMajestic = isMajestic, roleMultiplier = multiplier, machineCapped = true)

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

    fun nocturnalDrainFactor(hasDarkType: Boolean, isNight: Boolean): Double = if (hasDarkType && isNight) NOCTURNAL_DRAIN_FACTOR else 1.0
}
