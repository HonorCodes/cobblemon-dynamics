package codes.honor.cobblemondynamics.handler.cobblemon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealingChargeTest {
    /** COHO's Cobblemon config: `maxHealerCharge` 6 and `secondsToChargeHealingMachine` 900, so one charge every 150 s (BM13). */
    private val max = 6f
    private val own = HealingCharge.ownPerTick(max, secondsToCharge = 900.0)
    private val multiplier = HealingParams.of(JsonObject()).multiplierAt100

    private fun added(charge: Float, level: Int, recharges: Boolean = true): Float =
        HealingCharge.added(charge, max, own, HealingCharge.workerPerTick(multiplier, level, own), recharges)

    @Test
    fun `CD-MCH-08 Cobblemon's own rate is the maximum charge over the recharge time`() {
        assertEquals(6.0 / 18_000, own, 1e-12)
        assertEquals(0.0, HealingCharge.ownPerTick(max, secondsToCharge = 0.0), 1e-12)
    }

    @Test
    fun `CD-MCH-08 level 100 adds 4 times 6 over 18,000 per tick, and level 50 half that`() {
        assertEquals(4.0 * 6 / 18_000, HealingCharge.workerPerTick(multiplier, 100, own), 1e-12)
        assertEquals(2.0 * 6 / 18_000, HealingCharge.workerPerTick(multiplier, 50, own), 1e-12)
        assertEquals(4.0 * 6 / 18_000, HealingCharge.workerPerTick(multiplier, 250, own), 1e-12)
        assertEquals((4.0 * 6 / 18_000).toFloat(), added(charge = 0f, level = 100), 1e-9f)
        assertEquals((2.0 * 6 / 18_000).toFloat(), added(charge = 3f, level = 50), 1e-9f)
    }

    @Test
    fun `CD-MCH-08 a full machine gets nothing`() {
        assertEquals(0f, added(charge = max, level = 100))
    }

    /**
     * The machine's own tick updates its block's charge level and its comparator signal only while it charges, so the worker leaves
     * the last step to it, and the block shows the full charge.
     */
    @Test
    fun `CD-MCH-08 the worker stops one own step below the maximum, so the machine's own tick fills it`() {
        assertEquals(0f, added(charge = max - (own / 2).toFloat(), level = 100))
        assertEquals(0f, added(charge = max - own.toFloat(), level = 100))
        val near = max - own.toFloat() - 0.0001f
        assertEquals(0.0001f, added(charge = near, level = 100), 1e-6f)
    }

    @Test
    fun `CD-MCH-08 a machine that is healing or never runs out gets nothing`() {
        assertEquals(0f, added(charge = 0f, level = 100, recharges = false))
        assertEquals(0f, HealingCharge.added(0f, max, own = 0.0, perTick = 1.0, recharges = true))
    }

    /** Cobblemon's tick and the worker's, tick after tick, from empty: 5 times the own rate in all, and never past the maximum. */
    @Test
    fun `CD-MCH-08 an empty machine fills at 5 times its own rate and stops at its maximum`() {
        var charge = 0f
        var ticks = 0
        while (charge < max && ticks < 10_000) {
            charge = minOf(max, charge + own.toFloat())
            charge += added(charge, level = 100)
            assertTrue(charge <= max, "the charge passed its maximum at tick $ticks: $charge")
            ticks++
        }
        assertEquals(max, charge)
        assertTrue(ticks in 3_590..3_610, "an empty machine took $ticks ticks, not 18,000 ÷ 5")
    }

    @Test
    fun `CD-MCH-08 the default multiplier is 4, and a key of the wrong type takes it`() {
        assertEquals(4.0, multiplier)
        assertEquals(
            4.0,
            HealingParams.of(JsonParser.parseString("""{"recharge_multiplier_at_level_100": "fast"}""").asJsonObject).multiplierAt100,
        )
        assertEquals(
            2.0,
            HealingParams.of(JsonParser.parseString("""{"recharge_multiplier_at_level_100": 2}""").asJsonObject).multiplierAt100,
        )
    }
}
