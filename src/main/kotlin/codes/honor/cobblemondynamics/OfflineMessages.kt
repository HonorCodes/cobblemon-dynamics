package codes.honor.cobblemondynamics

import java.util.UUID

/**
 * Messages for players who are offline, until their next login (CD-LSH-13). Each player keeps the [limit] newest. In memory only, so a
 * server restart drops them. Server thread only.
 */
class OfflineMessages<T>(private val limit: Int = LIMIT) {
    private val queues = HashMap<UUID, ArrayDeque<T>>()

    /** Queues [message] for [playerId]. A full queue drops that player's oldest message first. */
    fun add(playerId: UUID, message: T) {
        val queue = queues.getOrPut(playerId) { ArrayDeque() }
        if (queue.size >= limit) queue.removeFirst()
        queue.addLast(message)
    }

    /** The messages for [playerId], oldest first. The queue then forgets them. */
    fun take(playerId: UUID): List<T> = queues.remove(playerId)?.toList().orEmpty()

    fun clear() {
        queues.clear()
    }

    companion object {
        const val LIMIT = 10
    }
}
