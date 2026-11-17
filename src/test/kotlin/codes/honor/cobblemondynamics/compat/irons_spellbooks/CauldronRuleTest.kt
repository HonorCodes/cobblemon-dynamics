package codes.honor.cobblemondynamics.compat.irons_spellbooks

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CauldronRuleTest {
    private val steps = CauldronParams.of(JsonObject()).extraStepsPerTick

    /**
     * One tick of Iron's Spells 'n Spellbooks 3.16.3's cauldron for one slot that holds an item while the cauldron boils (RES11): the
     * cook time counts up 1, and in the tick that takes it past 100 the item melts and the count goes back to 0.
     */
    private fun cauldronTick(cooktime: Int): Pair<Boolean, Int> {
        val next = cooktime + 1
        return if (next > CauldronRule.END) true to 0 else false to next
    }

    @Test
    fun `CD-MCH-07 the worker advances each running cook time by one extra step`() {
        val cooktimes = intArrayOf(1, 50, 99, 0)
        CauldronRule.advance(cooktimes, steps)
        assertArrayEquals(intArrayOf(2, 51, 100, 0), cooktimes)
    }

    @Test
    fun `CD-MCH-07 a cook time never passes its end, and an idle slot stays idle`() {
        val cooktimes = intArrayOf(100, 0, 99)
        CauldronRule.advance(cooktimes, steps = 5)
        assertArrayEquals(intArrayOf(100, 0, 100), cooktimes)
    }

    /** The extra step comes after the cauldron's own step of the same tick, so the item melts on the cauldron's own step. */
    @Test
    fun `CD-MCH-07 a worked item melts within 55 ticks, and never past its end`() {
        var cooktime = 0
        var ticks = 0
        var melted = false
        while (!melted && ticks < 200) {
            val (done, next) = cauldronTick(cooktime)
            melted = done
            cooktime = next
            val slot = intArrayOf(cooktime)
            CauldronRule.advance(slot, steps)
            cooktime = slot[0]
            assertTrue(cooktime <= CauldronRule.END, "the cook time passed its end: $cooktime")
            ticks++
        }
        assertTrue(melted)
        assertEquals(51, ticks)
    }

    @Test
    fun `CD-MCH-07 a cauldron has demand only while a cook time runs below its end`() {
        assertTrue(CauldronRule.cooking(intArrayOf(0, 37, 0)))
        assertFalse(CauldronRule.cooking(intArrayOf(0, 0, 0)))
        assertFalse(CauldronRule.cooking(intArrayOf(100, 0)))
    }

    @Test
    fun `CD-MCH-07 the default is one extra step per tick`() {
        assertEquals(1, steps)
    }
}
