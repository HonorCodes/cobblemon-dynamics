package codes.honor.cobblemondynamics.xp

/** Vanilla's XP curve, as `Player.getXpNeededForNextLevel` gives it, with no game type, so the screen and the unit tests share it. */
object XpLevels {
    /** The XP from [level] to the next level. */
    fun neededForNext(level: Int): Int = when {
        level >= 30 -> 112 + (level - 30) * 9
        level >= 15 -> 37 + (level - 15) * 5
        else -> 7 + level * 2
    }

    /** CD-XPC-04: the level that a player at level 0 with no progress reaches with [points] XP. */
    fun levelFor(points: Int): Int {
        var level = 0
        var left = points.toLong()
        while (left >= neededForNext(level)) {
            left -= neededForNext(level)
            level++
        }
        return level
    }
}
