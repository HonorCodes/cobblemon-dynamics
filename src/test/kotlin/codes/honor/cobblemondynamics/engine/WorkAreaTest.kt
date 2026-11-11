package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkAreaTest {
    private val pasture = BlockPos(100, 64, 100)
    private val area = WorkArea.of(pasture, EngineSettings.DEFAULT, maxWander = 32)

    @Test
    fun `CD-ASG-08 offsets 8 and 6 are inside, 9 and 7 are outside`() {
        assertTrue(area.contains(pasture.offset(8, 0, 0)))
        assertTrue(area.contains(pasture.offset(0, 0, -8)))
        assertFalse(area.contains(pasture.offset(9, 0, 0)))
        assertTrue(area.contains(pasture.offset(0, 6, 0)))
        assertFalse(area.contains(pasture.offset(0, 7, 0)))
        assertFalse(area.contains(pasture.offset(0, -7, 0)))
    }

    @Test
    fun `CD-CFG-14 a radius above the roam limit acts as the roam limit`() {
        val wide = WorkArea.of(pasture, EngineSettings.DEFAULT.copy(horizontalRadius = 32), maxWander = 20)
        assertEquals(20, wide.horizontal)
        assertTrue(wide.contains(pasture.offset(20, 0, 0)))
        assertFalse(wide.contains(pasture.offset(21, 0, 0)))
    }

    @Test
    fun `CD-MAG-04 the box of the default area holds offset 8 and not offset 9`() {
        val box = WorkArea(BlockPos(0, 64, 0), 8, 6).box()
        assertTrue(box.contains(8.5, 64.5, 0.5))
        assertFalse(box.contains(9.5, 64.5, 0.5))
    }
}
