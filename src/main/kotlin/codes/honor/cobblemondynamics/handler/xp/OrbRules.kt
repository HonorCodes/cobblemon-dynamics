package codes.honor.cobblemondynamics.handler.xp

/**
 * One XP orb as a Soul Collector sees it: [count] units of [value] XP, [distanceSqr] the square of its distance from the worker, and
 * [playerWithinSqr] the square of the distance of the nearest player who could take it, or null when none is near
 * ([OrbRules.nearestPlayer]). The handler reads squares with `distanceToSqr`, so no check takes a root.
 */
data class OrbFacts(val id: Int, val distanceSqr: Double, val value: Int, val count: Int, val playerWithinSqr: Double?)

/** A player near an orb: the square of its distance from the orb, and whether it is a spectator. */
data class NearbyPlayer(val distanceSqr: Double, val spectator: Boolean)

/** What one batch takes from one orb: [xp] in [units] units, which leaves the orb [count] units of [value] XP. */
data class Take(val id: Int, val xp: Int, val units: Int, val count: Int, val value: Int) {
    /** An orb left with no XP goes. */
    val emptied: Boolean get() = count <= 0 || value <= 0
}

/** The Soul Collector's rules for orbs, with no game type, so the unit tests cover them (CD-SOU-05 to CD-SOU-08, CD-SOU-12). */
object OrbRules {
    /**
     * The square of the distance to the nearest player whom an orb follows: anyone who is no spectator, as vanilla's orb picks (RES9).
     * [deathSpots] holds the square of the orb's distance from each spot where a player died in the last 6,000 ticks, and each counts as
     * such a player, so the orbs of a death wait for their player (CD-SOU-12).
     */
    fun nearestPlayer(players: Iterable<NearbyPlayer>, deathSpots: Iterable<Double>): Double? {
        var nearest: Double? = null
        for (player in players) if (!player.spectator) nearest = minOf(nearest ?: player.distanceSqr, player.distanceSqr)
        for (spot in deathSpots) nearest = minOf(nearest ?: spot, spot)
        return nearest
    }

    /** CD-SOU-05: an orb that a player within [playerDistance] blocks could take stays, for that player and their Mending gear. */
    fun leftForPlayer(orb: OrbFacts, playerDistance: Double): Boolean =
        orb.playerWithinSqr?.let { it < playerDistance * playerDistance } == true

    /**
     * CD-SOU-07, CD-SOU-08: whether a batch with [room] XP of room takes anything from [orb]: a whole unit fits, or the orb has one unit
     * left, which the batch takes in part.
     */
    fun takeable(orb: OrbFacts, room: Int, playerDistance: Double): Boolean = room > 0 &&
        orb.count > 0 &&
        orb.value > 0 &&
        !leftForPlayer(orb, playerDistance) &&
        (orb.value <= room || orb.count == 1)

    /** CD-SOU-06: the orbs nearest the worker first, and of two as near the lower id first. A query sorts its orbs once with this. */
    fun nearestFirst(orbs: List<OrbFacts>): List<OrbFacts> = orbs.sortedWith(NEAREST_FIRST)

    /**
     * CD-SOU-05 to CD-SOU-07: the takes of one batch of at most [units] orb units and [room] XP, in the order of [orbs], which
     * [nearestFirst] sets. An orb with more units whose unit does not fit gives nothing, and the batch goes on to the next orb. The XP
     * of the takes is the XP that the orbs lose.
     */
    fun takes(orbs: List<OrbFacts>, units: Int, room: Int, playerDistance: Double): List<Take> {
        val takes = ArrayList<Take>()
        var unitsLeft = units
        var roomLeft = room
        for (orb in orbs) {
            if (unitsLeft <= 0 || roomLeft <= 0) break
            if (!takeable(orb, roomLeft, playerDistance)) continue
            val take = take(orb, unitsLeft, roomLeft)
            takes += take
            unitsLeft -= take.units
            roomLeft -= take.xp
        }
        return takes
    }

    /**
     * CD-SOU-07: what at most [units] units and [room] XP take from [orb]. A unit that fits goes whole, and a last unit alone gives the
     * room and keeps the rest of its value. A part of a unit cannot leave an orb with more units, so the take ends there. With [room]
     * the XP that the collector took, this cuts a take, so the orb loses only that XP or less.
     */
    fun take(orb: OrbFacts, units: Int, room: Int): Take {
        var count = orb.count
        var value = orb.value
        var roomLeft = room
        var xp = 0
        var used = 0
        while (used < units && roomLeft > 0 && count > 0) {
            val part = when {
                value <= roomLeft -> value.also { count-- }
                count == 1 -> roomLeft.also { value -= roomLeft }
                else -> break
            }
            xp += part
            roomLeft -= part
            used++
        }
        return Take(orb.id, xp, used, count, value)
    }

    private val NEAREST_FIRST: Comparator<OrbFacts> = compareBy(OrbFacts::distanceSqr, OrbFacts::id)
}
