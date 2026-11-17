package codes.honor.cobblemondynamics.handler.vanilla

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Pump on farmland (CD-MCH-09): the farmland of a 9 × 9 at the height of the bound block, kept at moisture 7. */
class FarmlandMoistureTest {
    private val bound = BlockPos(10, 64, 10)

    /** Farmland by position with its moisture. A cell outside the map holds no farmland. [reads] counts the reads. */
    private class Field(vararg cells: Pair<BlockPos, Int>) {
        val moisture = HashMap(cells.toMap())
        var reads = 0

        fun at(pos: BlockPos): Int? {
            reads++
            return moisture[pos]
        }

        fun wet(pos: BlockPos) {
            moisture[pos] = FarmlandMoisture.WET
        }
    }

    /** One check of [field] that the claim checks allow everywhere, which sets each farmland block that it changes to moisture 7. */
    private fun check(field: Field) {
        FarmlandMoisture.check(bound, field::at, { true }, field::wet)
    }

    /** The demand that a new claim counts at [now], as on the tick that a Pump arrives at its farmland. */
    private fun demandOf(field: Field, now: Long = 0): Boolean = FarmlandMoisture.Wetting(now).hasDemand(bound, now, field::at) { true }

    @Test
    fun `CD-MCH-09 farmland 4 blocks away reads 7 after a check, and farmland 5 blocks away or one block lower stays`() {
        val corner = bound.offset(4, 0, -4)
        val edge = bound.offset(-4, 0, 0)
        val far = bound.offset(5, 0, 0)
        val farCorner = bound.offset(4, 0, 5)
        val lower = bound.offset(1, -1, 0)
        val upper = bound.offset(0, 1, 1)
        val field = Field(bound to 0, corner to 3, edge to 6, far to 0, farCorner to 0, lower to 0, upper to 0)
        check(field)
        assertEquals(7, field.moisture[bound], "the bound farmland")
        assertEquals(7, field.moisture[corner], "farmland 4 blocks away on both axes")
        assertEquals(7, field.moisture[edge], "farmland 4 blocks away on one axis")
        assertEquals(0, field.moisture[far], "farmland 5 blocks away")
        assertEquals(0, field.moisture[farCorner], "farmland 5 blocks away on one axis")
        assertEquals(0, field.moisture[lower], "farmland one block lower")
        assertEquals(0, field.moisture[upper], "farmland one block higher")
    }

    @Test
    fun `CD-MCH-09 the Pump has demand only while farmland of its area is below 7`() {
        val field = Field(bound to 7, bound.offset(-4, 0, 4) to 6, bound.offset(5, 0, 0) to 0, bound.offset(0, -1, 0) to 0)
        assertTrue(demandOf(field), "farmland at moisture 6 in range")
        check(field)
        assertFalse(demandOf(field), "only farmland out of range is dry")
        assertFalse(demandOf(Field()), "an area with no farmland")
    }

    @Test
    fun `CD-MCH-09 a check asks the claim checks for each block that it changes, and a refused block keeps its moisture`() {
        val dry = bound.offset(1, 0, 0)
        val refused = bound.offset(2, 0, 2)
        val field = Field(bound to 7, dry to 2, refused to 1)
        val asked = ArrayList<BlockPos>()
        val changed = ArrayList<BlockPos>()
        val mayChange: (BlockPos) -> Boolean = { pos ->
            asked += pos
            pos != refused
        }
        val result = FarmlandMoisture.check(bound, field::at, mayChange) { pos ->
            field.wet(pos)
            changed += pos
        }
        assertEquals(setOf(dry, refused), asked.toSet(), "the claim checks were asked of wet farmland or of no farmland")
        assertEquals(listOf(dry), changed)
        assertEquals(setOf(refused), result, "the check reports the refused block")
        assertEquals(1, field.moisture[refused], "the refused farmland changed")
        assertEquals(7, field.moisture[dry])
    }

