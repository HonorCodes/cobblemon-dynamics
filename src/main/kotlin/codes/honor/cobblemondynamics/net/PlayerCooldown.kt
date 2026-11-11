package codes.honor.cobblemondynamics.net

import java.util.UUID

/**
 * A rate limit per player: after one allowed action, the player's next one waits for the cooldown. In memory and server thread only, so
 * the owner forgets a player at logout and everyone at server stop.
 */
class PlayerCooldown {
    private val last = HashMap<UUID, Long>()

    /** True, and [now] becomes the player's last action, when [player] never acted or acted [ticks] or more ticks before [now]. */
    fun tryAcquire(player: UUID, now: Long, ticks: Int): Boolean {
        val previous = last[player]
        if (previous != null && now - previous < ticks) return false
        last[player] = now
        return true
    }

    fun forget(player: UUID) {
        last.remove(player)
    }

    fun clear() {
        last.clear()
    }
}
