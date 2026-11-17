package codes.honor.cobblemondynamics.compat.cookingforblockheads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CfbOvenRuleTest {
    private val empty = List(CfbOvenRule.PROCESSING_SLOTS) { false }
    private val idle = List(CfbOvenRule.PROCESSING_SLOTS) { 0 }

    /**
     * One tick of the oven's own fuel step, from `OvenBlockEntity.serverTick` of the 21.1.24 jar: it takes one burn tick, and at 0 it
     * burns a fuel item while a slot cooks. Returns the burn time left and whether the tick burned a fuel item.
     */
    private fun ownTick(burnTime: Int): Pair<Int, Boolean> {
        val left = if (burnTime > 0) burnTime - 1 else 0
        return left to (left == 0)
    }

    @Test
    fun `CD-FUEL-09 a slot cooks while it holds an item that is not done`() {
        assertFalse(CfbOvenRule.cooks(empty, idle), "an empty oven")
        val one = empty.toMutableList().also { it[4] = true }
        assertTrue(CfbOvenRule.cooks(one, idle), "a slot that holds raw food")
        val done = idle.toMutableList().also { it[4] = CfbOvenRule.COOKED }
        assertFalse(CfbOvenRule.cooks(one, done), "a slot whose food is cooked and waits for room in the output")
        // A cook time without an item, as the oven keeps after a slot empties, cooks nothing.
        assertFalse(CfbOvenRule.cooks(empty, idle.toMutableList().also { it[0] = 120 }))
    }

    @Test
    fun `CD-FUEL-09 a cooking oven holds 2 burn ticks or more with its item burn time equal`() {
        assertEquals(OvenBurn(2, 2), CfbOvenRule.burn(OvenBurn(0, 0), cooks = true))
        assertEquals(OvenBurn(2, 2), CfbOvenRule.burn(OvenBurn(1, 528), cooks = true))
        // The oven's own fuel keeps its own values, and an oven with nothing to cook gets nothing.
        assertEquals(OvenBurn(400, 528), CfbOvenRule.burn(OvenBurn(400, 528), cooks = true))
        assertEquals(OvenBurn(0, 0), CfbOvenRule.burn(OvenBurn(0, 0), cooks = false))
    }

    @Test
    fun `CD-FUEL-13 the worker's burn time keeps the oven's own fuel step from running`() {
        var burn = CfbOvenRule.burn(OvenBurn(0, 0), cooks = true)
        repeat(400) { tick ->
            val (left, fueled) = ownTick(burn.burnTime)
            assertFalse(fueled, "the oven burned a fuel item on tick $tick")
            burn = CfbOvenRule.burn(OvenBurn(left, burn.itemBurnTime), cooks = true)
        }
    }

    @Test
    fun `CD-FUEL-14 the burn time that a worker leaves runs out within 2 ticks`() {
        var burnTime = CfbOvenRule.burn(OvenBurn(0, 0), cooks = true).burnTime
        var ticks = 0
        do {
            val (left, fueled) = ownTick(burnTime)
            burnTime = left
            ticks++
        } while (!fueled)
        assertTrue(ticks <= 2, "the oven burns its own fuel again only after $ticks ticks")
    }
}
