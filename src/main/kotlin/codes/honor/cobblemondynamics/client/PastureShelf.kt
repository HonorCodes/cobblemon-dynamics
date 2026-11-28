package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.AreaWatchPayload
import codes.honor.cobblemondynamics.net.PasturePanelStatePayload
import codes.honor.cobblemondynamics.net.PastureSettingsPayload
import codes.honor.cobblemondynamics.net.WorkModeTogglePayload
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class PastureShelf(
    private val screenWidth: Int,
    private val screenHeight: Int,
    initialFolded: Boolean,
    private val onFoldChanged: (Boolean) -> Unit,
) : AbstractWidget(0, 0, 0, 0, Component.translatable(PastureShelfState.TAB_COLLAPSED)) {
    private var panel: PasturePanelStatePayload? = null
    private var folded: Boolean = initialFolded
    private var viewState: PastureShelfState = PastureShelfState.from(null, initialFolded, watchingArea = false)
    private var unfoldedBounds: PastureShelfLayout.Bounds = PastureShelfLayout.unfolded(screenWidth, screenHeight)
    private var foldedRect: Rect = PastureShelfLayout.folded(screenWidth, screenHeight)

    init {
        syncBounds()
    }

    fun isFolded(): Boolean = folded

    fun show(nextPanel: PasturePanelStatePayload?) {
        panel = nextPanel
        val watching = nextPanel?.pos?.let(WorkAreaOverlay::isWatching) == true
        viewState = PastureShelfState.from(nextPanel, folded, watching)
        visible = viewState.visible
        syncBounds()
    }

    fun covers(mouseX: Double, mouseY: Double): Boolean = visible && Rect(x, y, width, height).contains(mouseX, mouseY)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!covers(mouseX, mouseY)) return false
        if (button != 0) return true
        if (folded) {
            folded = false
            onFoldChanged(false)
            show(panel)
            playDownSound(Minecraft.getInstance().soundManager)
            return true
        }
        val current = panel ?: return true
        when {
            unfoldedBounds.header.contains(mouseX, mouseY) -> {
                folded = true
                onFoldChanged(true)
                show(panel)
                playDownSound(Minecraft.getInstance().soundManager)
            }

            unfoldedBounds.workMode.contains(mouseX, mouseY) && viewState.workMode.enabled -> {
                ClientNet.send(WorkModeTogglePayload(!current.workMode))
                playDownSound(Minecraft.getInstance().soundManager)
            }

            unfoldedBounds.workRadius.contains(mouseX, mouseY) && viewState.workRadius.enabled -> {
                val nextRadius = viewState.nextRadius ?: return true
                ClientNet.send(PastureSettingsPayload(radius = nextRadius))
                playDownSound(Minecraft.getInstance().soundManager)
            }

            unfoldedBounds.showArea.contains(mouseX, mouseY) && viewState.showArea.enabled -> {
                val targetPos = current.pos
                if (targetPos != null) {
                    val nowWatching = WorkAreaOverlay.toggle(targetPos)
                    ClientNet.send(AreaWatchPayload(targetPos, nowWatching))
                    show(panel)
                    playDownSound(Minecraft.getInstance().soundManager)
                }
            }

            unfoldedBounds.accessLock.contains(mouseX, mouseY) && viewState.accessLock.enabled -> {
                ClientNet.send(PastureSettingsPayload(locked = !current.locked))
                playDownSound(Minecraft.getInstance().soundManager)
            }
        }
        return true
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!viewState.visible) return
        val font = Minecraft.getInstance().font
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        if (folded) {
            val hovered = foldedRect.contains(mx, my)
            drawControlBox(graphics, foldedRect, activeValue = panel?.workMode == true, enabled = true, hovered = hovered)
            drawScaledLabel(graphics, font, foldedRect, viewState.headerLabel.toComponent(), ACTIVE_TEXT)
            if (hovered) {
                Minecraft.getInstance().screen?.setTooltipForNextRenderPass(listOf(viewState.headerTooltip.toComponent().visualOrderText))
            }
            return
        }
        val container = unfoldedBounds.container
        graphics.fill(container.x, container.y, container.right, container.bottom, PANEL_BORDER)
        graphics.fill(container.x + 1, container.y + 1, container.right - 1, container.bottom - 1, PANEL_BG)

        val headerHovered = unfoldedBounds.header.contains(mx, my)
        drawControlBox(graphics, unfoldedBounds.header, activeValue = false, enabled = true, hovered = headerHovered)
        drawScaledLabel(graphics, font, unfoldedBounds.header, viewState.headerLabel.toComponent(), ACTIVE_TEXT)

        var tooltipSpec: TextSpec? = if (headerHovered) viewState.headerTooltip else null
        val entries = listOf(
            unfoldedBounds.workMode to viewState.workMode,
            unfoldedBounds.workRadius to viewState.workRadius,
            unfoldedBounds.showArea to viewState.showArea,
            unfoldedBounds.accessLock to viewState.accessLock,
        )
        for ((rect, control) in entries) {
            val hovered = rect.contains(mx, my)
            drawControlBox(graphics, rect, control.activeValue, control.enabled, hovered)
            val color = if (control.enabled) ACTIVE_TEXT else INACTIVE_TEXT
            drawScaledLabel(graphics, font, rect, control.label.toComponent(), color)
            if (hovered && !control.enabled && control.disabledReason != null) {
                tooltipSpec = control.disabledReason
            }
        }
        if (tooltipSpec != null) {
            Minecraft.getInstance().screen?.setTooltipForNextRenderPass(listOf(tooltipSpec.toComponent().visualOrderText))
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    private fun syncBounds() {
        foldedRect = PastureShelfLayout.folded(screenWidth, screenHeight)
        unfoldedBounds = PastureShelfLayout.unfolded(screenWidth, screenHeight)
        val box = if (folded) foldedRect else unfoldedBounds.container
        x = box.x
        y = box.y
        width = box.width
        height = box.height
    }

    private fun drawControlBox(graphics: GuiGraphics, rect: Rect, activeValue: Boolean, enabled: Boolean, hovered: Boolean) {
        RenderSystem.enableBlend()
        RenderSystem.enableDepthTest()
        graphics.blitSprite(WorkModeSprites.of(activeValue, enabled, hovered && enabled), rect.x, rect.y, rect.width, rect.height)
    }

    private fun drawScaledLabel(graphics: GuiGraphics, font: Font, rect: Rect, text: Component, color: Int) {
        val textWidth = font.width(text)
        if (textWidth <= 0) return
        val scale = PanelLayout.labelScale(textWidth, rect.width)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(rect.x + rect.width / 2f, rect.y + (rect.height - font.lineHeight * scale) / 2f + scale, 0f)
        pose.scale(scale, scale, 1f)
        graphics.drawString(font, text, -textWidth / 2, 0, color, true)
        pose.popPose()
    }

    companion object {
        private const val PANEL_BORDER = 0xDD14181F.toInt()
        private const val PANEL_BG = 0xE6222833.toInt()
        private const val ACTIVE_TEXT = 0xFFFFFFFF.toInt()
        private const val INACTIVE_TEXT = 0xFFA0A0A0.toInt()
    }
}
