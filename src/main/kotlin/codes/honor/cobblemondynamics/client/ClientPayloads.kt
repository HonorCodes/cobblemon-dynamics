package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.DynamicsPayloads
import codes.honor.cobblemondynamics.net.LeashOverviewPayload
import codes.honor.cobblemondynamics.net.PasturePanelStatePayload

/** The client's handlers of the server's payloads. The payloads themselves register in common code, in [DynamicsPayloads]. */
object ClientPayloads : DynamicsPayloads.ClientHandlers {
    fun register() {
        DynamicsPayloads.clientHandlers = this
    }

    override fun onPanelState(payload: PasturePanelStatePayload) = ClientState.onPanelState(payload)

    override fun onLeashOverview(payload: LeashOverviewPayload) = ClientState.onLeashOverview(payload)
}
