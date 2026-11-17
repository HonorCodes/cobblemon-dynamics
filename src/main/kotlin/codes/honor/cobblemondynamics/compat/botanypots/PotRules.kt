package codes.honor.cobblemondynamics.compat.botanypots

import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler

/**
 * What the pot roles read of a Botany Pot, as [PotAdapter] reads it: its pot type (`BASIC`, `HOPPER` or `WAXED`), whether its bone
 * meal cooldown has run out, whether a player could harvest it, its growth ticks and the ticks that its crop needs (−1 with no crop).
 */
data class PotFacts(val type: String, val canBonemeal: Boolean, val canHarvest: Boolean, val growth: Float, val required: Int)

/**
 * The pot roles' rules over plain facts, so that unit tests load them with no Botany Pots class. The numbers are those of Botany Pots'
 * own bone meal, its fertilizer `botanypots:minecraft/fertilizer/bone_meal`: a random 400 to 600 ticks of growth, never past the
 * required ticks − 20, then a cooldown of 20 ticks (RES10).
 */
object PotRules {
    const val BASIC = "BASIC"
    const val HOPPER = "HOPPER"
    const val WAXED = "WAXED"

    /** CD-FRM-06: the pot roles claim in the lanes of the farming roles, so a pot takes one Cultivator and one Harvester. */
    const val CULTIVATOR_LANE = CultivatorHandler.LANE
    const val HARVESTER_LANE = HarvesterHandler.LANE

    const val MIN_GROWTH = 400
    const val MAX_GROWTH = 600

    /** The last ticks of growth, which Botany Pots' bone meal leaves to the pot's own growth. */
    const val NATURAL_TICKS = 20
    const val BONEMEAL_COOLDOWN = 20

    /**
     * CD-FRM-04: a pot whose bone meal cooldown has run out and whose growth is below the required ticks − 20. A waxed pot never
     * grows, and a pot with no crop needs no growth.
     */
    fun cultivatorDemand(pot: PotFacts): Boolean =
        pot.type != WAXED && pot.canBonemeal && pot.required > NATURAL_TICKS && pot.growth < pot.required - NATURAL_TICKS

    /**
     * CD-FRM-04, CD-FRM-07: the growth after one use that rolled [roll] ticks, or null when the pot has no demand or the use question
     * of [mayUse] refuses. The question is asked only for a use that would happen.
     */
    fun grown(pot: PotFacts, roll: Int, mayUse: () -> Boolean): Float? {
        if (!cultivatorDemand(pot) || !mayUse()) return null
        return minOf(pot.growth + roll.coerceIn(MIN_GROWTH, MAX_GROWTH), (pot.required - NATURAL_TICKS).toFloat())
    }

    /** One roll of 400 to 600 ticks, from a source of random ints below a bound, such as `RandomSource.nextInt`. */
    fun roll(nextInt: (Int) -> Int): Int = MIN_GROWTH + nextInt(MAX_GROWTH - MIN_GROWTH + 1)

    /** CD-FRM-05: a basic pot that a player could harvest. A hopper pot harvests itself, and a waxed pot never grows. */
    fun harvesterDemand(pot: PotFacts): Boolean = pot.type == BASIC && pot.canHarvest

    /** CD-FRM-05, CD-FRM-07: whether a harvest happens. The use question of [mayUse] is asked only for a pot with demand. */
    fun harvests(pot: PotFacts, mayUse: () -> Boolean): Boolean = harvesterDemand(pot) && mayUse()
}
