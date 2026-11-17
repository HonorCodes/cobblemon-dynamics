package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.xp.CollectorMenu
import codes.honor.cobblemondynamics.xp.CollectorSlots
import codes.honor.cobblemondynamics.xp.CollectorStore
import codes.honor.cobblemondynamics.xp.XpLevels
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

/**
 * The Experience Collector's screen (CD-XPC-04): the stored XP in points, the level that a player at level 0 would reach with it, and
 * the button "Collect experience". The button sends vanilla's container button packet, and the server checks it (CD-XPC-05).
 */
class ExperienceCollectorScreen(menu: CollectorMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<CollectorMenu>(menu, inventory, title) {
    private var collect: Button? = null

    init {
        imageWidth = WIDTH
        imageHeight = HEIGHT
    }

    override fun init() {
        super.init()
        titleLabelX = (imageWidth - font.width(title)) / 2
        val button = Button.builder(Component.translatable(COLLECT_KEY)) { press() }
            .bounds(leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
        collect = addRenderableWidget(button)
        refresh()
    }

    override fun containerTick() {
        super.containerTick()
        refresh()
    }

    /** An empty collector has nothing to give. */
    private fun refresh() {
        collect?.active = menu.storedXp > 0
    }

    private fun press() {
        minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, CollectorSlots.COLLECT_BUTTON)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.blit(TEXTURE, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, WIDTH, HEIGHT)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false)
        val xp = menu.storedXp
        graphics.drawString(font, Component.translatable(POINTS_KEY, CollectorStore.format(xp)), TEXT_X, POINTS_Y, XP_COLOR, false)
        graphics.drawString(font, Component.translatable(LEVEL_KEY, XpLevels.levelFor(xp)), TEXT_X, LEVEL_Y, XP_COLOR, false)
    }

    companion object {
        const val COLLECT_KEY = "cobblemon_dynamics.experience_collector.collect"
        const val POINTS_KEY = "cobblemon_dynamics.experience_collector.points"
        const val LEVEL_KEY = "cobblemon_dynamics.experience_collector.level"

        /** The background, `art/textures/gui/experience_collector.pixels`, which is exactly the screen's size. */
        private val TEXTURE: ResourceLocation = id("textures/gui/experience_collector.png")
        private const val WIDTH = 176
        private const val HEIGHT = 82

        /** The two lines sit in the background's dark display, beside its orb. */
        private const val TEXT_X = 24
        private const val POINTS_Y = 22
        private const val LEVEL_Y = 34
        private const val BUTTON_X = 28
        private const val BUTTON_Y = 54
        private const val BUTTON_WIDTH = 120
        private const val BUTTON_HEIGHT = 20

        /** Vanilla's label grey, and the green of its XP level number. */
        private const val TITLE_COLOR = 0x404040
        private const val XP_COLOR = 0x80FF20
    }
}
