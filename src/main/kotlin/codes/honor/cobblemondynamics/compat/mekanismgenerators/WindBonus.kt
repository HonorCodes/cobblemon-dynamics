package codes.honor.cobblemondynamics.compat.mekanismgenerators

import net.minecraft.core.BlockPos

/**
 * Tailwind's numbers on the Mekanism Wind Generator. Mekanism's private `getMultiplier()` gives m, which is 0 unless the cell 4
 * above the generator holds no fluid and sees the sky, and the generator makes `windGenerationMin` × m J per tick. A worker of level
 * L turns a nonzero m into m + (M − m) × L ÷ 100, with M = `windGenerationMax` ÷ `windGenerationMin`, so level 100 makes the config's
 * maximum, no level makes more, and the sky rule holds (CD-AIR-02, CD-BAL-14). No Mekanism class, so unit tests load it.
 */
object WindBonus {
    /** M, or 0 for a config whose lowest rate is 0, where Mekanism's own formula divides by 0. */
    fun top(min: Long, max: Long): Double = if (min > 0) max.toDouble() / min else 0.0

    fun multiplier(own: Double, top: Double, level: Int): Double =
        if (own <= 0.0 || top <= own) own else own + (top - own) * level.coerceIn(0, 100) / 100.0

    /** A grant holds for this tick and the next. The handler renews it on every Working tick, so a missed stop lapses within 2 ticks. */
    fun until(now: Long): Long = now + 1

    /**
     * CD-AIR-03: the multiplier that Mekanism stores at its update at [now], every 20 ticks. A grant that its worker renewed within 2
     * ticks boosts it, and the first update after that gives Mekanism's own m again.
     */
    fun applied(own: Double, top: Double, grant: WindGrant?, now: Long): Double =
        if (grant != null && now <= grant.until) multiplier(own, top, grant.level) else own

    /** CD-AIR-04: a bounding block of the generator keys the generator's main block. A block with no main keys itself. */
    fun canonical(pos: BlockPos, main: BlockPos?): BlockPos = main ?: pos
}

/** The level of the Tailwind worker of one Wind Generator, and the last tick that the grant holds. On the server only, never saved. */
class WindGrant(var level: Int = 0, var until: Long = Long.MIN_VALUE)
