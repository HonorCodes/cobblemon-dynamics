package codes.honor.cobblemondynamics.compat

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.concurrent.CopyOnWriteArrayList

/** What the work engine tells the compat shims about its workers. The engine calls it on the server thread. */
object WorkerEvents {
    interface Listener {
        /** At each 20-tick poll, for every worker of a Work Mode pasture. */
        fun onPolled(pokemon: Pokemon)

        /** When a worker leaves: it left its pasture, Work Mode turned off, or the pasture is gone. */
        fun onLeft(pokemon: Pokemon)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun add(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun polled(pokemon: Pokemon) {
        for (listener in listeners) listener.onPolled(pokemon)
    }

    fun left(pokemon: Pokemon) {
        for (listener in listeners) listener.onLeft(pokemon)
    }
}
