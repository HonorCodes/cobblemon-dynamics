package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Switches Cobbleworkers off for the workers of Work Mode pastures through its rest flag (CD-CMP-01, CD-CMP-02). The mod
 * registers it only when Cobbleworkers is loaded (CD-CMP-06). With `compat.cobbleworkers` off, each poll clears the flags that
 * Dynamics set, so switching the shim off strands none.
 */
object CobbleworkersCompat : WorkerEvents.Listener {
    fun register() {
        WorkerEvents.add(this)
    }

    override fun onPolled(pokemon: Pokemon) {
        val data = pokemon.persistentData
        val changed = if (WorkEngineSettings.current.compatCobbleworkers) CobbleworkersRest.mark(data) else CobbleworkersRest.unmark(data)
        if (changed) pokemon.onChange()
    }

    override fun onLeft(pokemon: Pokemon) {
        if (CobbleworkersRest.unmark(pokemon.persistentData)) pokemon.onChange()
    }
}
