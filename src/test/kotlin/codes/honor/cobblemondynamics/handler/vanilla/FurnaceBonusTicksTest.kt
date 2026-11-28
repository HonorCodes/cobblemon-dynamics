package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FurnaceBonusTicksTest {
    @Test
    fun `CD-PRG-10 bonus cook ticks are 0 below 1_5, +1 at 1_5, +2 at 2_0, and clamp to cookingTotalTime minus 1`() {
        assertEquals(0, FurnaceHandler.bonusCookTicks(1.0))
        assertEquals(0, FurnaceHandler.bonusCookTicks(1.49))
        assertEquals(1, FurnaceHandler.bonusCookTicks(1.50))
        assertEquals(1, FurnaceHandler.bonusCookTicks(1.99))
        assertEquals(2, FurnaceHandler.bonusCookTicks(2.00))
        assertEquals(3, FurnaceHandler.bonusCookTicks(2.50))

        assertEquals(50, FurnaceHandler.boostedProgress(50, 200, 1.49))
        assertEquals(51, FurnaceHandler.boostedProgress(50, 200, 1.50))
        assertEquals(52, FurnaceHandler.boostedProgress(50, 200, 2.00))
        assertEquals(199, FurnaceHandler.boostedProgress(198, 200, 2.00))
        assertEquals(199, FurnaceHandler.boostedProgress(199, 200, 2.50))
        assertEquals(0, FurnaceHandler.boostedProgress(0, 1, 3.00))
    }
}
