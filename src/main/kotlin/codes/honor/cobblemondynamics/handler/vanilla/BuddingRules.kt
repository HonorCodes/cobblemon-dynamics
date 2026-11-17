package codes.honor.cobblemondynamics.handler.vanilla

/** What the Crystal Tender reads of the block on one face of its target. [BuddingHandler] fills it from a level. */
data class BudFacts(
    /** Air or a water source, where vanilla's and AE2's budding blocks start a bud. */
    val open: Boolean = false,
    /** In `#c:buds`. */
    val bud: Boolean = false,
    /** In `#c:clusters`. */
    val cluster: Boolean = false,
    /** Its `facing` points away from the target, or it has none: a bud that the target grew. */
    val facesAway: Boolean = true,
    /** The value of its integer property `stage` or `age`, with that property's highest value, or null with neither. */
    val growth: Int? = null,
    val growthMax: Int = 0,
)

/** The pure rule of the Crystal Tender's demand on one face of its target. */
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
}
