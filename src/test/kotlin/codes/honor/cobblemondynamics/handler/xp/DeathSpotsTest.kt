package codes.honor.cobblemondynamics.handler.xp

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeathSpotsTest {
    private val overworld = ResourceLocation.parse("minecraft:overworld")
    private val nether = ResourceLocation.parse("minecraft:the_nether")
    private val area = AABB(-10.0, -10.0, -10.0, 10.0, 10.0, 10.0)

    private fun spot(x: Double, tick: Long, level: ResourceLocation = overworld) = DeathSpots.Spot(level, x, 4.0, 3.0, tick)

    /** An orb despawns 6,000 ticks after it drops, so the spot of a death counts as long (R86). */
    @Test
    fun `CD-SOU-12 a spot counts for 6,000 ticks after the death, only in its own level and inside the area`() {
        val spots = DeathSpots()
        spots.record(spot(1.0, tick = 1_000))
        assertEquals(listOf(spot(1.0, tick = 1_000)), spots.within(overworld, area, now = 1_000))
        assertEquals(1, spots.within(overworld, area, now = 6_999).size, "a spot 5,999 ticks old")
        assertTrue(spots.within(nether, area, now = 1_000).isEmpty(), "a spot of another level")
        assertTrue(spots.within(overworld, area.move(30.0, 0.0, 0.0), now = 1_000).isEmpty(), "a spot outside the area")
        assertTrue(spots.within(overworld, area, now = 7_000).isEmpty(), "a spot 6,000 ticks old")
        assertEquals(0, spots.size, "an expired spot stayed in memory")
    }

    /** A spot from a later tick than now comes from another world in the same game, so it counts as expired. */
    @Test
    fun `CD-SOU-12 a spot from a later tick counts as expired, and a server stop forgets every spot`() {
        val spots = DeathSpots()
        spots.record(spot(1.0, tick = 50_000))
        assertTrue(spots.within(overworld, area, now = 100).isEmpty())
        spots.record(spot(2.0, tick = 100))
        spots.record(spot(3.0, tick = 120, level = nether))
        assertEquals(2, spots.size)
        spots.clear()
        assertEquals(0, spots.size)
    }

    @Test
    fun `CD-SOU-12 the memory holds at most 256 spots, and the 257th lets the oldest go`() {
        val spots = DeathSpots()
        for (i in 0..DeathSpots.MAX_SPOTS) spots.record(spot(i.toDouble(), tick = 100L + i))
        assertEquals(256, DeathSpots.MAX_SPOTS)
        assertEquals(DeathSpots.MAX_SPOTS, spots.size)
        val all = AABB(-1.0, -1.0, -1.0, 300.0, 10.0, 10.0)
        assertEquals((1..DeathSpots.MAX_SPOTS).map { it.toDouble() }, spots.within(overworld, all, now = 400).map { it.x })
    }
}
