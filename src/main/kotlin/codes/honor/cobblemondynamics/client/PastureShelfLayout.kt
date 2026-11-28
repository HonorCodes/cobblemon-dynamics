package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.PasturePanelStatePayload
import codes.honor.cobblemondynamics.pasture.WorkModePolicy

object PastureShelfLayout {
    private const val PC_WIDTH = 349
    private const val PC_HEIGHT = 205
    private const val STORAGE_X = 85
    private const val STORAGE_Y = 27
    private const val STORAGE_WIDTH = 174
    private const val STORAGE_HEIGHT = 155

    const val SHELF_GAP = 2
    const val TOP_OFFSET = 4
    const val FOLDED_WIDTH = 16
    const val FOLDED_HEIGHT = 20
    const val MIN_UNFOLDED_WIDTH = 36
    const val MAX_UNFOLDED_WIDTH = 86
    const val UNFOLDED_HEIGHT = 78
    const val PADDING = 3
    const val HEADER_HEIGHT = 12
    const val CONTROL_HEIGHT = 13
    const val ROW_GAP = 2

    private const val MAX_COMPACT_WIDTH = 92
    private const val COMPACT_HEIGHT = 40
    private const val COMPACT_PAD = 2
    private const val COMPACT_HEADER_HEIGHT = 10
    private const val COMPACT_CONTROL_HEIGHT = 11
    private const val COMPACT_GAP = 2

    data class Bounds(
        val container: Rect,
        val header: Rect,
        val workMode: Rect,
        val workRadius: Rect,
        val showArea: Rect,
        val accessLock: Rect,
    ) {
        val controls: List<Rect> get() = listOf(workMode, workRadius, showArea, accessLock)
        val interactive: List<Rect> get() = listOf(header, workMode, workRadius, showArea, accessLock)
    }

    fun pcGui(screenWidth: Int, screenHeight: Int): Rect =
        Rect((screenWidth - PC_WIDTH) / 2, (screenHeight - PC_HEIGHT) / 2, PC_WIDTH, PC_HEIGHT)

    fun storageBox(screenWidth: Int, screenHeight: Int): Rect {
        val pc = pcGui(screenWidth, screenHeight)
        return Rect(pc.x + STORAGE_X, pc.y + STORAGE_Y, STORAGE_WIDTH, STORAGE_HEIGHT)
    }

    fun folded(screenWidth: Int, screenHeight: Int): Rect {
        val panel = PanelLayout.panel(screenWidth, screenHeight)
        val sideX = panel.right + SHELF_GAP
        if (sideX + FOLDED_WIDTH <= screenWidth - 1) {
            val y = (panel.y + TOP_OFFSET).coerceIn(0, (screenHeight - FOLDED_HEIGHT).coerceAtLeast(0))
            return Rect(sideX, y, FOLDED_WIDTH, FOLDED_HEIGHT)
        }
        val storage = storageBox(screenWidth, screenHeight)
        val recall = PanelLayout.recall(screenWidth, screenHeight)
        val x = (storage.right + SHELF_GAP).coerceIn(0, (screenWidth - FOLDED_WIDTH).coerceAtLeast(0))
        val y = (maxOf(panel.bottom, recall.bottom) + SHELF_GAP).coerceIn(0, (screenHeight - FOLDED_HEIGHT).coerceAtLeast(0))
        return Rect(x, y, FOLDED_WIDTH, FOLDED_HEIGHT)
    }

    fun unfolded(screenWidth: Int, screenHeight: Int): Bounds {
        val panel = PanelLayout.panel(screenWidth, screenHeight)
        val sideX = panel.right + SHELF_GAP
        val availableSideWidth = screenWidth - sideX - 1
        if (availableSideWidth < MIN_UNFOLDED_WIDTH) {
            return compactBelowRecall(screenWidth, screenHeight, panel)
        }
        val y = (panel.y + TOP_OFFSET).coerceIn(0, (screenHeight - UNFOLDED_HEIGHT).coerceAtLeast(0))
        val width = availableSideWidth.coerceIn(MIN_UNFOLDED_WIDTH, MAX_UNFOLDED_WIDTH)
        val container = Rect(sideX, y, width, UNFOLDED_HEIGHT)
        val innerX = sideX + PADDING
        val innerWidth = width - 2 * PADDING
        val header = Rect(innerX, y + PADDING, innerWidth, HEADER_HEIGHT)
        val row0 = header.bottom + ROW_GAP
        val workMode = Rect(innerX, row0, innerWidth, CONTROL_HEIGHT)
        val workRadius = Rect(innerX, workMode.bottom + ROW_GAP, innerWidth, CONTROL_HEIGHT)
        val showArea = Rect(innerX, workRadius.bottom + ROW_GAP, innerWidth, CONTROL_HEIGHT)
        val accessLock = Rect(innerX, showArea.bottom + ROW_GAP, innerWidth, CONTROL_HEIGHT)
        return Bounds(container, header, workMode, workRadius, showArea, accessLock)
    }

