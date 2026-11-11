package codes.honor.cobblemondynamics.handler.item

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransferParamsTest {
    @Test
    fun `CD-TLK-03 the defaults are 64 items per second at Lv100 and a 32-block destination range`() =
        assertEquals(TransferParams(64, 32), TransferParams.of(JsonObject()))

    @Test
    fun `CD-BAL-05 a level outside 1 to 100 takes the nearest end of the curve`() {
        val params = TransferParams.of(JsonObject())
        assertEquals(1, params.itemsPerBatch(0))
        assertEquals(64, params.itemsPerBatch(100))
        assertEquals(64, params.itemsPerBatch(150))
    }

    @Test
    fun `CD-CFG-13 a pack's rate applies with no cap beyond it`() {
        val params = TransferParams.of(JsonObject().apply { addProperty("items_per_second_at_level_100", 128) })
        assertEquals(128, params.itemsPerBatch(100))
        assertEquals(0, TransferParams.of(JsonObject().apply { addProperty("items_per_second_at_level_100", 0) }).itemsPerBatch(100))
    }

    @Test
    fun `CD-TLK-12 a destination at 32 blocks on an axis is in reach, and one at 33 is not`() {
        val params = TransferParams.of(JsonObject())
        val pasture = BlockPos(0, 64, 0)
        assertTrue(params.inReach(pasture, BlockPos(32, 64, 0)))
        assertFalse(params.inReach(pasture, BlockPos(33, 64, 0)))
        assertTrue(params.inReach(pasture, BlockPos(-32, 96, 32)))
        assertFalse(params.inReach(pasture, BlockPos(0, 31, 0)))
    }
}
