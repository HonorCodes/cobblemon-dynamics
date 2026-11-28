package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.AreaBoundsPayload
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkAreaOverlayStateTest {
    private val pos = BlockPos(20, 64, -10)

    @Test
    fun `CD-SHF-05 overlay persists across screen close updates live bounds and clears on toggle off or inactive payload`() {
        val overlay = WorkAreaOverlayState()
        assertTrue(overlay.toggle(pos))
        assertTrue(overlay.isWatching(pos))

        overlay.onBounds(AreaBoundsPayload(pos, active = true, 16, 58, -14, 24, 70, -6))
        assertEquals(1, overlay.activeBoxes().size)
        assertEquals(16.0, overlay.boxAt(pos)?.minX)
        assertEquals(25.0, overlay.boxAt(pos)?.maxX)

        overlay.onBounds(AreaBoundsPayload(pos, active = true, 8, 58, -22, 32, 70, 2))
        assertEquals(8.0, overlay.boxAt(pos)?.minX)
        assertEquals(33.0, overlay.boxAt(pos)?.maxX)

        assertFalse(overlay.toggle(pos))
        assertFalse(overlay.isWatching(pos))
        assertTrue(overlay.activeBoxes().isEmpty())

        assertTrue(overlay.toggle(pos))
        overlay.onBounds(AreaBoundsPayload(pos, active = true, 12, 58, -18, 28, 70, -2))
        assertEquals(1, overlay.activeBoxes().size)
        overlay.onBounds(AreaBoundsPayload.inactive(pos))
        assertFalse(overlay.isWatching(pos))
        assertTrue(overlay.activeBoxes().isEmpty())
    }

    @Test
    fun `CD-SHF-05 overlay prunes broken pasture in loaded chunk preserves unloaded chunk and clears on level reset`() {
        val overlay = WorkAreaOverlayState()
        val unloadedPos = BlockPos(200, 64, 200)
        overlay.toggle(pos)
        overlay.toggle(unloadedPos)
        overlay.onBounds(AreaBoundsPayload(pos, active = true, 16, 58, -14, 24, 70, -6))
        overlay.onBounds(AreaBoundsPayload(unloadedPos, active = true, 196, 58, 196, 204, 70, 204))

        val removed = overlay.pruneMissingPastures(
            isChunkLoaded = { it == pos },
            isPastureBlock = { false },
        )
        assertEquals(listOf(pos), removed)
        assertFalse(overlay.isWatching(pos))
        assertTrue(overlay.isWatching(unloadedPos))
        assertEquals(1, overlay.activeBoxes().size)

        overlay.clear()
        assertFalse(overlay.isWatching(unloadedPos))
        assertTrue(overlay.activeBoxes().isEmpty())
    }

    @Test
    fun `CD-SHF-05 rapid off-on toggle and 17th-watch eviction inactive payload keep client overlay synchronized`() {
        val overlay = WorkAreaOverlayState()
        val zeroPos = BlockPos.ZERO
        assertTrue(overlay.toggle(zeroPos))
        overlay.onBounds(AreaBoundsPayload(zeroPos, active = true, -8, -6, -8, 8, 6, 8))
        assertFalse(overlay.toggle(zeroPos))
        assertTrue(overlay.toggle(zeroPos))
        overlay.onBounds(AreaBoundsPayload(zeroPos, active = true, -8, -6, -8, 8, 6, 8))
        assertTrue(overlay.isWatching(zeroPos))
        assertEquals(1, overlay.activeBoxes().size)

        overlay.clear()
        val positions = (0..16).map { BlockPos(it * 16, 64, 0) }
        for (i in 0 until 16) {
            val p = positions[i]
            assertTrue(overlay.toggle(p))
            overlay.onBounds(AreaBoundsPayload(p, active = true, p.x - 4, 58, -4, p.x + 4, 70, 4))
        }
        assertEquals(16, overlay.activeBoxes().size)

        val seventeenth = positions[16]
        assertTrue(overlay.toggle(seventeenth))
        overlay.onBounds(AreaBoundsPayload.inactive(positions[0]))
        overlay.onBounds(AreaBoundsPayload(seventeenth, active = true, seventeenth.x - 4, 58, -4, seventeenth.x + 4, 70, 4))

        assertFalse(overlay.isWatching(positions[0]))
        assertEquals(null, overlay.boxAt(positions[0]))
        assertTrue(overlay.isWatching(seventeenth))
        assertEquals(16, overlay.activeBoxes().size)
    }
}