    private fun compactBelowRecall(screenWidth: Int, screenHeight: Int, panel: Rect): Bounds {
        val storage = storageBox(screenWidth, screenHeight)
        val recall = PanelLayout.recall(screenWidth, screenHeight)
        val x = (storage.right + SHELF_GAP).coerceAtLeast(0)
        val maxAvailWidth = (screenWidth - x - 1).coerceAtLeast(MIN_UNFOLDED_WIDTH)
        val width = maxAvailWidth.coerceAtMost(MAX_COMPACT_WIDTH).coerceAtMost((screenWidth - x).coerceAtLeast(1))
        val y = (maxOf(panel.bottom, recall.bottom) + SHELF_GAP).coerceIn(0, (screenHeight - COMPACT_HEIGHT).coerceAtLeast(0))
        val height = COMPACT_HEIGHT.coerceAtMost((screenHeight - y).coerceAtLeast(1))
        val container = Rect(x, y, width, height)
        val innerX = x + COMPACT_PAD
        val innerWidth = (width - 2 * COMPACT_PAD).coerceAtLeast(4)
        val header = Rect(innerX, y + COMPACT_PAD, innerWidth, COMPACT_HEADER_HEIGHT)
        val colLeftWidth = ((innerWidth - COMPACT_GAP) / 2).coerceAtLeast(1)
        val colRightX = innerX + colLeftWidth + COMPACT_GAP
        val colRightWidth = (innerWidth - colLeftWidth - COMPACT_GAP).coerceAtLeast(1)
        val row0Y = header.bottom + COMPACT_GAP
        val workMode = Rect(innerX, row0Y, colLeftWidth, COMPACT_CONTROL_HEIGHT)
        val workRadius = Rect(colRightX, row0Y, colRightWidth, COMPACT_CONTROL_HEIGHT)
        val row1Y = workMode.bottom + COMPACT_GAP
        val showArea = Rect(innerX, row1Y, colLeftWidth, COMPACT_CONTROL_HEIGHT)
        val accessLock = Rect(colRightX, row1Y, colRightWidth, COMPACT_CONTROL_HEIGHT)
        return Bounds(container, header, workMode, workRadius, showArea, accessLock)
    }
}

data class ShelfControlState(val label: TextSpec, val enabled: Boolean, val activeValue: Boolean, val disabledReason: TextSpec? = null)

