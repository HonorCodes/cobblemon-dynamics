package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.railcraft.RailcraftFuelRule.Slot
import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RailcraftFuelRuleTest {
    /**
     * One tick of Railcraft's blast furnace: at its process step, every 16 ticks, it takes a fuel item while `burnTime` is 10 or less,
     * and then it burns 5 of `burnTime` (RES10). True when the tick took a fuel item.
     */
    private fun furnaceTick(burnTime: Int, processStep: Boolean): Pair<Boolean, Int> {
        val tookFuel = processStep && burnTime <= 10
        return tookFuel to maxOf(0, burnTime - 5)
    }

    @Test
    fun `CD-FUEL-04 CD-FUEL-13 the margin of 16 keeps the furnace from its fuel step`() {
        assertEquals(16, RailcraftFuelRule.FURNACE_MARGIN)
        assertEquals(16, RailcraftFuelRule.furnaceBurn(0))
        assertEquals(16, RailcraftFuelRule.furnaceBurn(11))
        // Coke that the furnace took before the worker came keeps its own value.
        assertEquals(3_200, RailcraftFuelRule.furnaceBurn(3_200))
        var burn = 0
        for (tick in 0 until 160) {
            burn = RailcraftFuelRule.furnaceBurn(burn)
            val (tookFuel, left) = furnaceTick(burn, processStep = tick % 16 == 0)
            assertFalse(tookFuel, "tick $tick took a fuel item")
            burn = left
        }
    }

    @Test
    fun `CD-FUEL-14 a stopped blast furnace is back on its own fuel within 2 ticks`() {
        var burn = RailcraftFuelRule.furnaceBurn(0)
        repeat(2) { burn = furnaceTick(burn, processStep = false).second }
        assertTrue(burn <= 10, "after 2 ticks the furnace holds $burn, so its next process step does not take its own fuel")
    }

    @Test
    fun `CD-FUEL-04 the blast furnace takes a worker from level 50`() {
        val minLevel = RailcraftFuelParams.of(BuiltInRoles.parse("stoker_railcraft").params).blastFurnaceMinLevel
        assertEquals(50, minLevel)
        assertEquals(RailcraftFuelParams(50), RailcraftFuelParams.of(JsonObject()))
        assertFalse(RailcraftFuelRule.admits(level = 49, minLevel = minLevel))
        assertTrue(RailcraftFuelRule.admits(level = 50, minLevel = minLevel))
    }

    @Test
    fun `CD-CLM-04 every brick of a blast furnace keys its master`() {
        val master = BlockPos(0, 64, 0)
        assertEquals(master, RailcraftFuelRule.canonical(BlockPos(1, 66, 1), master))
        assertEquals(BlockPos(1, 64, 0), RailcraftFuelRule.canonical(BlockPos(1, 64, 0), null))
    }

    @Test
    fun `CD-FUEL-04 the furnace's recipe fits only with room for its result and its slag`() {
        val empty = Slot(empty = true, sameItem = false, count = 0, adding = 1, limit = 64)
        assertTrue(RailcraftFuelRule.fits(listOf(empty, empty)))
        assertTrue(RailcraftFuelRule.fits(listOf(Slot(false, true, 63, 1, 64), empty)))
        assertFalse(RailcraftFuelRule.fits(listOf(Slot(false, true, 64, 1, 64), empty)))
        assertFalse(RailcraftFuelRule.fits(listOf(empty, Slot(false, false, 1, 1, 64))))
    }
}
