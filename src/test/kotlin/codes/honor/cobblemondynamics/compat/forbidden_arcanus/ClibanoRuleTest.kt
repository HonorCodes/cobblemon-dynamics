package codes.honor.cobblemondynamics.compat.forbidden_arcanus

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClibanoRuleTest {
    /**
     * One tick of the Clibano's own fuel step, from `ClibanoMainBlockEntity.serverTick` of the 2.6.1 jar: a lit Clibano takes one burn
     * tick, and one that is not lit burns a fuel item while it can smelt. Returns the burn time left and whether the tick burned fuel.
     */
    private fun ownTick(burnTime: Int): Pair<Int, Boolean> = if (burnTime > 0) (burnTime - 1) to false else 0 to true

    @Test
    fun `CD-FUEL-10 a worked Clibano holds 2 burn ticks or more with its burn duration equal`() {
        assertEquals(ClibanoBurn(2, 2), ClibanoRule.keep(ClibanoBurn(0, 0)))
        assertEquals(ClibanoBurn(2, 2), ClibanoRule.keep(ClibanoBurn(1, 2)))
        // Its own fuel keeps its own values: the worker only ever raises the burn time.
        assertEquals(ClibanoBurn(1_200, 1_600), ClibanoRule.keep(ClibanoBurn(1_200, 1_600)))
    }

    /** The mixin keeps the burn time at the start of the Clibano's own tick, before its fuel step reads it. */
    @Test
    fun `CD-FUEL-13 the kept burn time keeps the Clibano's own fuel step from running`() {
        var burnTime = 0
        repeat(400) { tick ->
            val (left, fueled) = ownTick(ClibanoRule.keep(ClibanoBurn(burnTime, 0)).time)
            assertFalse(fueled, "the Clibano burned a fuel item on tick $tick")
            burnTime = left
        }
    }

    @Test
    fun `CD-FUEL-14 the burn time that a worker leaves runs out within 2 ticks`() {
        var burnTime = ownTick(ClibanoRule.keep(ClibanoBurn(0, 0)).time).first
        var ticks = 0
        do {
            val (left, fueled) = ownTick(burnTime)
            burnTime = left
            ticks++
        } while (!fueled)
        assertTrue(ticks <= 2, "the Clibano burns its own fuel again only after $ticks ticks")
    }

    /** The main block sits in the middle of the 3 by 3 by 3 Clibano, so the whole cube is the target and no frame block hides it. */
    @Test
    fun `CD-FUEL-10 the target is the cube around the main block`() {
        val main = BlockPos(10, 64, -3)
        assertTrue(ClibanoRule.isPart(main, main))
        assertTrue(ClibanoRule.isPart(main, main.offset(1, 1, 1)))
        assertTrue(ClibanoRule.isPart(main, main.offset(-1, -1, 0)))
        assertFalse(ClibanoRule.isPart(main, main.offset(2, 0, 0)))
        assertFalse(ClibanoRule.isPart(main, main.offset(0, -2, 1)))
    }
}
