package codes.honor.cobblemondynamics.compat.botanypots

import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PotRulesTest {
    /** A pot of Botany Pots 21.1.44 with wheat in dirt: 1,090 required growth ticks (RES10). */
    private fun pot(
        growth: Float,
        type: String = PotRules.BASIC,
        canBonemeal: Boolean = true,
        canHarvest: Boolean = false,
        required: Int = 1_090,
    ) = PotFacts(type, canBonemeal, canHarvest, growth, required)

    private val yes = { true }

    @Test
    fun `CD-FRM-04 a use on a pot at 0 of 1,090 ticks adds 400 to 600`() {
        for (roll in listOf(400, 517, 600)) assertEquals(roll.toFloat(), PotRules.grown(pot(0f), roll, yes))
    }

    @Test
    fun `CD-FRM-04 a use at 1,000 adds 70, and a pot at 1,070 has no demand`() {
        assertEquals(1_070f, PotRules.grown(pot(1_000f), 400, yes))
        assertEquals(1_070f, PotRules.grown(pot(1_000f), 600, yes))
        assertFalse(PotRules.cultivatorDemand(pot(1_070f)))
        assertNull(PotRules.grown(pot(1_070f), 400, yes))
        assertTrue(PotRules.cultivatorDemand(pot(1_069f)))
    }

    /** Botany Pots' own bone meal: a random 400 to 600 ticks, then a cooldown of 20 ticks in which no bone meal works. */
    @Test
    fun `CD-FRM-04 each use rolls 400 to 600 ticks and sets the bone meal cooldown to 20 ticks`() {
        assertEquals(400, PotRules.roll { 0 })
        assertEquals(600, PotRules.roll { bound -> bound - 1 })
        assertEquals(20, PotRules.BONEMEAL_COOLDOWN)
        assertFalse(PotRules.cultivatorDemand(pot(0f, canBonemeal = false)), "a pot in its bone meal cooldown")
    }

    @Test
    fun `CD-FRM-04 a waxed pot, a pot with no crop and a pot of 20 ticks or less have no growth demand`() {
        assertFalse(PotRules.cultivatorDemand(pot(Float.MAX_VALUE, type = PotRules.WAXED)))
        assertFalse(PotRules.cultivatorDemand(pot(0f, type = PotRules.WAXED)))
        assertFalse(PotRules.cultivatorDemand(pot(-1f, required = -1)), "a pot with no crop")
        assertFalse(PotRules.cultivatorDemand(pot(0f, required = 20)))
        assertTrue(PotRules.cultivatorDemand(pot(-1f)), "a pot that was just harvested")
        assertTrue(PotRules.cultivatorDemand(pot(0f, type = PotRules.HOPPER)), "a hopper pot grows too")
    }

    @Test
    fun `CD-FRM-05 a basic pot that can harvest has demand, and a hopper pot and a waxed pot have none`() {
        assertTrue(PotRules.harvesterDemand(pot(1_090f, canHarvest = true)))
        assertFalse(PotRules.harvesterDemand(pot(1_090f, type = PotRules.HOPPER, canHarvest = true)))
        assertFalse(PotRules.harvesterDemand(pot(1_090f, type = PotRules.WAXED, canHarvest = true)))
        assertFalse(PotRules.harvesterDemand(pot(500f, canHarvest = false)))
        assertTrue(PotRules.harvests(pot(1_090f, canHarvest = true), yes))
    }

    @Test
    fun `CD-FRM-07 a refused use question gives no growth and no harvest`() {
        var asked = 0
        val no = {
            asked++
            false
        }
        assertNull(PotRules.grown(pot(0f), 500, no))
        assertFalse(PotRules.harvests(pot(1_090f, canHarvest = true), no))
        assertEquals(2, asked)
    }

    /** The question is asked only for a use or a harvest that would happen, so a pot with no demand asks nothing. */
    @Test
    fun `CD-FRM-07 a pot with no demand asks no use question`() {
        var asked = 0
        val counted = {
            asked++
            true
        }
        assertNull(PotRules.grown(pot(1_070f), 500, counted))
        assertFalse(PotRules.harvests(pot(500f), counted))
        assertEquals(0, asked)
    }

    @Test
    fun `CD-FRM-06 the pot roles claim in the lanes of the Cultivator and the Harvester`() {
        assertEquals(CultivatorHandler.LANE, PotRules.CULTIVATOR_LANE)
        assertEquals(HarvesterHandler.LANE, PotRules.HARVESTER_LANE)
    }
}
