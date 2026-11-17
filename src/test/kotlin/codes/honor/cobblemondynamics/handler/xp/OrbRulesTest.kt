package codes.honor.cobblemondynamics.handler.xp

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The facts hold squared distances, as the handler reads them with `distanceToSqr` (R86). */
class OrbRulesTest {
    private val defaults = SoulCollectorParams.DEFAULT

    /** [n] orbs of one unit of 1 XP, at 1 to [n] blocks from the worker, listed farthest first. */
    private fun singles(n: Int): List<OrbFacts> = (n downTo 1).map { OrbFacts(it, sq(it.toDouble()), 1, 1, null) }

    private fun sq(blocks: Double): Double = blocks * blocks

    private fun xpOf(takes: List<Take>): Int = takes.sumOf { it.xp }

    @Test
    fun `CD-SOU-05 an orb 7,9 blocks from a player stays, an orb 8,1 blocks away goes, and a spectator 2 blocks away stops nothing`() {
        val near = OrbRules.nearestPlayer(listOf(NearbyPlayer(sq(7.9), false), NearbyPlayer(sq(30.0), false)), emptyList())
        val far = OrbRules.nearestPlayer(listOf(NearbyPlayer(sq(8.1), false)), emptyList())
        val spectator = OrbRules.nearestPlayer(listOf(NearbyPlayer(sq(2.0), true)), emptyList())
        assertEquals(sq(7.9), near)
        assertNull(spectator, "a spectator counted as a player")
        val orbs = listOf(OrbFacts(1, 1.0, 5, 1, near), OrbFacts(2, 4.0, 5, 1, far), OrbFacts(3, 9.0, 5, 1, spectator))
        val taken = OrbRules.takes(orbs, units = 10, room = 1_000, playerDistance = defaults.playerDistance).map { it.id }
        assertEquals(listOf(2, 3), taken)
        assertTrue(OrbRules.leftForPlayer(orbs[0], defaults.playerDistance))
        assertFalse(OrbRules.takeable(orbs[0], 1_000, defaults.playerDistance))
        assertTrue(OrbRules.takeable(orbs[1], 1_000, defaults.playerDistance))
    }

    /** A death spot counts as a player who is no spectator, so the orbs that a dead player dropped wait for that player (R86). */
    @Test
    fun `CD-SOU-12 an orb 7,9 blocks from a fresh death spot stays, and an orb 8,1 blocks away goes`() {
        val near = OrbRules.nearestPlayer(emptyList(), listOf(sq(40.0), sq(7.9)))
        val far = OrbRules.nearestPlayer(emptyList(), listOf(sq(8.1)))
        assertEquals(sq(7.9), near)
        val orbs = listOf(OrbFacts(1, 1.0, 5, 1, near), OrbFacts(2, 4.0, 5, 1, far))
        assertEquals(listOf(2), OrbRules.takes(orbs, units = 10, room = 1_000, defaults.playerDistance).map { it.id })
        assertEquals(sq(3.0), OrbRules.nearestPlayer(listOf(NearbyPlayer(sq(1.0), true), NearbyPlayer(sq(5.0), false)), listOf(sq(3.0))))
        assertNull(OrbRules.nearestPlayer(emptyList(), emptyList()))
    }

    @Test
    fun `CD-SOU-06 of 30 units in one batch, level 1 takes 1, level 50 takes 5 and level 100 takes 10, the nearest first`() {
        val sorted = OrbRules.nearestFirst(singles(30))
        assertEquals((1..30).toList(), sorted.map { it.id }, "a query sorts its orbs nearest first")
        for ((level, units) in mapOf(1 to 1, 50 to 5, 100 to 10)) {
            assertEquals(units, defaults.unitsPerBatch(level), "level $level")
            val takes = OrbRules.takes(sorted, defaults.unitsPerBatch(level), room = 1_000, defaults.playerDistance)
            assertEquals((1..units).toList(), takes.map { it.id }, "level $level")
            assertEquals(units, takes.sumOf { it.units }, "level $level")
        }
        assertEquals(listOf(1, 1, 10, 10), listOf(-5, 0, 150, 100).map(defaults::unitsPerBatch), "the level is clamped to 1..100")
        val tie = OrbRules.nearestFirst(listOf(OrbFacts(9, 4.0, 1, 1, null), OrbFacts(3, 4.0, 1, 1, null), OrbFacts(5, 1.0, 1, 1, null)))
        assertEquals(listOf(5, 3, 9), tie.map { it.id }, "of two orbs as near, the lower id goes first")
    }

