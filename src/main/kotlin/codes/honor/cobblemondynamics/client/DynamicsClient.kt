package codes.honor.cobblemondynamics.client

import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge

/** Registers every client hook. The mod calls it only on the client dist, so no client class loads on a dedicated server. */
object DynamicsClient {
    fun register(modBus: IEventBus) {
        ClientEffects.register(modBus)
        PastureScreenHooks.register()
        HeldLeash.register()
        HeadBars.register()
        TargetOutlines.register()
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> {
            ClientState.reset()
            PastureScreenHooks.reset()
        }
    }
}