data class PastureShelfState(
    val visible: Boolean,
    val folded: Boolean,
    val headerLabel: TextSpec,
    val headerTooltip: TextSpec,
    val workMode: ShelfControlState,
    val workRadius: ShelfControlState,
    val showArea: ShelfControlState,
    val accessLock: ShelfControlState,
    val allowedRadiusSteps: List<Int>,
    val nextRadius: Int?,
) {
    companion object {
        const val TAB_COLLAPSED = "cobblemon_dynamics.ui.shelf.tab.open"
        const val TAB_EXPANDED = "cobblemon_dynamics.ui.shelf.tab.close"
        const val TAB_TOOLTIP_OPEN = "cobblemon_dynamics.ui.shelf.tooltip.open"
        const val TAB_TOOLTIP_CLOSE = "cobblemon_dynamics.ui.shelf.tooltip.close"
        const val RADIUS_LABEL = "cobblemon_dynamics.ui.shelf.radius"
        const val SHOW_AREA_ON = "cobblemon_dynamics.ui.shelf.area.on"
        const val SHOW_AREA_OFF = "cobblemon_dynamics.ui.shelf.area.off"
        const val ACCESS_LOCKED = "cobblemon_dynamics.ui.shelf.lock.on"
        const val ACCESS_UNLOCKED = "cobblemon_dynamics.ui.shelf.lock.off"
        const val REASON_LOCKED = "cobblemon_dynamics.ui.shelf.reason.locked"
        const val REASON_NO_CONTROL = "cobblemon_dynamics.ui.shelf.reason.no_control"
        const val REASON_RADIUS_CAPPED = "cobblemon_dynamics.ui.shelf.reason.radius_capped"
        const val REASON_WORK_MODE_OFF = "cobblemon_dynamics.ui.shelf.reason.work_mode_off"
        const val REASON_NOT_OWNER = "cobblemon_dynamics.ui.shelf.reason.not_owner"

        fun nextRadiusStep(current: Int, steps: List<Int>): Int? {
            if (steps.isEmpty()) return null
            val candidate = steps.firstOrNull { it > current } ?: steps.first()
            return candidate.takeIf { it != current }
        }

        fun from(panel: PasturePanelStatePayload?, folded: Boolean, watchingArea: Boolean): PastureShelfState {
            if (panel == null) {
                val blank = ShelfControlState(TextSpec.Key(WorkModeButton.OFF), enabled = false, activeValue = false)
                return PastureShelfState(
                    visible = false,
                    folded = folded,
                    headerLabel = TextSpec.Key(TAB_COLLAPSED),
                    headerTooltip = TextSpec.Key(TAB_TOOLTIP_OPEN),
                    workMode = blank,
                    workRadius = blank,
                    showArea = blank,
                    accessLock = blank,
                    allowedRadiusSteps = emptyList(),
                    nextRadius = null,
                )
            }
            val steps = WorkModePolicy.allowedRadiusSteps(panel.maxHorizontalRadius)
            val nextStep = nextRadiusStep(panel.radius, steps)
            val workModeReason = when {
                panel.canToggle -> null
                panel.locked -> TextSpec.Key(REASON_LOCKED)
                else -> TextSpec.Key(WorkModeButton.LOCKED)
            }
            val radiusEnabled = panel.canToggle && nextStep != null
            val radiusReason = when {
                radiusEnabled -> null
                !panel.canToggle && panel.locked -> TextSpec.Key(REASON_LOCKED)
                !panel.canToggle -> TextSpec.Key(REASON_NO_CONTROL)
                else -> TextSpec.Key(REASON_RADIUS_CAPPED, listOf(panel.maxHorizontalRadius))
            }
            val areaActive = panel.workMode && watchingArea
            val areaReason = if (panel.workMode) null else TextSpec.Key(REASON_WORK_MODE_OFF)
            val lockReason = if (panel.canLock) null else TextSpec.Key(REASON_NOT_OWNER)
            return PastureShelfState(
                visible = true,
                folded = folded,
                headerLabel = TextSpec.Key(if (folded) TAB_COLLAPSED else TAB_EXPANDED),
                headerTooltip = TextSpec.Key(if (folded) TAB_TOOLTIP_OPEN else TAB_TOOLTIP_CLOSE),
                workMode = ShelfControlState(
                    label = TextSpec.Key(if (panel.workMode) WorkModeButton.ON else WorkModeButton.OFF),
                    enabled = panel.canToggle,
                    activeValue = panel.workMode,
                    disabledReason = workModeReason,
                ),
                workRadius = ShelfControlState(
                    label = TextSpec.Key(RADIUS_LABEL, listOf(panel.radius)),
                    enabled = radiusEnabled,
                    activeValue = panel.workMode,
                    disabledReason = radiusReason,
                ),
                showArea = ShelfControlState(
                    label = TextSpec.Key(if (areaActive) SHOW_AREA_ON else SHOW_AREA_OFF),
                    enabled = panel.workMode,
                    activeValue = areaActive,
                    disabledReason = areaReason,
                ),
                accessLock = ShelfControlState(
                    label = TextSpec.Key(if (panel.locked) ACCESS_LOCKED else ACCESS_UNLOCKED),
                    enabled = panel.canLock,
                    activeValue = panel.locked,
                    disabledReason = lockReason,
                ),
                allowedRadiusSteps = steps,
                nextRadius = nextStep,
            )
        }
    }
}
