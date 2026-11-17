package codes.honor.cobblemondynamics.compat.mekanismgenerators

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindBonusTest {
    /** Mekanism Generators' defaults: 60 J/t at the lowest height, 480 J/t at the highest (BM12). */
    private val top = WindBonus.top(min = 60, max = 480)

    @Test
    fun `CD-AIR-02 m 2_03 with M 8 gives 8 at level 100 and 5_015 at level 50, and m 0 gives 0`() {
        assertEquals(8.0, top, EPSILON)
        assertEquals(8.0, WindBonus.multiplier(own = 2.03, top = 8.0, level = 100), EPSILON)
        assertEquals(5.015, WindBonus.multiplier(own = 2.03, top = 8.0, level = 50), EPSILON)
        // No sky, or a fluid above the generator: Mekanism's sky rule holds at every level.
        for (level in listOf(1, 50, 100)) assertEquals(0.0, WindBonus.multiplier(own = 0.0, top = 8.0, level = level), EPSILON)
    }

    @Test
    fun `CD-AIR-02 a level 100 worker makes 480 J per tick at the config maximum`() {
        assertEquals(480.0, 60 * WindBonus.multiplier(own = 2.03, top = top, level = 100), EPSILON)
    }

    @Test
    fun `CD-BAL-14 no level pushes a generator past windGenerationMax`() {
        for (own in listOf(0.5, 1.0, 2.03, 7.9, 8.0)) {
            for (level in -5..150) {
                val boosted = WindBonus.multiplier(own, top, level)
                assertTrue(boosted <= top + EPSILON, "m $own at level $level gives $boosted")
                assertTrue(boosted >= own - EPSILON, "m $own at level $level gives $boosted")
            }
        }
        // A config with no lowest rate gives no bonus, as Mekanism divides by it.
        assertEquals(0.0, WindBonus.top(min = 0, max = 480), EPSILON)
        assertEquals(2.03, WindBonus.multiplier(own = 2.03, top = 0.0, level = 100), EPSILON)
    }

    @Test
    fun `CD-AIR-03 the bonus holds while a worker renewed it within 2 ticks, and the next update after that gives m`() {
        val grant = WindGrant(level = 100, until = WindBonus.until(now = 100))
        assertEquals(8.0, WindBonus.applied(own = 2.03, top = top, grant = grant, now = 100), EPSILON)
        assertEquals(8.0, WindBonus.applied(own = 2.03, top = top, grant = grant, now = 101), EPSILON)
        assertEquals(2.03, WindBonus.applied(own = 2.03, top = top, grant = grant, now = 102), EPSILON)
        assertEquals(2.03, WindBonus.applied(own = 2.03, top = top, grant = null, now = 100), EPSILON)
    }

    @Test
    fun `CD-AIR-04 the canonical of a bounding block is its main position`() {
        val main = BlockPos(5, 80, 5)
        assertEquals(main, WindBonus.canonical(BlockPos(5, 83, 5), main))
        assertEquals(main, WindBonus.canonical(main, main))
        assertEquals(BlockPos(5, 83, 5), WindBonus.canonical(BlockPos(5, 83, 5), null))
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
