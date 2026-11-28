package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceGridTest {
    private class TestWorld {
        private val cells = HashMap<BlockPos, SurfaceCell>()

        fun put(x: Int, y: Int, z: Int, cell: SurfaceCell) {
            cells[BlockPos(x, y, z)] = cell
        }

        fun get(x: Int, y: Int, z: Int): SurfaceCell = cells[BlockPos(x, y, z)] ?: SurfaceCell.AIR

        fun topY(x: Int, y: Int, z: Int, headroom: Double = SurfaceGrid.DEFAULT_HEADROOM): Double? =
            SurfaceGrid.supportTopY(x, y, z, headroom, ::get)
    }

    private fun fullBlock() = SurfaceCell(
        isAir = false,
        isSolid = true,
        sturdyTop = true,
        collisionMinY = 0.0,
        collisionMaxY = 1.0,
    )

    private fun thinBlock(maxY: Double, isSolid: Boolean = false) = SurfaceCell(
        isAir = false,
        isSolid = isSolid,
        collisionMinY = 0.0,
        collisionMaxY = maxY,
        thinSurface = true,
    )

    private fun partialFloor(maxY: Double, isSolid: Boolean = true) = SurfaceCell(
        isAir = false,
        isSolid = isSolid,
        collisionMinY = 0.0,
        collisionMaxY = maxY,
        partialFloor = true,
    )

    @Test
    fun `CD-NAV-01 resolves supportTopY across full, thin, partial, leaf and Cobblemon placeable floors`() {
        val w = TestWorld()
        w.put(0, 1, 0, fullBlock())
        assertEquals(2.0, w.topY(0, 2, 0)!!, 1e-6)

        w.put(1, 1, 0, fullBlock())
        w.put(1, 2, 0, thinBlock(0.0625))
        assertEquals(2.0625, w.topY(1, 2, 0)!!, 1e-6)
        assertNull(w.topY(1, 3, 0))

        w.put(2, 1, 0, fullBlock())
        w.put(2, 2, 0, thinBlock(0.0625))
        assertEquals(2.0625, w.topY(2, 2, 0)!!, 1e-6)

        w.put(3, 1, 0, fullBlock())
        w.put(3, 2, 0, thinBlock(0.375, isSolid = true))
        assertEquals(2.375, w.topY(3, 2, 0)!!, 1e-6)

        w.put(4, 1, 0, partialFloor(0.9375))
        assertEquals(1.9375, w.topY(4, 2, 0)!!, 1e-6)

        w.put(5, 1, 0, partialFloor(0.875))
        assertEquals(1.875, w.topY(5, 2, 0)!!, 1e-6)

        w.put(6, 1, 0, fullBlock())
        w.put(
            6,
            2,
            0,
            SurfaceCell(isAir = false, isSolid = true, collisionMinY = 0.0, collisionMaxY = 0.5, partialFloor = true, thinSurface = true),
        )
        assertEquals(2.5, w.topY(6, 2, 0)!!, 1e-6)
        assertNull(w.topY(6, 3, 0))

        w.put(
            7,
            1,
            0,
            SurfaceCell(isAir = false, isSolid = true, collisionMinY = 0.0, collisionMaxY = 0.5, partialFloor = true, thinSurface = true),
        )
        assertEquals(1.5, w.topY(7, 2, 0)!!, 1e-6)

        w.put(8, 1, 0, partialFloor(1.0))
        assertEquals(2.0, w.topY(8, 2, 0)!!, 1e-6)

        w.put(9, 1, 0, fullBlock())
        w.put(9, 2, 0, thinBlock(0.25))
        assertEquals(2.25, w.topY(9, 2, 0)!!, 1e-6)
    }

    @Test
    fun `CD-NAV-01 rejects fluids, hazards, signs and insufficient headroom`() {
        val w = TestWorld()
        w.put(0, 1, 0, fullBlock())
        w.put(0, 2, 0, SurfaceCell(isAir = false, hasFluid = true))
        assertNull(w.topY(0, 2, 0))

        w.put(1, 1, 0, fullBlock().copy(hazard = true))
        assertNull(w.topY(1, 2, 0))

        w.put(2, 1, 0, fullBlock())
        w.put(2, 2, 0, SurfaceCell(isAir = false, hazard = true))
        assertNull(w.topY(2, 2, 0))

        w.put(3, 1, 0, fullBlock())
        w.put(3, 2, 0, SurfaceCell(isAir = false, isSolid = true))
        assertNull(w.topY(3, 2, 0))

        w.put(4, 1, 0, fullBlock())
        w.put(4, 2, 0, thinBlock(0.0625))
        w.put(4, 4, 0, fullBlock())
        assertNull(w.topY(4, 2, 0, headroom = 2.0))
        assertEquals(2.0625, w.topY(4, 2, 0, headroom = 1.2)!!, 1e-6)

        w.put(5, 1, 0, fullBlock())
        w.put(5, 3, 0, SurfaceCell(isAir = false, isSolid = true, collisionMinY = 0.5, collisionMaxY = 1.0))
        assertNull(w.topY(5, 2, 0, headroom = 2.0))

        val waterloggedSlab = SurfaceCell(
            isAir = false,
            isSolid = true,
            collisionMinY = 0.0,
            collisionMaxY = 0.5,
            hasFluid = true,
            partialFloor = true,
            thinSurface = true,
        )
        w.put(6, 1, 0, fullBlock())
        w.put(6, 2, 0, waterloggedSlab)
        assertNull(w.topY(6, 2, 0))
        assertNull(w.topY(6, 3, 0))

        w.put(7, 1, 0, waterloggedSlab)
        assertNull(w.topY(7, 2, 0))

        w.put(8, 1, 0, partialFloor(1.0).copy(hasFluid = true))
        assertNull(w.topY(8, 2, 0))
    }

    @Test
    fun `CD-NAV-02 body boxes and snapY sit at supportTopY plus 0_001 without colliding with floor`() {
        val spot = BlockPos(6, 2, 10)
        for ((topY, expectedSnap) in listOf(2.0625 to 2.0635, 1.9375 to 1.9385, 2.5 to 2.501)) {
            assertEquals(expectedSnap, Arrival.snapY(topY), 1e-6)
            val box = WorkerBody.boxes(spot, 0.8, 1.4, topY).single()
            assertTrue(box.minY > topY, "box minY ${box.minY} must stay strictly above supportTopY $topY")
            assertEquals(topY + 0.002, box.minY, 1e-6)
            assertEquals(topY + 1.4, box.maxY, 1e-6)
        }
    }
}
