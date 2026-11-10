package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.LeashOverviewPayload
import codes.honor.cobblemondynamics.net.PasturePanelStatePayload
import net.minecraft.core.BlockPos

/** The last state that the server sent this client. The client draws it (M5). Client thread only. */
object ClientState {
    var panel: PasturePanelStatePayload? = null
        private set

    var leashOverview: List<Pair<Int, BlockPos>> = emptyList()
        private set

    fun onPanelState(payload: PasturePanelStatePayload) {
        panel = payload
    }

    fun onLeashOverview(payload: LeashOverviewPayload) {
        leashOverview = payload.entries
    }
}
