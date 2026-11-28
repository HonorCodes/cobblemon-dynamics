package codes.honor.cobblemondynamics.handler.vanilla

import com.google.gson.JsonObject
import kotlin.math.roundToInt

/** What the Crystal Tender reads of the block on one face of its target. [BuddingHandler] fills it from a level. */
data class BudFacts(
    /** A face where the target can start a bud ([BuddingRules.isOpen]): air or a water source, or air alone for the budding citrine. */
    val open: Boolean = false,
    /** In `#c:buds` or `#cobblemon_dynamics:crystal/buds`. */
    val bud: Boolean = false,
    /** In `#c:clusters`. */
    val cluster: Boolean = false,
    /** Its `facing` points away from the target, or it has none: a bud that the target grew. */
    val facesAway: Boolean = true,
    /** The value of its integer property `stage` or `age`, with that property's highest value, or null with neither. */
    val growth: Int? = null,
    val growthMax: Int = 0,
)

/** What the Crystal Tender reads of its target itself, a block of `#cobblemon_dynamics:crystal_tender_targets`. */
data class CrystalFacts(
    /** In `#cobblemon_dynamics:crystal/self_growing`: a bud that grows into its next stage in place, as Cobblemon's tumblestones do. */
    val selfGrowing: Boolean = false,
    /** A block of `#cobblemon:tumblestone_heat_source` in the 3 by 3 by 3 around it. */
    val heatSourceNear: Boolean = false,
    /** A stage follows its own: Cobblemon's `GrowableStoneBlock.nextStage` is not null, or the block is no such block. */
    val hasNextStage: Boolean = true,
    /** In `#cobblemon_dynamics:crystal/scheduled_tick`: a block that grows in its scheduled tick, such as Unusual End's budding citrine. */
    val scheduledTick: Boolean = false,
)

/** The extra tick that the Crystal Tender gives its target: a random tick, its scheduled tick, or none on a failed roll. */
enum class ExtraTick { RANDOM, SCHEDULED, NONE }

/** The pure rules of the Crystal Tender's demand and extra tick. */
object BuddingRules {
    /** CD-MCH-03, R88: a claim reads the 27 blocks around a tumblestone for a heat source at most once in this many ticks. */
    const val HEAT_CHECK_TICKS = 20

    /** The default Crystal Tender extra-tick interval (CD-CRY-02). */
    const val DEFAULT_INTERVAL_TICKS = 10
    private const val INTERVAL_TICKS = "interval_ticks"

    /** The budding amethyst's chance: its random tick grows a face on 1 roll in 5. */
    const val DEFAULT_SCHEDULED_TICK_ONE_IN = 5
    private const val SCHEDULED_TICK_ONE_IN = "scheduled_tick_one_in"

    fun intervalOf(params: JsonObject, multiplier: Double = 1.0): Int {
        val base = params.get(INTERVAL_TICKS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: DEFAULT_INTERVAL_TICKS
        return if (multiplier <= 0.0 || multiplier == 1.0) base else maxOf(1, (base.coerceAtLeast(1) / multiplier).roundToInt())
    }

    /**
     * CD-CRY-03, CD-FIX-02: the face can still grow while it is open or holds a bud that the target grew. Cobblemon puts every stage
     * of its type gem clusters in both `#c:buds` and `#c:clusters`, so a bud of `#c:clusters` grows only below the highest value of its
     * `stage` or `age`.
     */
    fun canGrow(f: BudFacts): Boolean = when {
        f.open -> true
        !f.bud || !f.facesAway -> false
        f.cluster -> f.growth != null && f.growth < f.growthMax
        else -> true
    }

    /** CD-MCH-04: the buds of `#cobblemon_dynamics:crystal/buds`, which no `c` tag holds, count as buds of `#c:buds`. */
    fun isBud(inBudsTag: Boolean, inCrystalBuds: Boolean): Boolean = inBudsTag || inCrystalBuds

    /**
     * CD-CRY-03, CD-MCH-04: a face where the target can start a bud. Vanilla's rule takes air or a water source ([clusterCanGrow]), and a
     * block of the scheduled tick tag takes [air] alone, because Unusual End's citrine grows only into air (R88).
     */
    fun isOpen(air: Boolean, clusterCanGrow: Boolean, scheduledTick: Boolean): Boolean = if (scheduledTick) air else clusterCanGrow

    /**
     * CD-CRY-03, CD-MCH-03: a self-growing bud has demand only while Cobblemon's own rule lets it grow, a heat source near and a next
     * stage. Every other target, the budding blocks, the budding citrine and Quark's corundum, has demand while one of its [faces] can
     * still grow.
     */
    fun hasDemand(target: CrystalFacts, faces: () -> List<BudFacts>): Boolean =
        if (target.selfGrowing) target.heatSourceNear && target.hasNextStage else faces().any(::canGrow)

    /**
     * CD-MCH-04: a block of the scheduled tick tag grows only in its scheduled tick, so it gets that tick in place of a random one. That
     * tick grows a face with no roll of its own, so it comes only on a [roll] of 0, of a roll in 0 until `scheduled_tick_one_in`, and the
     * block grows at the pace of budding amethyst (R88). Every other target gets a random tick, whose own rules roll.
     */
    fun extraTick(target: CrystalFacts, roll: Int): ExtraTick = when {
        !target.scheduledTick -> ExtraTick.RANDOM
        roll == 0 -> ExtraTick.SCHEDULED
        else -> ExtraTick.NONE
    }

    /** CD-MCH-04: the role file's `scheduled_tick_one_in`, 5 when absent and at least 1. */
    fun scheduledTickOneIn(params: JsonObject): Int {
        val value = params.get(SCHEDULED_TICK_ONE_IN)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        return (value ?: DEFAULT_SCHEDULED_TICK_ONE_IN).coerceAtLeast(1)
    }

    /** CD-MCH-03, R88: whether a claim reads a tumblestone's heat source again, last read at [checkedAt] or never. */
    fun heatCheckDue(checkedAt: Long?, now: Long): Boolean = checkedAt == null || now - checkedAt !in 0L until HEAT_CHECK_TICKS
}
