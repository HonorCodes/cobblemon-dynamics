package codes.honor.cobblemondynamics.handler.vanilla

/** What the Crystal Tender reads of the block on one face of its target. [BuddingHandler] fills it from a level. */
data class BudFacts(
    /** Air or a water source, where vanilla's and AE2's budding blocks start a bud. */
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

/** The extra tick that the Crystal Tender gives its target. */
enum class ExtraTick { RANDOM, SCHEDULED }

/** The pure rules of the Crystal Tender's demand and extra tick. */
object BuddingRules {
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
     * CD-CRY-03, CD-MCH-03: a self-growing bud has demand only while Cobblemon's own rule lets it grow, a heat source near and a next
     * stage. Every other target, the budding blocks, the budding citrine and Quark's corundum, has demand while one of its [faces] can
     * still grow.
     */
    fun hasDemand(target: CrystalFacts, faces: () -> List<BudFacts>): Boolean =
        if (target.selfGrowing) target.heatSourceNear && target.hasNextStage else faces().any(::canGrow)

    /** CD-MCH-04: a block of the scheduled tick tag grows only in its scheduled tick, so it gets that tick in place of a random one. */
    fun extraTick(target: CrystalFacts): ExtraTick = if (target.scheduledTick) ExtraTick.SCHEDULED else ExtraTick.RANDOM
}
