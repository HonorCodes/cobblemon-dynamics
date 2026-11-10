package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.PasturePanelStatePayload
import codes.honor.cobblemondynamics.net.WorkModeTogglePayload
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

/**
 * The Work Mode button of the pasture panel. It shows the state that the server last sent and asks for the other one. It stays
 * hidden until the first state arrives, so it never shows a guessed label (CD-UI-02).
 */
class WorkModeButton(x: Int, y: Int) :
    Button(
        x,
        y,
        PanelLayout.BUTTON.width,
        PanelLayout.BUTTON.height,
        Component.translatable(OFF),
        OnPress { (it as WorkModeButton).request() },
        DEFAULT_NARRATION,
    ) {
    private var state: PasturePanelStatePayload? = null

    init {
        visible = false
    }

    fun show(state: PasturePanelStatePayload?) {
        if (state == this.state) return
        this.state = state
        visible = state != null
        if (state == null) return
        message = Component.translatable(if (state.workMode) ON else OFF)
        active = state.canToggle
        // CD-UI-03: a viewer who may not toggle sees why.
        setTooltip(if (state.canToggle) null else Tooltip.create(Component.translatable(LOCKED)))
    }

    /** True when ([mouseX], [mouseY]) is on the button, enabled or not. */
    fun covers(mouseX: Double, mouseY: Double): Boolean = visible && Rect(x, y, width, height).contains(mouseX, mouseY)

    private fun request() {
        val state = state ?: return
        if (state.canToggle) ClientNet.send(WorkModeTogglePayload(!state.workMode))
    }

    /** The label shrinks to fit, because "Work Mode: Off" is wider than Recall in the vanilla font. */
    override fun renderString(guiGraphics: GuiGraphics, font: Font, color: Int) {
        val textWidth = font.width(message)
        val scale = PanelLayout.labelScale(textWidth, width)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + width / 2f, y + (height - font.lineHeight * scale) / 2f + scale, 0f)
        pose.scale(scale, scale, 1f)
        guiGraphics.drawString(font, message, -textWidth / 2, 0, color, true)
        pose.popPose()
    }

    companion object {
        const val ON = "cobblemon_dynamics.ui.work_mode.on"
        const val OFF = "cobblemon_dynamics.ui.work_mode.off"
        const val LOCKED = "cobblemon_dynamics.ui.work_mode.locked"
    }
}
