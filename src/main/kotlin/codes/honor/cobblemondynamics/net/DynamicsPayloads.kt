package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.client.ClientState
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.WorkModePolicy
import codes.honor.cobblemondynamics.pasture.WorkModeService
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/** Every payload of the mod. None is optional, so a client without the mod cannot join (CD-PLAT-06). Handlers run on the main thread. */
object DynamicsPayloads {
    /**
     * 2: no `work_sound` payload, because work is silent (CD-FX-09). 3: the Work Leash's synced pending source carries its dimension,
     * so a client of an older build is refused at login instead of failing to read the item.
     */
    private const val VERSION = "3"

    /** The cooldown per player of `leash_overview_request` and of `pasture_panel_open`. */
    private const val REQUEST_COOLDOWN_TICKS = 10

    private val overviewRequests = PlayerCooldown()
    private val panelOpens = PlayerCooldown()
    private val toggleReplies = PlayerCooldown()

    fun register(modBus: IEventBus) {
        modBus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar(VERSION)
            // Client handlers run only on a client, so the client classes that they call never load on a dedicated server.
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
                serverPlayer(context)?.let(::onOverviewRequest)
            }
        }
    }

    /**
     * The pasture is the one whose panel the player has open, as Cobblemon's own panel finds it. Cobblemon keeps that link only
     * while the player is closer than 10.0 blocks, so a toggle from exactly 10.0 blocks finds no pasture (CD-SEC-04). A refused or
     * ignored request sends the player the current state, so the panel shows the truth. It does so at most once per toggle cooldown,
     * so a flood of refused requests cannot rebuild the panel state on every packet.
     */
    fun onToggle(player: ServerPlayer, payload: WorkModeTogglePayload) {
        val pos = PastureAdapter.linkedPasture(player) ?: return
        val level = player.serverLevel()
        val decision = WorkModeService.request(player, pos, payload.enabled)
        if (decision == WorkModePolicy.Decision.Apply) return
        if (toggleReplies.tryAcquire(player.uuid, level.gameTime, WorkEngineSettings.current.toggleCooldownTicks)) {
            PanelViewers.send(player, level, pos)
        }
    }

    /**
     * The player views the pasture whose panel opened. An open within 10 ticks of the player's last one gets the pasture's state with
     * the 20-tick refresh instead of at once, so reopening the panel cannot rebuild the state on every packet (CD-UI-02 allows 20 ticks).
     */
    fun onPanelOpen(player: ServerPlayer) {
        val pos = PastureAdapter.linkedPasture(player) ?: return
        val level = player.serverLevel()
        PanelViewers.open(player, level, pos, sendNow = panelOpens.tryAcquire(player.uuid, level.gameTime, REQUEST_COOLDOWN_TICKS))
    }

    /** A request within 10 ticks of the player's last answered one gets no reply. The client asks every 20 ticks while it holds the leash. */
    fun onOverviewRequest(player: ServerPlayer) {
        if (!overviewRequests.tryAcquire(player.uuid, player.serverLevel().gameTime, REQUEST_COOLDOWN_TICKS)) return
        PacketDistributor.sendToPlayer(player, LeashOverviewPayload.of(player))
    }

    /** Forgets the request times of a player who logged out. */
    fun forget(playerId: UUID) {
        overviewRequests.forget(playerId)
        panelOpens.forget(playerId)
        toggleReplies.forget(playerId)
    }

    /** Forgets every request time when a server stops, because the next server runs on another clock. */
    fun reset() {
        overviewRequests.clear()
        panelOpens.clear()
        toggleReplies.clear()
    }

    private fun serverPlayer(context: IPayloadContext): ServerPlayer? = context.player() as? ServerPlayer
}
