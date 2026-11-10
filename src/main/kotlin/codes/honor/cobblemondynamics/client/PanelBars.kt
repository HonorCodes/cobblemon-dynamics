package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.net.PanelEntry
import com.cobblemon.mod.common.client.gui.pasture.PastureWidget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * Draws a Work Stamina bar and a state icon on each row of the pasture list (CD-UI-04). `PastureWidgetMixin` calls it at the tail
 * of `PastureWidget.renderWidget`, so the parts follow the list's own rows, scroll and clipping.
 */
object PanelBars {
    private const val ICON_TEXTURE_SIZE = 16

    /** Above the Pokémon models of the rows, and below vanilla tooltips. */
    private const val LAYER_Z = 200f

    fun render(widget: PastureWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!ClientConfig.panelBars) return
        val state = ClientState.panel ?: return
        if (state.entries.isEmpty()) return
        val byId = state.entries.associateBy { it.pokemonId }
        val list = widget.pastureScrollList
        val window = PanelLayout.listWindow(Rect(list.x, list.y, list.width, list.height))
        var hovered: PanelEntry? = null
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, LAYER_Z)
        graphics.enableScissor(window.x, window.y, window.right, window.bottom)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        list.children().forEachIndexed { index, slot ->
            val entry = byId[slot.pokemon.pokemonId] ?: return@forEachIndexed
            val parts = PanelLayout.rowParts(list.rowLeft, PanelLayout.rowTop(list.y, list.scrollAmount, index))
            if (parts.bar.y >= window.bottom || parts.icon.bottom <= window.y) return@forEachIndexed
            drawBar(graphics, parts.bar, entry.percent)
            val icon = parts.icon
            val size = ICON_TEXTURE_SIZE
            graphics.blit(StateIcons.iconFor(entry.state), icon.x, icon.y, icon.width, icon.height, 0f, 0f, size, size, size, size)
            val x = mouseX.toDouble()
            val y = mouseY.toDouble()
            if (window.contains(x, y) && parts.contains(x, y)) hovered = entry
        }
        RenderSystem.disableBlend()
        graphics.disableScissor()
        graphics.pose().popPose()
        val entry = hovered ?: return
        val lines = StaminaBar.tooltip(entry.percent, entry.state).map {
            Component.translatable(it.key, *it.args.toTypedArray()).visualOrderText
        }
        Minecraft.getInstance().screen?.setTooltipForNextRenderPass(lines)
    }

    private fun drawBar(graphics: GuiGraphics, bar: Rect, percent: Int) {
        for ((rect, color) in StaminaBar.parts(bar, percent)) graphics.fill(rect.x, rect.y, rect.right, rect.bottom, color)
    }
}
