package codes.honor.cobblemondynamics.compat.ironfurnaces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The recipe lookup of one furnace, with a recipe type and a cached check that stand in for Minecraft's. */
class FurnaceCheckTest {
    /** R88: a furnace keeps its own cached check, as the furnace itself does, so two furnaces never evict each other's recipe. */
    @Test
    fun `CD-FUEL-07 each furnace keeps a cached check of its own, and a new one for a new recipe type`() {
        var made = 0
        val smelting = FurnaceCheck.forType(null, "smelting") { "check ${++made}" }
        assertSame(smelting, FurnaceCheck.forType(smelting, "smelting") { "check ${++made}" }, "the same type made a new check")
        val blasting = FurnaceCheck.forType(smelting, "blasting") { "check ${++made}" }
        assertNotSame(smelting, blasting, "an augment that changed the recipe type kept the old check")
        assertEquals("blasting", blasting.type)
        assertEquals(2, made)
    }

    /** R88: the demand and the work of one tick read one recipe lookup. */
    @Test
    fun `CD-FUEL-07 the recipe check runs once per tick`() {
        val check = FurnaceCheck("smelting", "check")
        val runs = mutableListOf<Long>()
        assertTrue(check.canSmelt(now = 100) { runs.add(100) })
        assertTrue(check.canSmelt(now = 100) { !runs.add(100) }, "the second read of a tick ran the lookup again")
        assertEquals(listOf(100L), runs)
        assertFalse(check.canSmelt(now = 101) { !runs.add(101) }, "the next tick kept the last tick's result")
        assertEquals(listOf(100L, 101L), runs)
    }
}
