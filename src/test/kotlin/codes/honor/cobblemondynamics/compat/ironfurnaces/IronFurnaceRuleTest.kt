package codes.honor.cobblemondynamics.compat.ironfurnaces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IronFurnaceRuleTest {
    private fun furnace(burnTime: Int = 0, canSmelt: Boolean = true, furnaceMode: Boolean = true) =
        IronFurnaceFacts(furnaceMode = furnaceMode, canSmelt = canSmelt, burnTime = burnTime)

    /**
     * One tick of the furnace's own fuel step, from `BlockIronFurnaceTileBase.tick` of the 4.3.2 jar: a burning furnace takes one burn
     * tick, and a furnace that is not burning then burns a fuel item while its recipe check passes. Returns the burn time left and
     * whether the tick burned a fuel item.
     */
    private fun ownTick(burnTime: Int): Pair<Int, Boolean> {
        val left = if (burnTime > 0) burnTime - 1 else 0
        return left to (left == 0)
    }

    @Test
    fun `CD-FUEL-07 a furnace in furnace mode whose recipe check passes has demand, and holds 2 burn ticks or more`() {
        assertTrue(IronFurnaceRule.hasDemand(furnace()))
        assertEquals(2, IronFurnaceRule.burnTime(furnace(burnTime = 0)))
        assertEquals(2, IronFurnaceRule.burnTime(furnace(burnTime = 1)))
        // A furnace that burns its own fuel keeps its own value: the worker only ever raises it.
        assertEquals(1_600, IronFurnaceRule.burnTime(furnace(burnTime = 1_600)))
    }

    @Test
    fun `CD-FUEL-07 a Factory, a Generator and a furnace whose recipe check fails get no fuel`() {
        assertFalse(IronFurnaceRule.hasDemand(furnace(furnaceMode = false)), "the Factory or Generator augment")
        assertFalse(IronFurnaceRule.hasDemand(furnace(canSmelt = false)), "a furnace with nothing to smelt")
        assertEquals(0, IronFurnaceRule.burnTime(furnace(furnaceMode = false)))
        assertEquals(0, IronFurnaceRule.burnTime(furnace(canSmelt = false)))
    }

    /** The output half of the furnace's own check: room for the result, within the smaller of the stack size and 64. */
    @Test
    fun `CD-FUEL-07 the output room follows the furnace's own check`() {
        assertTrue(IronFurnaceRule.hasRoom(outputEmpty = true, sameItem = false, outputCount = 0, resultCount = 1, outputMaxStack = 64))
        assertTrue(IronFurnaceRule.hasRoom(outputEmpty = false, sameItem = true, outputCount = 63, resultCount = 1, outputMaxStack = 64))
        assertFalse(IronFurnaceRule.hasRoom(outputEmpty = false, sameItem = true, outputCount = 64, resultCount = 1, outputMaxStack = 64))
        assertFalse(IronFurnaceRule.hasRoom(outputEmpty = false, sameItem = false, outputCount = 1, resultCount = 1, outputMaxStack = 64))
        assertFalse(IronFurnaceRule.hasRoom(outputEmpty = false, sameItem = true, outputCount = 16, resultCount = 1, outputMaxStack = 16))
    }

    /** The engine works after the block entities tick, so each furnace tick starts from the value that the worker left. */
    @Test
    fun `CD-FUEL-13 the worker's burn time keeps the furnace's own fuel step from running`() {
        var burnTime = IronFurnaceRule.burnTime(furnace(burnTime = 0))
        repeat(400) { tick ->
            val (left, fueled) = ownTick(burnTime)
            assertFalse(fueled, "the furnace burned a fuel item on tick $tick")
            burnTime = IronFurnaceRule.burnTime(furnace(burnTime = left))
        }
    }

    @Test
    fun `CD-FUEL-14 the burn time that a worker leaves runs out within 2 ticks`() {
        var burnTime = IronFurnaceRule.burnTime(furnace(burnTime = 0))
        var ticks = 0
        do {
            val (left, fueled) = ownTick(burnTime)
            burnTime = left
            ticks++
        } while (!fueled)
        assertTrue(ticks <= 2, "the furnace burns its own fuel again only after $ticks ticks")
    }
}
