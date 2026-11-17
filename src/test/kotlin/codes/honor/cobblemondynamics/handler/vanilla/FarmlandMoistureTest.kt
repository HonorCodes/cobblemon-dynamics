package codes.honor.cobblemondynamics.handler.vanilla

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Pump on farmland (CD-MCH-09): the farmland of a 9 × 9 at the height of the bound block, kept at moisture 7. */
class FarmlandMoistureTest {
    private val bound = BlockPos(10, 64, 10)

    /** Farmland by position with its moisture. A cell outside the map holds no farmland. */
    private class Field(vararg cells: Pair<BlockPos, Int>) {
        val moisture = HashMap(cells.toMap())

        fun at(pos: BlockPos): Int? = moisture[pos]
    }

    /** One check of [field], which sets each farmland block that it changes to moisture 7. Returns the blocks that it changed. */
    private fun check(field: Field, mayChange: (BlockPos) -> Boolean = { true }): List<BlockPos> {
        val changed = ArrayList<BlockPos>()
        FarmlandMoisture.check(bound, field::at, mayChange) { pos ->
            field.moisture[pos] = FarmlandMoisture.WET
            changed += pos
        }
        return changed
    }

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
        assertEquals(7, field.at(bound), "the bound farmland")
        assertEquals(7, field.at(corner), "farmland 4 blocks away on both axes")
        assertEquals(7, field.at(edge), "farmland 4 blocks away on one axis")
        assertEquals(0, field.at(far), "farmland 5 blocks away")
        assertEquals(0, field.at(farCorner), "farmland 5 blocks away on one axis")
        assertEquals(0, field.at(lower), "farmland one block lower")
        assertEquals(0, field.at(upper), "farmland one block higher")
    }

    @Test
    fun `CD-MCH-09 the Pump has demand only while farmland of its area is below 7`() {
        val field = Field(bound to 7, bound.offset(-4, 0, 4) to 6, bound.offset(5, 0, 0) to 0, bound.offset(0, -1, 0) to 0)
        assertTrue(FarmlandMoisture.hasDemand(bound, field::at), "farmland at moisture 6 in range")
        check(field)
        assertFalse(FarmlandMoisture.hasDemand(bound, field::at), "only farmland out of range is dry")
        assertFalse(FarmlandMoisture.hasDemand(bound) { null }, "an area with no farmland")
    }

    @Test
    fun `CD-MCH-09 a check asks the claim checks for each block that it changes, and a refused block keeps its moisture`() {
        val dry = bound.offset(1, 0, 0)
        val refused = bound.offset(2, 0, 2)
        val field = Field(bound to 7, dry to 2, refused to 1)
        val asked = ArrayList<BlockPos>()
        val changed = check(field) { pos ->
            asked += pos
            pos != refused
        }
        assertEquals(setOf(dry, refused), asked.toSet(), "the claim checks were asked of wet farmland or of no farmland")
        assertEquals(listOf(dry), changed)
        assertEquals(1, field.at(refused), "the refused farmland changed")
        assertEquals(7, field.at(dry))
    }

    @Test
    fun `CD-MCH-09 the area is the reach of vanilla's farmland water check, and a check comes every 20 ticks of work`() {
        // FarmBlock.isNearWater reads 4 blocks out on each side, so one water source wets a 9 × 9 (RES11).
        assertEquals(4, FarmlandMoisture.REACH)
        assertEquals(7, FarmlandMoisture.WET)
        assertEquals(20, FarmlandMoisture.CHECK_TICKS)
    }
}
