package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockScanCursorTest {
    @Test
    fun `CD-ASG-07 the default 17 x 13 x 17 area takes 8 ticks and no tick checks a 513th position`() {
        val c = BlockScanCursor(-8, -6, -8, 8, 6, 8, budget = 512)
        assertEquals(3_757, c.total)
        val perTick = mutableListOf<Int>()
        while (!c.done) perTick += c.step { _, _, _ -> }
        assertEquals(8, perTick.size)
        assertTrue(perTick.all { it <= 512 })
        assertEquals(3_757, perTick.sum())
    }

    @Test
    fun `CD-ASG-06 radius 32 by 6 needs 108 ticks at 512 per tick`() {
        assertEquals(108, BlockScanCursor(-32, -6, -32, 32, 6, 32, budget = 512).ticksNeeded())
    }

    @Test
    fun `CD-CFG-11 a budget of 256 never checks more than 256 positions in a tick`() {
        val c = BlockScanCursor(-8, -6, -8, 8, 6, 8, budget = 256)
        while (!c.done) assertTrue(c.step { _, _, _ -> } <= 256)
    }

    @Test
    fun `every position is visited exactly once`() {
        val c = BlockScanCursor(0, 0, 0, 2, 1, 2, budget = 4)
        val seen = mutableSetOf<Triple<Int, Int, Int>>()
        while (!c.done) c.step { x, y, z -> assertTrue(seen.add(Triple(x, y, z))) }
        assertEquals(18, seen.size)
    }
}
