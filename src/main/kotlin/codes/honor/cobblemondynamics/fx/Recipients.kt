package codes.honor.cobblemondynamics.fx

object Recipients {
    /** A player at exactly [radius] blocks from the worker receives its effects, and a player farther away does not (CD-FX-08). */
    fun inRange(distanceSqr: Double, radius: Int): Boolean = distanceSqr <= radius.toDouble() * radius

    /**
     * The players that get a packet naming an entity: those of [watchers], the players that track the entity, within [radius] blocks
     * of it. A player who does not track the entity has no entity of that id on its client, so it never gets the packet.
     */
    fun <P> of(watchers: Collection<P>, radius: Int, distanceSqr: (P) -> Double): List<P> =
        watchers.filter { inRange(distanceSqr(it), radius) }
}
