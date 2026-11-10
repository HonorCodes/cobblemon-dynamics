package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.client.ClientEffects
import codes.honor.cobblemondynamics.client.ClientState
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.WorkModePolicy
import codes.honor.cobblemondynamics.pasture.WorkModeService
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Every payload of the mod. None is optional, so a client without the mod cannot join (CD-PLAT-06). Handlers run on the main thread. */
object DynamicsPayloads {
    private const val VERSION = "1"

    fun register(modBus: IEventBus) {
        modBus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar(VERSION)
            // Client handlers run only on a client, so the client classes that they call never load on a dedicated server.
            registrar.playToClient(WorkSoundPayload.TYPE, WorkSoundPayload.STREAM_CODEC) { payload, _ ->
                ClientEffects.playWorkSound(payload)
            }
            registrar.playToClient(PasturePanelStatePayload.TYPE, PasturePanelStatePayload.STREAM_CODEC) { payload, _ ->
                ClientState.onPanelState(payload)
            }
            registrar.playToClient(LeashOverviewPayload.TYPE, LeashOverviewPayload.STREAM_CODEC) { payload, _ ->
                ClientState.onLeashOverview(payload)
            }
            registrar.playToServer(WorkModeTogglePayload.TYPE, WorkModeTogglePayload.STREAM_CODEC) { payload, context ->
                serverPlayer(context)?.let { onToggle(it, payload) }
            }
            registrar.playToServer(PasturePanelOpenPayload.TYPE, PasturePanelOpenPayload.STREAM_CODEC) { _, context ->
                serverPlayer(context)?.let(::onPanelOpen)
            }
            registrar.playToServer(PasturePanelClosePayload.TYPE, PasturePanelClosePayload.STREAM_CODEC) { _, context ->
                serverPlayer(context)?.let(PanelViewers::close)
            }
            registrar.playToServer(LeashOverviewRequestPayload.TYPE, LeashOverviewRequestPayload.STREAM_CODEC) { _, context ->
                serverPlayer(context)?.let { PacketDistributor.sendToPlayer(it, LeashOverviewPayload.of(it)) }
            }
        }
    }

    /**
     * The pasture is the one whose panel the player has open, as Cobblemon's own panel finds it. Cobblemon keeps that link only
     * while the player is closer than 10.0 blocks, so a toggle from exactly 10.0 blocks finds no pasture (CD-SEC-04). A refused or
     * ignored request sends the player the current state, so the panel shows the truth.
     */
    fun onToggle(player: ServerPlayer, payload: WorkModeTogglePayload) {
        val pos = PastureAdapter.linkedPasture(player) ?: return
        val decision = WorkModeService.request(player, pos, payload.enabled)
        if (decision != WorkModePolicy.Decision.Apply) PanelViewers.send(player, player.serverLevel(), pos)
    }

    fun onPanelOpen(player: ServerPlayer) {
        val pos = PastureAdapter.linkedPasture(player) ?: return
        PanelViewers.open(player, player.serverLevel(), pos)
    }

    private fun serverPlayer(context: IPayloadContext): ServerPlayer? = context.player() as? ServerPlayer
}