    /** A merged orb of count 4 is 4 units, so a batch counts its units, not its orbs. */
    @Test
    fun `CD-SOU-06 a batch counts units across merged orbs`() {
        val orbs = listOf(OrbFacts(1, 1.0, 2, 4, null), OrbFacts(2, 4.0, 2, 4, null))
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

    /**
     * A batch puts each take into the collector before it changes the orb, and a collector that takes less cuts the take (R86). A part of
     * a unit cannot leave an orb with more units, so the cut take is the whole units that fit, and the handler gives the rest back.
     */
    @Test
    fun `CD-SOU-07 a take of 10 XP that the collector cuts to 7 takes 5 from an orb of count 4, and 7 from an orb of count 1`() {
        val merged = OrbFacts(1, 1.0, 5, 4, null)
        val planned = OrbRules.takes(listOf(merged), units = 10, room = 12, defaults.playerDistance).single()
        assertEquals(planned, OrbRules.take(merged, planned.units, planned.xp), "a take that the collector took whole changed")
        assertEquals(Take(1, 5, 1, 3, 5), OrbRules.take(merged, planned.units, 7))
        assertEquals(Take(2, 7, 1, 1, 43), OrbRules.take(OrbFacts(2, 1.0, 50, 1, null), units = 1, room = 7))
        assertEquals(Take(1, 0, 0, 4, 5), OrbRules.take(merged, planned.units, 0), "a collector that took nothing changed the orb")
        for (accepted in 0..12) {
            val take = OrbRules.take(merged, units = 10, room = accepted)
            assertTrue(take.xp <= accepted, "a cut to $accepted XP took ${take.xp}")
            assertEquals(merged.value * merged.count - take.value * take.count, take.xp, "a cut to $accepted XP")
        }
    }

    /** What every take adds equals what its orb loses, so no XP is lost or made, whatever the room and the units. */
    @Test
    fun `CD-SOU-07 the XP that the takes give is exactly the XP that the orbs lose`() {
        val orbs = listOf(OrbFacts(1, 1.0, 7, 3, null), OrbFacts(2, 4.0, 50, 1, null), OrbFacts(3, 9.0, 3, 5, null))
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
        val orbs = listOf(OrbFacts(1, 1.0, 30, 2, null), OrbFacts(2, 4.0, 3, 1, null))
        assertEquals(listOf(Take(2, 3, 1, 0, 3)), OrbRules.takes(orbs, units = 10, room = 20, defaults.playerDistance))
        assertFalse(OrbRules.takeable(orbs[0], 20, defaults.playerDistance))
    }

    @Test
    fun `CD-SOU-08 a full collector or no takeable orb is no demand`() {
        val orb = OrbFacts(1, 1.0, 5, 1, null)
        assertTrue(OrbRules.takeable(orb, 1, defaults.playerDistance))
        assertFalse(OrbRules.takeable(orb, 0, defaults.playerDistance), "a full collector has demand")
        assertFalse(OrbRules.takeable(orb.copy(count = 0), 100, defaults.playerDistance))
        assertFalse(OrbRules.takeable(orb.copy(playerWithinSqr = sq(3.0)), 100, defaults.playerDistance))
        assertTrue(OrbRules.takes(listOf(orb), units = 10, room = 0, defaults.playerDistance).isEmpty())
    }

    @Test
    fun `CD-SOU-10 a role file with 20 units per second takes 20 units at level 100, and its player distance counts`() {
        val params = SoulCollectorParams(20, 4.0)
        assertEquals(20, params.unitsPerBatch(100))
        assertEquals(20, OrbRules.takes(singles(30), params.unitsPerBatch(100), room = 1_000, params.playerDistance).size)
        val orb = OrbFacts(1, 1.0, 5, 1, sq(5.0))
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
