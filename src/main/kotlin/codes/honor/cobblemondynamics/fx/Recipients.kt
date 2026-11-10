package codes.honor.cobblemondynamics.fx

object Recipients {
    /** A player at exactly [radius] blocks from the worker receives its effects, and a player farther away does not (CD-FX-08). */
    fun inRange(distanceSqr: Double, radius: Int): Boolean = distanceSqr <= radius.toDouble() * radius
}
