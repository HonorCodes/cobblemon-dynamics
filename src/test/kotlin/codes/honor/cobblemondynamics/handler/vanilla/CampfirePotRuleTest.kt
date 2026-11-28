package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CampfirePotRuleTest {
    @Test
    fun `CD-SNT-06 reports demand only while actively cooking with closed lid outside stoker-never`() {
        assertTrue(CampfirePotRule.hasDemand(progress = 2, totalTime = 200, lidOpen = 0, cooking = true, never = false))
        assertTrue(CampfirePotRule.hasDemand(progress = 198, totalTime = 200, lidOpen = 0, cooking = true, never = false))
        assertTrue(
            CampfirePotRule.hasDemand(progress = 0, totalTime = 200, lidOpen = 0, cooking = false, never = false, canStartRecipe = true),
        )
        assertFalse(
            CampfirePotRule.hasDemand(progress = 0, totalTime = 200, lidOpen = 1, cooking = false, never = false, canStartRecipe = true),
        )
        assertFalse(
            CampfirePotRule.hasDemand(progress = 0, totalTime = 200, lidOpen = 0, cooking = false, never = true, canStartRecipe = true),
        )
        assertFalse(CampfirePotRule.hasDemand(progress = 0, totalTime = 200, lidOpen = 0, cooking = false, never = false))
        assertFalse(CampfirePotRule.hasDemand(progress = 0, totalTime = 200, lidOpen = 0, cooking = true, never = false))
        assertFalse(CampfirePotRule.hasDemand(progress = 2, totalTime = 200, lidOpen = 0, cooking = false, never = false))
        assertFalse(CampfirePotRule.hasDemand(progress = 2, totalTime = 200, lidOpen = 1, cooking = true, never = false))
        assertFalse(CampfirePotRule.hasDemand(progress = 200, totalTime = 200, lidOpen = 0, cooking = true, never = false))
        assertFalse(CampfirePotRule.hasDemand(progress = 2, totalTime = 0, lidOpen = 0, cooking = true, never = false))
        assertFalse(CampfirePotRule.hasDemand(progress = 2, totalTime = 200, lidOpen = 0, cooking = true, never = true))
    }

    @Test
    fun `CD-SNT-07 advances progress by one pot step up to totalTime minus step and preserves step alignment`() {
        assertEquals(4, CampfirePotRule.nextProgress(progress = 2, totalTime = 200, step = 2))
        assertEquals(198, CampfirePotRule.nextProgress(progress = 196, totalTime = 200, step = 2))
        assertEquals(198, CampfirePotRule.nextProgress(progress = 198, totalTime = 200, step = 2))
        assertEquals(2, CampfirePotRule.nextProgress(progress = 1, totalTime = 200, step = 2))
        assertEquals(4, CampfirePotRule.nextProgress(progress = 3, totalTime = 200, step = 2))
        assertEquals(198, CampfirePotRule.nextProgress(progress = 199, totalTime = 200, step = 2))
        assertEquals(0, CampfirePotRule.nextProgress(progress = 0, totalTime = 200, step = 2))
        assertEquals(200, CampfirePotRule.nextProgress(progress = 200, totalTime = 200, step = 2))

        var progress = 0
        var ticks = 0
        while (true) {
            ticks++
            progress += 2
            if (progress == 200) break
            assertTrue(progress < 200, "overshot totalTime: $progress")
            progress = CampfirePotRule.nextProgress(progress, totalTime = 200, step = 2)
        }
        assertEquals(51, ticks)
    }
}
