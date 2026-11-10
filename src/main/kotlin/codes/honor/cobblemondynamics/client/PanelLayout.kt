package codes.honor.cobblemondynamics.client

/** A rectangle in GUI pixels. It covers [x] until [right] and [y] until [bottom], both exclusive. */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun offset(dx: Int, dy: Int): Rect = Rect(x + dx, y + dy, width, height)

    fun contains(other: Rect): Boolean = other.x >= x && other.y >= y && other.right <= right && other.bottom <= bottom

    fun contains(px: Double, py: Double): Boolean = px >= x && px < right && py >= y && py < bottom

    fun overlaps(other: Rect): Boolean = x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

/**
 * The pasture panel of Cobblemon 1.8.4's PC screen, with the Work Mode button directly above Recall (CD-UI-01). Offsets count from
 * the panel's top-left corner. Cobblemon's list would reach under the button, so a mixin shortens it to [LIST_HEIGHT].
 */
object PanelLayout {
    // PCGUI centres its 349 × 205 base. The pasture panel sits at (267, 8) in it: StorageWidget (85, 27) plus (182, -19).
    private const val PC_WIDTH = 349
    private const val PC_HEIGHT = 205
    private const val PANEL_X = 267
    private const val PANEL_Y = 8

    const val PANEL_WIDTH = 82
    const val PANEL_HEIGHT = 169
    const val GAP = 2

    /** Cobblemon's list: 70 wide, its top at `listY - 4` = 27, 120 high. Its rows and scrollbar sit inside. */
    const val LIST_TOP = 27
    const val COBBLEMON_LIST_HEIGHT = 120

    private const val CONTROL_X = 6
    private const val CONTROL_WIDTH = 70
    private const val RECALL_Y = 153
    private const val RECALL_HEIGHT = 17

    const val BUTTON_HEIGHT = 12
    const val BUTTON_Y = RECALL_Y - GAP - BUTTON_HEIGHT

    /** The list's height with the button below it, as `PasturePokemonScrollListMixin` sets it. */
    const val LIST_HEIGHT = BUTTON_Y - GAP - LIST_TOP

    /** The space that a label keeps free on each side, as vanilla buttons do. */
    private const val LABEL_MARGIN = 2

    val PANEL = Rect(0, 0, PANEL_WIDTH, PANEL_HEIGHT)
    val RECALL = Rect(CONTROL_X, RECALL_Y, CONTROL_WIDTH, RECALL_HEIGHT)
    val BUTTON = Rect(CONTROL_X, BUTTON_Y, CONTROL_WIDTH, BUTTON_HEIGHT)
    val LIST = Rect(CONTROL_X, LIST_TOP, CONTROL_WIDTH, LIST_HEIGHT)

    /** The panel on a screen of [screenWidth] × [screenHeight] GUI pixels, as `PCGUI` places it. */
    fun panel(screenWidth: Int, screenHeight: Int): Rect =
        PANEL.offset((screenWidth - PC_WIDTH) / 2 + PANEL_X, (screenHeight - PC_HEIGHT) / 2 + PANEL_Y)

    fun button(screenWidth: Int, screenHeight: Int): Rect = onScreen(BUTTON, screenWidth, screenHeight)

    fun recall(screenWidth: Int, screenHeight: Int): Rect = onScreen(RECALL, screenWidth, screenHeight)

    /** The controls of the panel other than the button. */
    fun otherControls(screenWidth: Int, screenHeight: Int): List<Rect> =
        listOf(LIST, RECALL).map { onScreen(it, screenWidth, screenHeight) }

    /** A row of the list and the 3 pixels below it, as `AbstractSelectionList` spaces the 29-pixel slots. */
    const val ROW_HEIGHT = 32

    /** The top of row [index], as `AbstractSelectionList.getRowTop` computes it for a list with no header. */
    fun rowTop(listY: Int, scrollAmount: Double, index: Int): Int = listY + 4 - scrollAmount.toInt() + index * ROW_HEIGHT

    /** The part of [list] that shows rows: Cobblemon clips them to `y + 4` until `y - 1 + height`. */
    fun listWindow(list: Rect): Rect = Rect(list.x, list.y + 4, list.width, list.height - 5)

    /** A row's state icon and Work Stamina bar. Hovering either one shows the row's tooltip (CD-UI-10). */
    data class RowParts(val icon: Rect, val bar: Rect) {
        fun contains(px: Double, py: Double): Boolean = icon.contains(px, py) || bar.contains(px, py)
    }

    /**
     * Cobblemon draws a row's 62 × 29 slot at (rowLeft - 4, rowTop + 2). The icon fills the dark strip above the slot's move
     * button, and the bar runs along the top edge of the slot's card, from the gap above the slot into its frame.
     */
    fun rowParts(rowLeft: Int, rowTop: Int): RowParts {
        val slotX = rowLeft - 4
        val slotY = rowTop + 2
        return RowParts(icon = Rect(slotX + 2, slotY + 2, 8, 8), bar = Rect(slotX + 11, slotY - 1, 50, 4))
    }

    /** The text scale that fits a label [textWidth] pixels wide into a button [width] pixels wide. */
    fun labelScale(textWidth: Int, width: Int): Float {
        val room = width - 2 * LABEL_MARGIN
        return if (textWidth <= room) 1f else room.toFloat() / textWidth
    }

    private fun onScreen(rect: Rect, screenWidth: Int, screenHeight: Int): Rect {
        val panel = panel(screenWidth, screenHeight)
        return rect.offset(panel.x, panel.y)
    }
}
