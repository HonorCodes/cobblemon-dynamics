package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.net.PasturePanelClosePayload
import codes.honor.cobblemondynamics.net.PasturePanelOpenPayload
import com.cobblemon.mod.common.client.gui.pasture.PasturePCGUIConfiguration
import com.cobblemon.mod.common.client.gui.pc.PCGUI
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

/** Adds the Work Mode button to Cobblemon's pasture panel and tells the server while the panel is open. Client only. */
object PastureScreenHooks {
    private val mixins = PanelMixinCheck(MixinState::applied) { CobblemonDynamics.LOGGER.warn(it) }

    /** The pasture panel that the server knows is open. A resize runs `init` again, which builds a new button for the same screen. */
    private var openScreen: PCGUI? = null
    private var button: WorkModeButton? = null

    fun register() {
        NeoForge.EVENT_BUS.addListener<ScreenEvent.Init.Post>(::onInit)
        NeoForge.EVENT_BUS.addListener<ScreenEvent.Render.Pre> { if (it.screen === openScreen) button?.show(ClientState.panel) }
        NeoForge.EVENT_BUS.addListener<ScreenEvent.MouseButtonPressed.Pre>(::onClick)
        NeoForge.EVENT_BUS.addListener<ScreenEvent.Closing> { if (it.screen === openScreen) close() }
    }

    /** Forgets the open panel without telling the server, for a disconnect. */
    fun reset() {
        openScreen = null
        button = null
    }

    private fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? PCGUI ?: return
        if (screen.configuration !is PasturePCGUIConfiguration) return
        if (openScreen !== screen) {
            openScreen = screen
            ClientState.clearPanel()
            mixins.listShortened()
            ClientNet.send(PasturePanelOpenPayload)
        }
        val rect = PanelLayout.button(screen.width, screen.height)
        val created = WorkModeButton(rect.x, rect.y)
        created.show(ClientState.panel)
        event.addListener(created)
        button = created
    }

    /**
     * The button takes the clicks on it before the panel does, as it draws over the panel. A click on the disabled button goes
     * nowhere, so it never reaches the list rows that a list of Cobblemon's height keeps under the button.
     */
    private fun onClick(event: ScreenEvent.MouseButtonPressed.Pre) {
        val button = button ?: return
        if (event.screen !== openScreen || !button.covers(event.mouseX, event.mouseY)) return
        button.mouseClicked(event.mouseX, event.mouseY, event.button)
        event.isCanceled = true
    }

    private fun close() {
        reset()
        ClientState.clearPanel()
        ClientNet.send(PasturePanelClosePayload)
    }
}
