package codes.honor.cobblemondynamics.client

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PastureShelfLayoutTest {
    private val resolutions = listOf(
        360 to 240,
        384 to 240,
        427 to 240,
        640 to 360,
        854 to 480,
        1280 to 720,
        1920 to 1080,
    )

    @Test
    fun `CD-SHF-01 folded and unfolded shelf fit inside screen and never overlap PCGUI storage list or Recall`() {
        for ((w, h) in resolutions) {
            val screen = Rect(0, 0, w, h)
            val pc = PastureShelfLayout.pcGui(w, h)
            val storage = PastureShelfLayout.storageBox(w, h)
            val panel = PanelLayout.panel(w, h)
            val button = PanelLayout.button(w, h)
            val recall = PanelLayout.recall(w, h)
            val otherControls = PanelLayout.otherControls(w, h)
            val protected = buildList {
                if (w >= 427) add(pc)
                add(storage)
                add(panel)
                add(button)
                add(recall)
                addAll(otherControls)
            }

            val folded = PastureShelfLayout.folded(w, h)
            assertTrue(
                0 <= folded.x && folded.right <= w && 0 <= folded.y && folded.bottom <= h && screen.contains(folded),
                "folded $folded outside $screen",
            )
            for (existing in protected) {
                assertFalse(folded.overlaps(existing), "folded $folded overlaps $existing at ${w}x$h")
            }

            val unfolded = PastureShelfLayout.unfolded(w, h)
            val box = unfolded.container
            assertTrue(
                0 <= box.x && box.right <= w && 0 <= box.y && box.bottom <= h && screen.contains(box),
                "unfolded $box outside $screen",
            )
            for (existing in protected) {
                assertFalse(box.overlaps(existing), "unfolded $box overlaps $existing at ${w}x$h")
            }

            val interactive = unfolded.interactive
            for (i in interactive.indices) {
                assertTrue(unfolded.container.contains(interactive[i]), "child ${interactive[i]} escapes container at ${w}x$h")
                for (j in i + 1 until interactive.size) {
                    assertFalse(interactive[i].overlaps(interactive[j]), "${interactive[i]} overlaps ${interactive[j]} at ${w}x$h")
                }
            }
        }
    }
}