    /**
     * R90: a Pump whose only dry farmland lies in a claim that refuses it gives no demand after its check, so it stands by and its
     * Work Stamina recovers. Each count, every 20 ticks, asks the claim checks of the refused block again, and the first count after
     * they allow it gives demand.
     */
    @Test
    fun `CD-MCH-09 farmland that the claim checks refused gives no demand, and is asked again every 20 ticks`() {
        val theirs = bound.offset(3, 0, -2)
        val field = Field(bound to 7, theirs to 2)
        var allowed = false
        val asked = ArrayList<BlockPos>()
        val mayChange: (BlockPos) -> Boolean = { pos ->
            asked += pos
            pos != theirs || allowed
        }
        val wetting = FarmlandMoisture.Wetting(0)
        assertTrue(wetting.hasDemand(bound, 0, field::at, mayChange), "the dry block before any check")
        wetting.work(bound, 0, field::at, mayChange, field::wet)
        assertEquals(2, field.moisture[theirs], "the refused block changed")
        for (tick in 1L..20L) assertFalse(wetting.hasDemand(bound, tick, field::at, mayChange), "demand at tick $tick")
        asked.clear()
        assertFalse(wetting.hasDemand(bound, 40, field::at, mayChange), "demand at tick 40")
        assertEquals(listOf(theirs), asked, "the count of tick 40 asks the refused block again")
        allowed = true
        assertFalse(wetting.hasDemand(bound, 50, field::at, mayChange), "the count of tick 40 holds until tick 60")
        assertTrue(wetting.hasDemand(bound, 60, field::at, mayChange), "the count of tick 60, which the claim checks allow")
        wetting.since = 60
        wetting.work(bound, 60, field::at, mayChange, field::wet)
        assertEquals(7, field.moisture[theirs], "the allowed block after the check of tick 60")
        assertFalse(wetting.hasDemand(bound, 61, field::at, mayChange))
    }

    /** R90: a dry block that the last check did not refuse gives demand beside a refused one, with no claim check of its own. */
    @Test
    fun `CD-MCH-09 farmland that the claim checks allowed gives demand beside refused farmland`() {
        val theirs = bound.offset(-2, 0, 4)
        val field = Field(bound to 7, theirs to 0)
        val asked = ArrayList<BlockPos>()
        val mayChange: (BlockPos) -> Boolean = { pos ->
            asked += pos
            pos != theirs
        }
        val wetting = FarmlandMoisture.Wetting(0)
        assertTrue(wetting.hasDemand(bound, 0, field::at, mayChange))
        wetting.work(bound, 0, field::at, mayChange, field::wet)
        field.moisture[bound] = 6
        asked.clear()
        assertTrue(wetting.hasDemand(bound, 20, field::at, mayChange), "the dry bound block at tick 20")
        assertEquals(emptyList<BlockPos>(), asked, "the count asked the claim checks")
    }

    /** R90: the demand reads the 81 blocks of the area once in 20 ticks, not on each tick, and a check counts as a read. */
    @Test
    fun `CD-MCH-09 the demand reads the area at most once in 20 ticks`() {
        val field = Field(bound to 3)
        val wetting = FarmlandMoisture.Wetting(100)
        wetting.hasDemand(bound, 100, field::at) { true }
        assertEquals(81, field.reads, "the first count")
        wetting.work(bound, 100, field::at, { true }, field::wet)
        field.reads = 0
        for (tick in 101L..119L) assertFalse(wetting.hasDemand(bound, tick, field::at) { true }, "demand at tick $tick")
        assertEquals(0, field.reads, "ticks 101 to 119 read the area")
        assertFalse(wetting.hasDemand(bound, 120, field::at) { true })
        assertEquals(81, field.reads, "the count of tick 120")
    }

    @Test
    fun `CD-MCH-09 the area is the reach of vanilla's farmland water check, and a check comes every 20 ticks of work`() {
        // FarmBlock.isNearWater reads 4 blocks out on each side, so one water source wets a 9 × 9 (RES11).
        assertEquals(4, FarmlandMoisture.REACH)
        assertEquals(7, FarmlandMoisture.WET)
        assertEquals(20, FarmlandMoisture.CHECK_TICKS)
        val field = Field(bound to 0)
        val wetting = FarmlandMoisture.Wetting(5)
        val checks = (5L..45L).filter { tick ->
            field.moisture[bound] = 0
            wetting.work(bound, tick, field::at, { true }, field::wet)
            field.moisture[bound] == 7
        }
        assertEquals(listOf(5L, 25L, 45L), checks)
    }
}
