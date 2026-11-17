package codes.honor.cobblemondynamics.compat.createaddition

/** The pure rule of the Superheater's heat on the Liquid Blaze Burner of Create Crafts & Additions 1.7.2 (CD-MCH-01). */
object LiquidBurnerRule {
    private const val SEETHING = "seething"

    /**
     * Whether the burner shows a worker's Seething heat that neither a grant nor its own fuel gives any more, by Create's heat names. A
     * fueled burner writes its block state only when its fuel changes, so a worker's heat that outlived its grant, through a handler error
     * or a restart, would otherwise stay until then. Its own fuel gives Seething only with a superheating fuel. A lower heat is left alone:
     * the burner's own state can lag its fuel there, and a burner with no fuel writes its state on each of its own ticks.
     */
    fun stale(granted: Boolean, shown: String, own: String): Boolean = !granted && shown == SEETHING && own != SEETHING
}
