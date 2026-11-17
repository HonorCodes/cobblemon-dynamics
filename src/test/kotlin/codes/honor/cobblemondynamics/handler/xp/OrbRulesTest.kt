package codes.honor.cobblemondynamics.handler.xp

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrbRulesTest {
    private val defaults = SoulCollectorParams.DEFAULT

    /** [n] orbs of one unit of 1 XP, at 1 to [n] blocks from the worker, listed farthest first. */
    private fun singles(n: Int): List<OrbFacts> = (n downTo 1).map { OrbFacts(it, it.toDouble(), 1, 1, null) }

    private fun xpOf(takes: List<Take>): Int = takes.sumOf { it.xp }

    @Test
    fun `CD-SOU-05 an orb 7,9 blocks from a player stays, an orb 8,1 blocks away goes, and a spectator 2 blocks away stops nothing`() {
        val near = OrbRules.nearestPlayer(listOf(NearbyPlayer(7.9, false), NearbyPlayer(30.0, false)))
        val far = OrbRules.nearestPlayer(listOf(NearbyPlayer(8.1, false)))
        val spectator = OrbRules.nearestPlayer(listOf(NearbyPlayer(2.0, true)))
        assertEquals(7.9, near)
        assertNull(spectator, "a spectator counted as a player")
        val orbs = listOf(OrbFacts(1, 1.0, 5, 1, near), OrbFacts(2, 2.0, 5, 1, far), OrbFacts(3, 3.0, 5, 1, spectator))
        val taken = OrbRules.takes(orbs, units = 10, room = 1_000, playerDistance = defaults.playerDistance).map { it.id }
        assertEquals(listOf(2, 3), taken)
        assertTrue(OrbRules.leftForPlayer(orbs[0], defaults.playerDistance))
        assertFalse(OrbRules.takeable(orbs[0], 1_000, defaults.playerDistance))
        assertTrue(OrbRules.takeable(orbs[1], 1_000, defaults.playerDistance))
    }

    @Test
    fun `CD-SOU-06 of 30 units in one batch, level 1 takes 1, level 50 takes 5 and level 100 takes 10, the nearest first`() {
        for ((level, units) in mapOf(1 to 1, 50 to 5, 100 to 10)) {
            assertEquals(units, defaults.unitsPerBatch(level), "level $level")
            val takes = OrbRules.takes(singles(30), defaults.unitsPerBatch(level), room = 1_000, defaults.playerDistance)
            assertEquals((1..units).toList(), takes.map { it.id }, "level $level")
            assertEquals(units, takes.sumOf { it.units }, "level $level")
        }
        assertEquals(listOf(1, 1, 10, 10), listOf(-5, 0, 150, 100).map(defaults::unitsPerBatch), "the level is clamped to 1..100")
    }

    /** A merged orb of count 4 is 4 units, so a batch counts its units, not its orbs. */
    @Test
    fun `CD-SOU-06 a batch counts units across merged orbs`() {
        val orbs = listOf(OrbFacts(1, 1.0, 2, 4, null), OrbFacts(2, 2.0, 2, 4, null))
        val takes = OrbRules.takes(orbs, units = 5, room = 1_000, defaults.playerDistance)
        assertEquals(listOf(Take(1, 8, 4, 0, 2), Take(2, 2, 1, 3, 2)), takes)
        assertTrue(takes[0].emptied)
        assertFalse(takes[1].emptied)
    }

    @Test
    fun `CD-SOU-07 an orb of value 5 and count 4 with room for 12 gives 10 XP and keeps count 2`() {
        val takes = OrbRules.takes(listOf(OrbFacts(1, 1.0, 5, 4, null)), units = 10, room = 12, defaults.playerDistance)
        assertEquals(listOf(Take(1, 10, 2, 2, 5)), takes)
        assertEquals(10, xpOf(takes))
        assertFalse(takes.single().emptied)
    }

    @Test
    fun `CD-SOU-07 an orb of value 50 and count 1 with room for 20 gives 20 and keeps value 30`() {
        val takes = OrbRules.takes(listOf(OrbFacts(1, 1.0, 50, 1, null)), units = 10, room = 20, defaults.playerDistance)
        assertEquals(listOf(Take(1, 20, 1, 1, 30)), takes)
        assertFalse(takes.single().emptied, "the orb lost the 30 XP that the collector had no room for")
    }

    /** What every take adds equals what its orb loses, so no XP is lost or made, whatever the room and the units. */
    @Test
    fun `CD-SOU-07 the XP that the takes give is exactly the XP that the orbs lose`() {
        val orbs = listOf(OrbFacts(1, 1.0, 7, 3, null), OrbFacts(2, 2.0, 50, 1, null), OrbFacts(3, 3.0, 3, 5, null))
        for (room in listOf(0, 1, 6, 7, 13, 20, 40, 100, 1_000)) {
            for (units in 0..12) {
                val takes = OrbRules.takes(orbs, units, room, defaults.playerDistance)
                for (take in takes) {
                    val orb = orbs.single { it.id == take.id }
                    assertEquals(orb.value * orb.count - take.value * take.count, take.xp, "room $room, units $units, orb ${orb.id}")
                }
                assertTrue(xpOf(takes) <= room, "room $room, units $units: the takes give ${xpOf(takes)} XP")
                assertTrue(takes.sumOf { it.units } <= units, "room $room, units $units")
            }
        }
    }

    /** A nearer orb whose unit does not fit gives nothing, and the batch takes a farther orb that fits. */
    @Test
    fun `CD-SOU-07 an orb of count 2 whose unit does not fit stays whole, and a smaller orb behind it goes`() {
        val orbs = listOf(OrbFacts(1, 1.0, 30, 2, null), OrbFacts(2, 2.0, 3, 1, null))
        assertEquals(listOf(Take(2, 3, 1, 0, 3)), OrbRules.takes(orbs, units = 10, room = 20, defaults.playerDistance))
        assertFalse(OrbRules.takeable(orbs[0], 20, defaults.playerDistance))
    }

    @Test
    fun `CD-SOU-08 a full collector or no takeable orb is no demand`() {
        val orb = OrbFacts(1, 1.0, 5, 1, null)
        assertTrue(OrbRules.takeable(orb, 1, defaults.playerDistance))
        assertFalse(OrbRules.takeable(orb, 0, defaults.playerDistance), "a full collector has demand")
        assertFalse(OrbRules.takeable(orb.copy(count = 0), 100, defaults.playerDistance))
        assertFalse(OrbRules.takeable(orb.copy(playerWithin = 3.0), 100, defaults.playerDistance))
        assertTrue(OrbRules.takes(listOf(orb), units = 10, room = 0, defaults.playerDistance).isEmpty())
    }

    @Test
    fun `CD-SOU-10 a role file with 20 units per second takes 20 units at level 100, and its player distance counts`() {
        val params = SoulCollectorParams(20, 4.0)
        assertEquals(20, params.unitsPerBatch(100))
        assertEquals(20, OrbRules.takes(singles(30), params.unitsPerBatch(100), room = 1_000, params.playerDistance).size)
        val orb = OrbFacts(1, 1.0, 5, 1, 5.0)
        assertTrue(OrbRules.takeable(orb, 100, params.playerDistance), "a player 5 blocks away stopped a worker with a distance of 4")
        assertFalse(OrbRules.takeable(orb, 100, defaults.playerDistance))
    }

    @Test
    fun `CD-SOU-02 the handler matches the Experience Collector and no other block`() {
        assertTrue(SoulCollectorHandler.isTarget(ResourceLocation.parse("cobblemon_dynamics:experience_collector")))
        for (other in listOf("minecraft:chest", "create:fluid_tank", "cobblemon_dynamics_test:xp_tank", "cobblemon_dynamics:charge_cell")) {
            assertFalse(SoulCollectorHandler.isTarget(ResourceLocation.parse(other)), other)
        }
    }
}
