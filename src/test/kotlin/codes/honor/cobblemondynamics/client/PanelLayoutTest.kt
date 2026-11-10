package codes.honor.cobblemondynamics.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PanelLayoutTest {
    /** GUI-scaled screen sizes: 1920 × 1080 at GUI scales 4, 3, 2 and 1, and 854 × 480 (the smallest window) at 2 and 1. */
    @ParameterizedTest
    @CsvSource("480, 270", "640, 360", "960, 540", "1920, 1080", "427, 240", "854, 480")
    fun `CD-UI-01 the button sits in the panel, 2 pixels above Recall, and overlaps no other control`(width: Int, height: Int) {
        val panel = PanelLayout.panel(width, height)
        val button = PanelLayout.button(width, height)
        val recall = PanelLayout.recall(width, height)

        assertEquals((width - 349) / 2 + 267, panel.x)
        assertEquals((height - 205) / 2 + 8, panel.y)
        assertEquals(82, panel.width)
        assertEquals(169, panel.height)
        assertEquals(panel.x + 6 to panel.y + 153, recall.x to recall.y)

        assertTrue(panel.contains(button), "$button outside $panel")
        assertEquals(recall.y - 2, button.bottom, "the button must end 2 pixels above Recall")
        assertEquals(recall.x to recall.width, button.x to button.width, "the button must sit directly above Recall")
        for (control in PanelLayout.otherControls(width, height)) assertFalse(button.overlaps(control), "$button overlaps $control")
    }

    @Test
    fun `the pasture list keeps its width and top and ends 2 pixels above the button`() {
        assertEquals(Rect(6, 27, 70, 110), PanelLayout.LIST)
        assertEquals(PanelLayout.LIST_HEIGHT, PanelLayout.LIST.height)
        assertEquals(PanelLayout.BUTTON.y - 2, PanelLayout.LIST.bottom)
        assertTrue(PanelLayout.LIST_HEIGHT < PanelLayout.COBBLEMON_LIST_HEIGHT)
    }

    @Test
    fun `Cobblemon's own list would overlap the button, so the list must shrink`() {
        val cobblemonList = Rect(PanelLayout.LIST.x, PanelLayout.LIST.y, PanelLayout.LIST.width, PanelLayout.COBBLEMON_LIST_HEIGHT)
        assertTrue(PanelLayout.BUTTON.overlaps(cobblemonList))
    }

    @Test
    fun `rectangles that only touch do not overlap`() {
        val a = Rect(0, 0, 10, 10)
        assertFalse(a.overlaps(Rect(10, 0, 5, 5)))
        assertFalse(a.overlaps(Rect(0, 10, 5, 5)))
        assertTrue(a.overlaps(Rect(9, 9, 5, 5)))
        assertTrue(a.contains(9.5, 9.5))
        assertFalse(a.contains(10.0, 5.0))
    }

    @Test
    fun `a label that fits keeps its size and a wider one shrinks to the button's inner width`() {
        assertEquals(1f, PanelLayout.labelScale(textWidth = 66, width = 70))
        assertEquals(66f / 73f, PanelLayout.labelScale(textWidth = 73, width = 70), 1e-6f)
        assertEquals(1f, PanelLayout.labelScale(textWidth = 0, width = 70))
    }
}
