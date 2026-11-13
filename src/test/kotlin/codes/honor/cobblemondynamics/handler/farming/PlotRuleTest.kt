package codes.honor.cobblemondynamics.handler.farming

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlotRuleTest {
    @Test
    fun `CD-CRP-09 air above farmland keeps the binding, and stone in the cell or air below it clears it`() {
        // A player harvested the crop by hand: air above farmland.
        assertTrue(PlotRule.keeps(cellInRoleTag = false, cellEmpty = true, belowIsAir = false))
        // Stone was placed in the cell.
        assertFalse(PlotRule.keeps(cellInRoleTag = false, cellEmpty = false, belowIsAir = false))
        // The farmland under the empty cell is gone.
        assertFalse(PlotRule.keeps(cellInRoleTag = false, cellEmpty = true, belowIsAir = true))
        // The crop stands, so the binding holds as for any target.
        assertTrue(PlotRule.keeps(cellInRoleTag = true, cellEmpty = false, belowIsAir = false))
    }
}
