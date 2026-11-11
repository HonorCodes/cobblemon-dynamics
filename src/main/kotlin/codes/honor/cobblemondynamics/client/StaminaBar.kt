package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.command.StaminaReport
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import net.minecraft.resources.ResourceLocation
import kotlin.math.max

/** The Work Stamina bar that the panel rows and the head bars draw: a 1-pixel frame around the fill. */
object StaminaBar {
    const val FRAME = 0xFF1A1A1A.toInt()
    const val EMPTY = 0xFF4B4B4B.toInt()
    const val GREEN = 0xFF55C24A.toInt()
    const val YELLOW = 0xFFE3C238.toInt()
    const val RED = 0xFFD94545.toInt()

    /** `%s%% Work Stamina`: the stat is always "Work Stamina" (CD-UI-11). */
    const val TOOLTIP = "cobblemon_dynamics.ui.stamina"

    /**
     * The bar sprites (CD-ART-04): the frame with the empty inside, and the fill, which the code crops to the stamina and tints
     * with [color]. Each is the size of its bar, so it draws one texture pixel per GUI or name-tag pixel.
     */
    val PANEL_BAR: ResourceLocation = id("textures/gui/stamina_bar/panel_bar.png")
    val PANEL_FILL: ResourceLocation = id("textures/gui/stamina_bar/panel_fill.png")
    val HEAD_BAR: ResourceLocation = id("textures/gui/stamina_bar/head_bar.png")
    val HEAD_FILL: ResourceLocation = id("textures/gui/stamina_bar/head_fill.png")

    /** A tooltip line as a lang key and its arguments. */
    data class Line(val key: String, val args: List<Any> = emptyList())

    /** The filled part of a bar whose inner width is [width]: rounded down, and at least 1 pixel above 0%. */
    fun fillWidth(percent: Int, width: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        return if (clamped == 0) 0 else max(1, width * clamped / 100)
    }

    /** The inside of [bar], within its 1-pixel frame. */
    fun inner(bar: Rect): Rect = Rect(bar.x + 1, bar.y + 1, bar.width - 2, bar.height - 2)

    /** The filled part of [bar]'s inside at [percent], from its left end, or null when nothing is filled. */
    fun fill(bar: Rect, percent: Int): Rect? {
        val inner = inner(bar)
        val width = fillWidth(percent, inner.width)
        return if (width == 0) null else Rect(inner.x, inner.y, width, inner.height)
    }

    /** [bar] as rectangles that do not overlap, each with its colour: the 1-pixel frame, the fill and the empty rest. */
    fun parts(bar: Rect, percent: Int): List<Pair<Rect, Int>> {
        val inner = inner(bar)
        val filled = fillWidth(percent, inner.width)
        val parts = mutableListOf(
            Rect(bar.x, bar.y, bar.width, 1) to FRAME,
            Rect(bar.x, bar.bottom - 1, bar.width, 1) to FRAME,
            Rect(bar.x, inner.y, 1, inner.height) to FRAME,
            Rect(inner.right, inner.y, 1, inner.height) to FRAME,
        )
        if (filled > 0) parts += Rect(inner.x, inner.y, filled, inner.height) to color(percent)
        if (filled < inner.width) parts += Rect(inner.x + filled, inner.y, inner.width - filled, inner.height) to EMPTY
        return parts
    }

    fun color(percent: Int): Int = when {
        percent >= 50 -> GREEN
        percent >= 25 -> YELLOW
        else -> RED
    }

    /** CD-UI-10: the percentage and the state name. */
    fun tooltip(percent: Int, status: WorkerStatus): List<Line> =
        listOf(Line(TOOLTIP, listOf(percent)), Line(StaminaReport.stateKey(status)))
}
