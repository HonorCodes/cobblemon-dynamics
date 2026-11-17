package codes.honor.cobblemondynamics.compat.immersiveengineering

import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IeWindmillMathTest {
    private val clear = IeWindmillMath.weather(raining = false, thundering = false)
    private val rain = IeWindmillMath.weather(raining = true, thundering = false)
    private val storm = IeWindmillMath.weather(raining = true, thundering = true)

    @Test
    fun `CD-AIR-05 IE's weather factor is 0_495 clear, 0_66 in rain and 1_0 in a thunderstorm`() {
        assertEquals(0.495, clear, EPSILON)
        assertEquals(0.66, rain, EPSILON)
        assertEquals(1.0, storm, EPSILON)
    }

    @Test
    fun `CD-AIR-05 clear weather gives 1_0 at level 100 and 0_495 at level 0, and a thunderstorm gives 1_0 at any level`() {
        assertEquals(1.0, IeWindmillMath.factor(clear, 100), EPSILON)
        assertEquals(0.495, IeWindmillMath.factor(clear, 0), EPSILON)
        assertEquals(0.83, IeWindmillMath.factor(rain, 50), EPSILON)
        for (level in listOf(0, 1, 50, 100)) assertEquals(1.0, IeWindmillMath.factor(storm, level), EPSILON)
    }

    @Test
    fun `CD-AIR-05 the hook scales IE's own speed by the new factor over IE's, so sails and airflow stay IE's`() {
        // 8 sails in clear weather turn a dynamo at 35.6 FE/t, and level 100 gives the thunderstorm's 71.8 (BM11).
        val clearSpeed = 35.6 / 2_400
        assertEquals(clearSpeed / 0.495, IeWindmillMath.speed(clearSpeed, raining = false, thundering = false, level = 100), EPSILON)
        assertEquals(clearSpeed, IeWindmillMath.speed(clearSpeed, raining = false, thundering = false, level = 0), EPSILON)
        assertEquals(0.0, IeWindmillMath.speed(0.0, raining = false, thundering = false, level = 100), EPSILON)
    }

    @Test
    fun `CD-BAL-14 no level pushes a windmill past the thunderstorm factor`() {
        for (weather in listOf(clear, rain, storm)) {
            for (level in -5..150) {
                val factor = IeWindmillMath.factor(weather, level)
                assertTrue(factor <= 1.0 + EPSILON, "weather $weather at level $level gives $factor")
                assertTrue(factor >= weather - EPSILON, "weather $weather at level $level gives $factor")
            }
        }
    }

    @Test
    fun `CD-AIR-05 the bonus holds while its worker renews it within 2 ticks`() {
        val until = IeWindmillMath.until(now = 100)
        assertTrue(IeWindmillMath.holds(until, now = 100))
        assertTrue(IeWindmillMath.holds(until, now = 101))
        assertFalse(IeWindmillMath.holds(until, now = 102))
    }

    @Test
    fun `CD-AIR-06 a client gets the worker's level of each windmill, which never lapses there by time`() {
        val buffer = Unpooled.buffer()
        WindmillBonus.STREAM_CODEC.encode(buffer, WindmillBonus(73).also { it.until = 5 })
        val client = WindmillBonus.STREAM_CODEC.decode(buffer)
        assertEquals(73, client.level)
        assertEquals(Long.MAX_VALUE, client.until)
    }

    /** The sails turn in the plane of the hub, up to 4 blocks out, and the air that they need lies in front of it. */
    @Test
    fun `the worker stands in front of the windmill or behind it, never in the plane of its sails`() {
        val hub = BlockPos(0, 64, 0)
        assertFalse(IeWindmillMath.clearOfSails(hub, Direction.NORTH, BlockPos(2, 64, 0)))
        assertFalse(IeWindmillMath.clearOfSails(hub, Direction.NORTH, BlockPos(0, 61, 0)))
        assertTrue(IeWindmillMath.clearOfSails(hub, Direction.NORTH, BlockPos(0, 64, -2)))
        assertTrue(IeWindmillMath.clearOfSails(hub, Direction.NORTH, BlockPos(1, 63, 2)))
        assertFalse(IeWindmillMath.clearOfSails(hub, Direction.EAST, BlockPos(0, 64, 3)))
        assertTrue(IeWindmillMath.clearOfSails(hub, Direction.EAST, BlockPos(-1, 64, 3)))
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
