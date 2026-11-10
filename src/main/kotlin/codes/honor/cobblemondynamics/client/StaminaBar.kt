package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.command.StaminaReport
import codes.honor.cobblemondynamics.engine.WorkerStatus
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

    /** A tooltip line as a lang key and its arguments. */
    data class Line(val key: String, val args: List<Any> = emptyList())

    /** The filled part of a bar whose inner width is [width]: rounded down, and at least 1 pixel above 0%. */
    fun fillWidth(percent: Int, width: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        return if (clamped == 0) 0 else max(1, width * clamped / 100)
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
