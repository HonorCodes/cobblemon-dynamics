package codes.honor.cobblemondynamics.handler.xp

/**
 * One XP orb as a Soul Collector sees it: [count] units of [value] XP, [distance] blocks from the worker, and the distance of the
 * nearest player who could take it, or null when the level has none ([OrbRules.nearestPlayer]).
 */
data class OrbFacts(val id: Int, val distance: Double, val value: Int, val count: Int, val playerWithin: Double?)

/** A player of the orb's level: its distance from the orb, and whether it is a spectator. */
data class NearbyPlayer(val distance: Double, val spectator: Boolean)

/** What one batch takes from one orb: [xp] in [units] units, which leaves the orb [count] units of [value] XP. */
data class Take(val id: Int, val xp: Int, val units: Int, val count: Int, val value: Int) {
    /** An orb left with no XP goes. */
    val emptied: Boolean get() = count <= 0 || value <= 0
}

/** The Soul Collector's rules for orbs, with no game type, so the unit tests cover them (CD-SOU-05 to CD-SOU-08). */
object OrbRules {
    /** The distance to the nearest player whom an orb follows: anyone who is no spectator, as vanilla's orb picks (RES9). */
    fun nearestPlayer(players: Iterable<NearbyPlayer>): Double? = players.filterNot { it.spectator }.minOfOrNull { it.distance }

    /** CD-SOU-05: an orb that a player within [playerDistance] blocks could take stays, for that player and their Mending gear. */
    fun leftForPlayer(orb: OrbFacts, playerDistance: Double): Boolean = orb.playerWithin?.let { it < playerDistance } == true

    /**
     * CD-SOU-07, CD-SOU-08: whether a batch with [room] XP of room takes anything from [orb]: a whole unit fits, or the orb has one unit
     * left, which the batch takes in part.
     */
    fun takeable(orb: OrbFacts, room: Int, playerDistance: Double): Boolean = room > 0 &&
        orb.count > 0 &&
        orb.value > 0 &&
        !leftForPlayer(orb, playerDistance) &&
        (orb.value <= room || orb.count == 1)

    /**
     * CD-SOU-05 to CD-SOU-07: the takes of one batch of at most [units] orb units and [room] XP, nearest orb first. A unit that fits
     * goes whole. An orb with one unit left that does not fit gives the room and keeps the rest of its value. An orb with more units
     * whose unit does not fit gives nothing, and the batch goes on to the next orb. The XP of the takes is the XP that the orbs lose.
     */
    fun takes(orbs: List<OrbFacts>, units: Int, room: Int, playerDistance: Double): List<Take> {
        val takes = ArrayList<Take>()
        var unitsLeft = units
        var roomLeft = room
        for (orb in orbs.sortedWith(compareBy(OrbFacts::distance, OrbFacts::id))) {
            if (unitsLeft <= 0 || roomLeft <= 0) break
            if (!takeable(orb, roomLeft, playerDistance)) continue
            var count = orb.count
            var value = orb.value
            var xp = 0
            var used = 0
            while (unitsLeft > 0 && roomLeft > 0 && count > 0) {
                val part = when {
                    value <= roomLeft -> value.also { count-- }
                    count == 1 -> roomLeft.also { value -= roomLeft }
                    else -> break
                }
                xp += part
                roomLeft -= part
                unitsLeft--
                used++
            }
            takes += Take(orb.id, xp, used, count, value)
        }
        return takes
    }
}
