package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.registry.DynamicsMenus
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge

/** Registers every client hook. The mod calls it only on the client dist, so no client class loads on a dedicated server. */
object DynamicsClient {
    fun register(modBus: IEventBus) {
        ClientPayloads.register()
        ClientEffects.register(modBus)
        PastureScreenHooks.register()
        HeldLeash.register()
        HeadBars.register()
        TargetOutlines.register()
        WorkAreaOverlay.register()
        // CD-XPC-04: the Experience Collector's screen.
        modBus.addListener<RegisterMenuScreensEvent> { it.register(DynamicsMenus.EXPERIENCE_COLLECTOR.get(), ::ExperienceCollectorScreen) }
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> {
            ClientState.reset()
            PastureScreenHooks.reset()
            WorkAreaOverlay.reset()
        }
    }
}
