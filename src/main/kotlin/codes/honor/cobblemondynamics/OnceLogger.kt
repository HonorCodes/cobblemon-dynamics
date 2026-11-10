package codes.honor.cobblemondynamics

import java.util.concurrent.ConcurrentHashMap

/**
 * Warns once per cause (CD-PERF-05). The engine, handler and effect packages log only through this, so a cause that comes up on
 * every tick still writes one line. Each instance keeps its own causes.
 */
class OnceLogger(private val sink: (String) -> Unit = { CobblemonDynamics.LOGGER.warn(it) }) {
    private val causes: MutableSet<Any> = ConcurrentHashMap.newKeySet()

    /** Logs the [message] of the first report of [cause], and builds it only then. Returns whether it logged. */
    fun warn(cause: Any, message: () -> String): Boolean {
        if (!causes.add(cause)) return false
        sink(message())
        return true
    }
}
