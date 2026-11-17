package codes.honor.cobblemondynamics.compat.ars_nouveau

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourcelinkRuleTest {
    /** `VolcanicSourcelinkTile.getSourceValue` of the 5.13.5 jar gives a fuel item its smelting burn time divided by 12: 133 for coal. */
    private val coalSource = 1_600 / 12

    @Test
    fun `CD-FUEL-12 one gift per 1,600 ticks of Working, of the Source of one coal`() {
        var worked = 0
        var values = 0
        val gifts = mutableListOf<Int>()
        for (tick in 1..4_800) {
            val step = SourcelinkRule.step(worked, SourcelinkRule.DEFAULT_INTERVAL_TICKS)
            worked = step.worked
            val gift = SourcelinkRule.gift(step) { coalSource.also { values++ } }
            if (gift > 0) {
                assertEquals(coalSource, gift)
                gifts += tick
            }
        }
        assertEquals(listOf(1_600, 3_200, 4_800), gifts)
        // The sourcelink's own value of a coal also counts the burn toward its own progress, so it is read once per gift.
        assertEquals(3, values)
    }

    @Test
    fun `CD-FUEL-12 the role file's interval sets the pace, and a missing one falls back to 1,600`() {
        assertEquals(1_600, SourcelinkRule.intervalOf(JsonObject()))
        assertEquals(800, SourcelinkRule.intervalOf(JsonObject().apply { addProperty("interval_ticks", 800) }))
        assertFalse(SourcelinkRule.step(798, 800).gift)
        assertTrue(SourcelinkRule.step(799, 800).gift)
        assertEquals(0, SourcelinkRule.step(799, 800).worked)
    }

    /** The worker adds Source only: it burns no fuel item of the pedestals around the sourcelink, and they keep their own rules. */
    @Test
    fun `CD-FUEL-12 a full or disabled sourcelink has no demand`() {
        assertTrue(SourcelinkRule.hasDemand(canAcceptSource = true, disabled = false))
        assertFalse(SourcelinkRule.hasDemand(canAcceptSource = false, disabled = false))
        assertFalse(SourcelinkRule.hasDemand(canAcceptSource = true, disabled = true))
    }
}
