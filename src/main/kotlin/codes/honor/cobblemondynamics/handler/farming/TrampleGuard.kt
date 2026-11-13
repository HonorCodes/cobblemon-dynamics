package codes.honor.cobblemondynamics.handler.farming

/** CD-CRP-15: a worker that the work engine directs never tramples farmland. Wild Cobblemon and players keep the vanilla rule. */
object TrampleGuard {
    /** Whether a trample by an entity is canceled. [directed] asks the work engine, and runs only for a [cobblemon]. */
    fun cancels(cobblemon: Boolean, directed: () -> Boolean): Boolean = cobblemon && directed()
}
