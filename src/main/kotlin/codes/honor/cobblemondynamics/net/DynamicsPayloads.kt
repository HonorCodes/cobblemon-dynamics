package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.LoadLines
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.pasture.AreaWatchers
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
     * so a client of an older build is refused at login instead of failing to read the item. 4: Pasture Shelf settings, persistent
     * work-area watch/bounds payloads, and synced idle reasons and trait flags.
     */
    private const val VERSION = "4"

    /** The cooldown per player of `leash_overview_request` and of `pasture_panel_open`. */
    private const val REQUEST_COOLDOWN_TICKS = 10

    private val overviewRequests = PlayerCooldown()
    private val panelOpens = PlayerCooldown()
    private val toggleReplies = PlayerCooldown()

    /** What a client does with the server's payloads. */
    interface ClientHandlers {
        fun onPanelState(payload: PasturePanelStatePayload)

        fun onLeashOverview(payload: LeashOverviewPayload)

        fun onAreaBounds(payload: AreaBoundsPayload)
    }

    /**
     * The client's handlers of the server-to-client payloads. The client class `ClientPayloads` installs them from `DynamicsClient`,
     * so no common class names a client class. A dedicated server never receives these payloads and keeps none.
     */
    @Volatile
    var clientHandlers: ClientHandlers? = null

    fun register(modBus: IEventBus) {
        modBus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar(VERSION)
            registrar.playToClient(PasturePanelStatePayload.TYPE, PasturePanelStatePayload.STREAM_CODEC) { payload, _ ->
                clientHandlers?.onPanelState(payload)
            }
            registrar.playToClient(LeashOverviewPayload.TYPE, LeashOverviewPayload.STREAM_CODEC) { payload, _ ->
                clientHandlers?.onLeashOverview(payload)
            }
            registrar.playToClient(AreaBoundsPayload.TYPE, AreaBoundsPayload.STREAM_CODEC) { payload, _ ->
                clientHandlers?.onAreaBounds(payload)
            }
            registrar.playToServer(WorkModeTogglePayload.TYPE, WorkModeTogglePayload.STREAM_CODEC) { payload, context ->
                serverPlayer(context)?.let { onToggle(it, payload) }
            }
            registrar.playToServer(PastureSettingsPayload.TYPE, PastureSettingsPayload.STREAM_CODEC) { payload, context ->
                serverPlayer(context)?.let { onSettings(it, payload) }
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
            registrar.playToServer(AreaWatchPayload.TYPE, AreaWatchPayload.STREAM_CODEC) { payload, context ->
                serverPlayer(context)?.let { onAreaWatch(it, payload) }
            }
            val toClient = listOf(PasturePanelStatePayload.TYPE, LeashOverviewPayload.TYPE, AreaBoundsPayload.TYPE).map { it.id() }
            val toServer = listOf(
                WorkModeTogglePayload.TYPE,
                PastureSettingsPayload.TYPE,
                PasturePanelOpenPayload.TYPE,
                PasturePanelClosePayload.TYPE,
                LeashOverviewRequestPayload.TYPE,
                AreaWatchPayload.TYPE,
            ).map { it.id() }
            CobblemonDynamics.LOGGER.info(LoadLines.payloads(VERSION, toClient, toServer))
        }
    }

    /**
     * Resolves the pasture from the player's Cobblemon pasture link in its actual dimension level, then validates authorization,
     * same-dimension presence, and inclusive 10.0-block distance through [WorkModeService] (CD-SEC-04). A refused or ignored request in
     * the player's current level sends back the pasture's current state at most once per toggle cooldown.
     */
    fun onToggle(player: ServerPlayer, payload: WorkModeTogglePayload) {
        val target = PastureAdapter.linkedPastureTarget(player) ?: return
        val decision = WorkModeService.request(player, target.pos, payload.enabled, target.level)
        if (decision == WorkModePolicy.Decision.Apply) return
        if (player.serverLevel() === target.level &&
            toggleReplies.tryAcquire(player.uuid, target.level.gameTime, WorkEngineSettings.current.toggleCooldownTicks)
        ) {
            PanelViewers.send(player, target.level, target.pos)
        }
    }

    fun onSettings(player: ServerPlayer, payload: PastureSettingsPayload) {
        val target = PastureAdapter.linkedPastureTarget(player) ?: return
        val decision = WorkModeService.requestSettings(player, target.pos, payload.radius, payload.locked, target.level)
        if (decision == WorkModePolicy.Decision.Apply) return
        if (player.serverLevel() === target.level &&
            toggleReplies.tryAcquire(player.uuid, target.level.gameTime, WorkEngineSettings.current.toggleCooldownTicks)
        ) {
            PanelViewers.send(player, target.level, target.pos)
        }
    }

    fun onAreaWatch(player: ServerPlayer, payload: AreaWatchPayload) {
        AreaWatchers.onWatch(player, payload.pos, payload.watching)
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
