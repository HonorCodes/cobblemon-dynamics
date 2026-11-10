package codes.honor.cobblemondynamics.stamina

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PanelCadenceTest {
    @Test
    fun `CD-UI-05 sends at 0, 20 and 40, never at 19`() =
        assertEquals(listOf(0L, 20L, 40L), (0L..45L).filter { PanelCadence.shouldSend(0, it) })

    @Test
    fun `CD-UI-05 the cadence counts from the tick the panel opened`() {
        assertEquals(listOf(1_007L, 1_027L, 1_047L), (1_000L..1_050L).filter { PanelCadence.shouldSend(1_007, it) })
        assertFalse(PanelCadence.shouldSend(1_007, 987))
    }
}
