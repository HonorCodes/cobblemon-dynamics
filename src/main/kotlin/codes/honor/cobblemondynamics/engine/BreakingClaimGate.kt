package codes.honor.cobblemondynamics.engine

/** Pre-claim break gate for roles whose work breaks or replaces their claimed block cell (CD-TEAM-08). */
object BreakingClaimGate {
    fun mayClaim(breaksBlocks: Boolean, mayUse: Boolean, mayBreak: () -> Boolean): Boolean = mayUse && (!breaksBlocks || mayBreak())
}
