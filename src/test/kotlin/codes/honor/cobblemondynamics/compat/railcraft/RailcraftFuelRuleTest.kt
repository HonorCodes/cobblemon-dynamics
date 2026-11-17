package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.railcraft.RailcraftFuelRule.Kind
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
    fun `CD-FUEL-04 the level gate of 50 holds for the blast furnace only`() {
        val minLevel = RailcraftFuelParams.of(BuiltInRoles.parse("stoker_railcraft").params).blastFurnaceMinLevel
        assertEquals(50, minLevel)
        assertFalse(RailcraftFuelRule.admits(Kind.BLAST_FURNACE, level = 49, minLevel = minLevel))
        assertTrue(RailcraftFuelRule.admits(Kind.BLAST_FURNACE, level = 50, minLevel = minLevel))
        assertTrue(RailcraftFuelRule.admits(Kind.FIREBOX, level = 1, minLevel = minLevel))
    }

    @Test
    fun `CD-FUEL-05 one worker adds at most 1_0 burn tick per tick to a firebox's boiler`() {
        val cap = RailcraftFuelParams.of(BuiltInRoles.parse("stoker_railcraft").params).fireboxBurnTicksPerTick
        assertEquals(1.0, cap)
        assertEquals(RailcraftFuelParams(50, 1.0), RailcraftFuelParams.of(JsonObject()))
        // 36 high-pressure tanks need about 41 burn ticks per tick, far beyond one worker (BM14).
        assertEquals(1.0f, RailcraftFuelRule.fireboxGift(burnTime = 0f, need = 331f, cap = cap))
        assertEquals(0.25f, RailcraftFuelRule.fireboxGift(burnTime = 10.5f, need = 10.75f, cap = cap))
    }

    @Test
    fun `CD-FUEL-05 a boiler at its fuel need gets none`() {
        assertEquals(0f, RailcraftFuelRule.fireboxGift(burnTime = 10.75f, need = 10.75f, cap = 1.0))
        assertEquals(0f, RailcraftFuelRule.fireboxGift(burnTime = 40f, need = 10.75f, cap = 1.0))
        assertFalse(RailcraftFuelRule.fireboxDemand(formed = true, hasWater = false), "an empty boiler needs no fuel")
        assertFalse(RailcraftFuelRule.fireboxDemand(formed = false, hasWater = true))
        assertTrue(RailcraftFuelRule.fireboxDemand(formed = true, hasWater = true))
    }

    /**
     * A boiler burns its fuel once per cycle of 16 ticks at low pressure: it takes fuel items only while its burn time is below the
     * cycle's need. One worker covers a 1-tank boiler, which needs about 10.75 per cycle, so that boiler never takes its own fuel.
     */
    @Test
    fun `CD-FUEL-13 a boiler that its workers cover never reaches its own fuel step`() {
        val need = 10.75f
        var burn = 0f
        repeat(16) { burn += RailcraftFuelRule.fireboxGift(burn, need, cap = 1.0) }
        assertTrue(burn >= need, "a cycle ends with $burn of $need")
        assertEquals(need, burn)
    }

    @Test
    fun `CD-FUEL-05 each firebox block is its own target, and every brick of a blast furnace keys its master`() {
        val master = BlockPos(0, 64, 0)
        val firebox = BlockPos(1, 64, 0)
        assertEquals(firebox, RailcraftFuelRule.canonical(Kind.FIREBOX, firebox, master))
        assertEquals(master, RailcraftFuelRule.canonical(Kind.BLAST_FURNACE, BlockPos(1, 66, 1), master))
        assertEquals(firebox, RailcraftFuelRule.canonical(Kind.BLAST_FURNACE, firebox, null))
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
