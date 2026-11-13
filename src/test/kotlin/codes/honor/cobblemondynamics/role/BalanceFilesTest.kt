package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.capability.GeneratorParams
import codes.honor.cobblemondynamics.handler.farming.CultivatorParams
import codes.honor.cobblemondynamics.handler.farming.GrowthPace
import codes.honor.cobblemondynamics.handler.farming.HarvestClock
import codes.honor.cobblemondynamics.handler.item.TransferParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The balance rows over the mod's own role files (ADR-0010): each test parses the files and computes the bound that its row states. */
class BalanceFilesTest {
    private val roles: List<RoleDefinition> by lazy {
        BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.map(BuiltInRoles::parse)
    }

    private fun withHandler(handler: String): List<RoleDefinition> = roles.filter { it.handler.toString() == handler }

    /** The curve's level is clamped to 1 to 100, so these levels hold every rate that a worker can give. */
    private fun topRate(params: GeneratorParams): Int =
        (1..100).maxOf { level -> maxOf(params.fePerTick(level, emptySet()), params.fePerTick(level, params.rareLabels)) }

    @Test
    fun `CD-BAL-01 the default Generator tops out below 15,000 FE per tick`() {
        val generators = withHandler("cobblemon_dynamics:fe_push")
        assertTrue(generators.isNotEmpty(), "no built-in role file uses the FE handler")
        for (role in generators) {
            val top = topRate(GeneratorParams.of(role.params))
            assertTrue(top < 15_000, "${role.id} allows $top FE per tick per worker")
        }
        assertEquals(10_000, topRate(GeneratorParams.of(BuiltInRoles.parse("generator").params)))
    }

    /** One AE2 Growth Accelerator gives its neighbors 1 random tick per 10 ticks with AE2's default config. */
    @Test
    fun `CD-BAL-07 the Crystal Tender interval is at least 10 ticks`() {
        val tenders = withHandler("cobblemon_dynamics:budding")
        assertTrue(tenders.isNotEmpty(), "no built-in role file uses the budding handler")
        for (role in tenders) {
            val interval = role.params.get(INTERVAL_TICKS)
            assertNotNull(interval, "${role.id} has no $INTERVAL_TICKS")
            assertTrue(interval.asInt >= 10, "${role.id} gives a random tick every ${interval.asInt} ticks")
        }
        assertEquals(10, BuiltInRoles.parse("crystal_tender").params.get(INTERVAL_TICKS).asInt)
    }

    /** One batch every 20 ticks, so the items per batch are the items per second (R52). Both files must exist. */
    @Test
    fun `CD-BAL-05 Telekinesis and Magnet move at most 64 items per second`() {
        for (name in listOf("telekinesis", "magnet")) {
            val params = TransferParams.of(BuiltInRoles.parse(name).params)
            val top = (1..100).maxOf(params::itemsPerBatch)
            assertTrue(top <= 64, "$name.json lets one worker move $top items per second")
            assertEquals(64, params.itemsPerBatch(100), name)
        }
    }

    /** A player who holds bone meal applies 300 uses per minute (BM5), so a Cultivator does a tenth of that at most. */
    @Test
    fun `CD-BAL-08 the default Cultivator applies at most 30 uses per minute`() {
        val cultivators = withHandler("cobblemon_dynamics:cultivator")
        assertTrue(cultivators.isNotEmpty(), "no built-in role file uses the Cultivator handler")
        for (role in cultivators) {
            val params = CultivatorParams.of(role.params)
            val top = (1..100).maxOf { level -> 1_200 / GrowthPace.intervalTicks(level, params.usesPerMinuteAt100) }
            assertTrue(top <= 30, "${role.id} applies $top growth uses per minute")
        }
        assertEquals(30, CultivatorParams.of(BuiltInRoles.parse("cultivator").params).usesPerMinuteAt100)
    }

    /** One harvest per claim, `harvest_ticks` after the work starts (BM4). */
    @Test
    fun `CD-BAL-09 the default Harvester takes at most one crop per 20 ticks`() {
        val harvesters = withHandler("cobblemon_dynamics:harvester")
        assertTrue(harvesters.isNotEmpty(), "no built-in role file uses the Harvester handler")
        for (role in harvesters) {
            val ticks = HarvestClock.ticksOf(role.params)
            assertTrue(ticks >= 20, "${role.id} harvests $ticks ticks after its work starts")
        }
        assertEquals(20, HarvestClock.ticksOf(BuiltInRoles.parse("harvester").params))
    }

    private companion object {
        const val INTERVAL_TICKS = "interval_ticks"
    }
}
