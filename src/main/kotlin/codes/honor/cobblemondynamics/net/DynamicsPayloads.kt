package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.client.ClientEffects
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/** Every payload of the mod. None is optional, so a client without the mod cannot join (CD-PLAT-06). */
object DynamicsPayloads {
    private const val VERSION = "1"

    fun register(modBus: IEventBus) {
        modBus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar(VERSION)
            // Client handlers run only on a client, so the client classes that they call never load on a dedicated server.
            registrar.playToClient(WorkSoundPayload.TYPE, WorkSoundPayload.STREAM_CODEC) { payload, _ ->
                ClientEffects.playWorkSound(payload)
            }
        }
    }
}
