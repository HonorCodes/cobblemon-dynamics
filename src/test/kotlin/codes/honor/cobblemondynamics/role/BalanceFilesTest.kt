package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.compat.ars_nouveau.SourcelinkRule
import codes.honor.cobblemondynamics.compat.cookingforblockheads.CfbOvenRule
import codes.honor.cobblemondynamics.compat.forbidden_arcanus.ClibanoRule
import codes.honor.cobblemondynamics.compat.ironfurnaces.IronFurnaceRule
import codes.honor.cobblemondynamics.compat.mekanism.HeaterMath
import codes.honor.cobblemondynamics.compat.mekanism.HeaterParams
import codes.honor.cobblemondynamics.compat.pneumaticcraft.PncHeatMath
import codes.honor.cobblemondynamics.compat.pneumaticcraft.PncHeaterParams
import codes.honor.cobblemondynamics.handler.capability.GeneratorParams
import codes.honor.cobblemondynamics.handler.farming.CultivatorParams
import codes.honor.cobblemondynamics.handler.farming.GrowthPace
import codes.honor.cobblemondynamics.handler.farming.HarvestClock
import codes.honor.cobblemondynamics.handler.item.TransferParams
import codes.honor.cobblemondynamics.handler.tree.FellClock
import codes.honor.cobblemondynamics.handler.tree.LumberjackParams
import codes.honor.cobblemondynamics.handler.water.FreezePace
import codes.honor.cobblemondynamics.handler.water.FreezerParams
import codes.honor.cobblemondynamics.handler.xp.SoulCollectorParams
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

    /** A player with an iron axe breaks a log in 10 ticks, and Oritech's Treefeller fells 20 logs per second (BM6). */
    @Test
    fun `CD-BAL-10 the default Lumberjack fells at most 4 logs per second of Working`() {
        val lumberjacks = withHandler("cobblemon_dynamics:lumberjack")
        assertTrue(lumberjacks.isNotEmpty(), "no built-in role file uses the Lumberjack handler")
        for (role in lumberjacks) {
            val params = LumberjackParams.of(role.params)
            val top = (1..100).maxOf { level -> 20.0 / FellClock.ticksPerLog(level, params) }
            assertTrue(top <= 4.0, "${role.id} fells $top logs per second of Working")
        }
        assertEquals(5, LumberjackParams.of(BuiltInRoles.parse("lumberjack").params).ticksPerLogAt100)
    }

    /** A player takes one orb unit per 2 ticks (BM7), so a Soul Collector at level 100 takes as a player does, and never more. */
    @Test
    fun `CD-BAL-11 the default Soul Collector takes at most 10 orb units per second`() {
        val collectors = withHandler("cobblemon_dynamics:soul_collector")
        assertTrue(collectors.isNotEmpty(), "no built-in role file uses the Soul Collector handler")
        for (role in collectors) {
            val params = SoulCollectorParams.of(role.params)
            val top = (1..100).maxOf(params::unitsPerBatch)
            assertTrue(top <= 10, "${role.id} takes $top orb units per second")
        }
        assertEquals(10, SoulCollectorParams.of(BuiltInRoles.parse("soul_collector").params).unitsPerBatch(100))
    }

    /** A cold biome tries about 75 columns per minute in each chunk (BM8). The level is clamped to 1 to 100, as for the Cultivator. */
    @Test
    fun `CD-BAL-12 the default Freezer freezes at most 60 cells per minute`() {
        val freezers = withHandler("cobblemon_dynamics:freezer")
        assertTrue(freezers.isNotEmpty(), "no built-in role file uses the Freezer handler")
        for (role in freezers) {
            val params = FreezerParams.of(role.params)
            val top = (1..100).maxOf { level -> 1_200 / FreezePace.intervalTicks(level, params.cellsPerMinuteAt100) }
            assertTrue(top <= 60, "${role.id} freezes $top cells per minute")
        }
        assertEquals(60, FreezerParams.of(BuiltInRoles.parse("freezer").params).cellsPerMinuteAt100)
    }

    /** A full evaporation plant of height 18 uses about 1,870 J/t, and a level 100 Generator through a Resistive Heater gives 7,500 (BM9). */
    @Test
    fun `CD-BAL-13 one Mekanism Heater gives at most 1,500 J per tick`() {
        val heaters = withHandler("cobblemon_dynamics:mekanism_heat")
        assertTrue(heaters.isNotEmpty(), "no built-in role file uses the Mekanism heat handler")
        for (role in heaters) {
            val heat = HeaterParams.of(role.params).heatPerTickAt100
            val top = (1..100).maxOf { level -> HeaterMath.heatPerTick(heat, level) }
            assertTrue(top <= 1_500, "${role.id} gives $top J per tick")
        }
        assertEquals(1_500, HeaterMath.heatPerTick(HeaterParams.of(BuiltInRoles.parse("heater_mekanism").params).heatPerTickAt100, 100))
    }

    /** One lava block at 1,300 K with resistance 100 warms a machine at T by (1,300 − T) ÷ 100 per tick (BM10). */
    @Test
    fun `CD-BAL-13 one PneumaticCraft Heater gives at most the heat of one lava block`() {
        val heaters = withHandler("cobblemon_dynamics:pnc_heat")
        assertTrue(heaters.isNotEmpty(), "no built-in role file uses the PneumaticCraft heat handler")
        for (role in heaters) {
            val params = PncHeaterParams.of(role.params)
            for (temperature in 0..2_000 step 25) {
                val lava = maxOf(0.0, (1_300.0 - temperature) / 100.0)
                val top = (1..100).maxOf { level -> PncHeatMath.heatPerTick(params, temperature.toDouble(), level) }
                assertTrue(top <= lava + 1e-9, "${role.id} gives $top heat per tick at $temperature K, one lava block $lava")
            }
        }
        assertEquals(
            10.0,
            PncHeatMath.heatPerTick(PncHeaterParams.of(BuiltInRoles.parse("heater_pneumaticcraft").params), 300.0, 100),
            1e-9,
        )
    }

    /**
     * ADR-0010 with ruling 3 of R80: a Stoker gives a machine of another mod free fuel only, at that machine's own speed. The furnaces,
     * the oven and the Clibano hold 2 burn ticks, which runs a machine at its own speed and no faster, and the incubator runs at one Speed
     * Upgrade at most. None of their files holds a rate that a pack could raise, and the Volcanic Sourcelink gets one coal per 1,600 ticks.
     */
    @Test
    fun `CD-BAL-15 the Stokers of other mods give at most the free fuel of their rows`() {
        val stokers = mapOf(
            "stoker_ironfurnaces" to "cobblemon_dynamics:iron_furnace",
            "stoker_cookingforblockheads" to "cobblemon_dynamics:cfb_oven",
            "stoker_forbidden_arcanus" to "cobblemon_dynamics:clibano",
            "stoker_cobblemon_incubator" to "cobblemon_dynamics:egg_incubator",
            "stoker_ars_nouveau" to "cobblemon_dynamics:volcanic_sourcelink",
        )
        for ((name, handler) in stokers) assertEquals(handler, BuiltInRoles.parse(name).handler.toString(), name)
        for (name in stokers.keys - "stoker_ars_nouveau") assertEquals(0, BuiltInRoles.parse(name).params.size(), "$name holds a rate")
        assertEquals(2, IronFurnaceRule.HEAT_TICKS)
        assertEquals(2, CfbOvenRule.HEAT_TICKS)
        assertEquals(2, ClibanoRule.HEAT_TICKS)
        val interval = SourcelinkRule.intervalOf(BuiltInRoles.parse("stoker_ars_nouveau").params)
        assertTrue(interval >= 1_600, "the Volcanic Sourcelink gets one coal per $interval ticks")
        assertEquals(1_600, interval)
    }

    private companion object {
        const val INTERVAL_TICKS = "interval_ticks"
    }
}
