package codes.honor.cobblemondynamics.bed

import codes.honor.cobblemondynamics.claim.TargetKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BedSelectorTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val from = Vec3(0.5, 64.0, 0.5)

    /** A bed whose sleep point lies [dx] and [dz] blocks from [from], at the same height. */
    private fun bed(dx: Double, dz: Double, tier: BedTier = BedTier.HAY) = BedCandidate(
        TargetKey.block(dim, BlockPos.containing(from.x + dx, from.y, from.z + dz)),
        Vec3(from.x + dx, from.y, from.z + dz),
        tier.tier,
    )

    @Test
    fun `CD-BED-05 beds at 3 and 5 blocks give the 3-block bed`() {
        val near = bed(3.0, 0.0)
        val far = bed(-5.0, 0.0)
        assertEquals(near, BedSelector.pick(from, listOf(far, near)) { true })
        assertEquals(near, BedSelector.pick(from, listOf(near, far)) { true })
    }

    @Test
    fun `CD-BED-05 the nearest bed wins over a higher tier`() {
        val hay = bed(3.0, 0.0, BedTier.HAY)
        val starlit = bed(0.0, 5.0, BedTier.STARLIT)
        assertEquals(hay, BedSelector.pick(from, listOf(starlit, hay)) { true })
    }

    @Test
    fun `CD-BED-06 a Hay Bed and a Plush Bed both 4_0 blocks away give the Plush Bed`() {
        val hay = bed(4.0, 0.0, BedTier.HAY)
        val plush = bed(0.0, 4.0, BedTier.PLUSH)
        assertEquals(plush, BedSelector.pick(from, listOf(hay, plush)) { true })
        assertEquals(plush, BedSelector.pick(from, listOf(plush, hay)) { true })
    }

    @Test
    fun `CD-BED-05 a claimed bed is skipped for the next nearest`() {
        val near = bed(3.0, 0.0)
        val far = bed(-5.0, 0.0)
        assertEquals(far, BedSelector.pick(from, listOf(near, far)) { it != near.key })
    }

    @Test
    fun `CD-BED-08 with no free bed there is none`() {
        assertNull(BedSelector.pick(from, listOf(bed(3.0, 0.0))) { false })
        assertNull(BedSelector.pick(from, emptyList()) { true })
    }

    @Test
    fun `a tie of distance and tier goes to the lowest position`() {
        val east = bed(4.0, 0.0)
        val west = bed(-4.0, 0.0)
        val lowest = listOf(east, west).minBy { (it.key as TargetKey.Block).pos }
        assertEquals(lowest, BedSelector.pick(from, listOf(east, west)) { true })
        assertEquals(lowest, BedSelector.pick(from, listOf(west, east)) { true })
    }

    @Test
    fun `CD-BED-09 a sleeper reaches its bed within 0_5 blocks, or within half its width when it is wider`() {
        val point = BedCandidate.sleepPoint(BlockPos(10, 64, 10))
        assertEquals(Vec3(10.5, 64.0 + BedBlock.HEIGHT, 10.5), point)
        assertTrue(BedSelector.isAt(Vec3(10.9, point.y, 10.5), 0.5, point))
        assertFalse(BedSelector.isAt(Vec3(11.1, point.y, 10.5), 0.5, point))
        // A Wailord is 8 blocks wide.
        assertTrue(BedSelector.isAt(Vec3(14.4, 64.0, 10.5), 8.0, point))
        assertFalse(BedSelector.isAt(Vec3(14.6, 64.0, 10.5), 8.0, point))
        assertFalse(BedSelector.isAt(Vec3(10.5, point.y + 1.5, 10.5), 0.5, point))
    }
}
