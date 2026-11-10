package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.vanilla.CauldronFill.Contents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CauldronFillTest {
    /** The tick, counted from 1, on which each water level is first reached, at [mbPerTick] from an empty cauldron. */
    private fun levelTicks(mbPerTick: Int): Map<Int, Int> {
        val reached = LinkedHashMap<Int, Int>()
        var fill = CauldronFill.Fill(level = 0, storedMb = 0)
        var tick = 0
        while (fill.level < CauldronFill.FULL_LEVEL) {
            tick++
            fill = CauldronFill.deliver(fill.level, fill.storedMb, mbPerTick)
            for (level in 1..fill.level) reached.putIfAbsent(level, tick)
        }
        return reached
    }

    @Test
    fun `CD-PMP-04 a Lv10 Pump at 50 mB per tick raises a level per 333 mB and fills an empty cauldron on tick 20`() {
        assertEquals(mapOf(1 to 7, 2 to 14, 3 to 20), levelTicks(50))
    }

    @Test
    fun `CD-PMP-04 a Lv100 Pump at 500 mB per tick fills a cauldron in 2 ticks`() {
        assertEquals(mapOf(1 to 1, 2 to 2, 3 to 2), levelTicks(500))
        assertEquals(CauldronFill.Fill(level = 1, storedMb = 167), CauldronFill.deliver(0, 0, 500))
    }

    @Test
    fun `CD-PMP-04 a full cauldron takes nothing and keeps nothing`() {
        assertEquals(CauldronFill.Fill(level = 3, storedMb = 0), CauldronFill.deliver(2, 300, 50))
        assertEquals(CauldronFill.Fill(level = 3, storedMb = 0), CauldronFill.deliver(3, 0, 500))
        assertEquals(CauldronFill.Fill(level = 3, storedMb = 0), CauldronFill.deliver(0, 0, Int.MAX_VALUE))
    }

    @Test
    fun `CD-PMP-06 an empty cauldron and water at level 2 have demand, and full water, lava and powder snow have none`() {
        assertTrue(CauldronFill.hasDemand(Contents.EMPTY, 0), "an empty cauldron")
        assertTrue(CauldronFill.hasDemand(Contents.WATER, 2), "water at level 2")
        assertFalse(CauldronFill.hasDemand(Contents.WATER, 3), "water at level 3")
        assertFalse(CauldronFill.hasDemand(Contents.LAVA, 0), "a lava cauldron")
        assertFalse(CauldronFill.hasDemand(Contents.POWDER_SNOW, 3), "a powder snow cauldron")
    }
}
