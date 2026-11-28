package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.compat.mekanism.HeaterMath
import codes.honor.cobblemondynamics.compat.pneumaticcraft.PncHeatMath
import codes.honor.cobblemondynamics.compat.pneumaticcraft.PncHeaterParams
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.handler.capability.GeneratorParams
import codes.honor.cobblemondynamics.handler.capability.PumpParams
import codes.honor.cobblemondynamics.handler.farming.GrowthPace
import codes.honor.cobblemondynamics.handler.farming.HarvestClock
import codes.honor.cobblemondynamics.handler.item.TransferParams
import codes.honor.cobblemondynamics.handler.tree.FellClock
import codes.honor.cobblemondynamics.handler.tree.LumberjackParams
import codes.honor.cobblemondynamics.handler.vanilla.BuddingRules
import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus
import codes.honor.cobblemondynamics.handler.water.FreezePace
import codes.honor.cobblemondynamics.handler.xp.SoulCollectorParams
import codes.honor.cobblemondynamics.stamina.MoveFacts
import codes.honor.cobblemondynamics.stamina.TraitFlags
import codes.honor.cobblemondynamics.stamina.WorkTraits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CombinedMultiplierTest {
    @Test
    fun `CD-PRG-09 full 6-way multiplicative stack scales output and pace roles while clamping Muse shelves to 15 and intervals to 1+`() {
        val eval = WorkTraits.evaluate(
            shiny = true,
            alpha = true,
            majestic = true,
            moves = listOf(MoveFacts("thunderbolt", "electric", 90.0, isStatus = false)),
            roleTypes = setOf("electric"),
            hasMate = true,
            labels = setOf("legendary"),
            outputOrPace = true,
            settings = EngineSettings.DEFAULT,
        )
        val expected = 1.15 * 1.25 * 1.35 * 1.20 * 1.15 * 2.0
        assertEquals(expected, eval.combinedMultiplier, 1e-9)
        assertEquals(5.356125, eval.combinedMultiplier, 1e-9)
        val allFlags = TraitFlags.SHINY or TraitFlags.ALPHA or TraitFlags.MAJESTIC or TraitFlags.MASTERY or TraitFlags.MATE
        assertEquals(allFlags, eval.flags)

        val gen = GeneratorParams.of(BuiltInRoles.parse("generator").params)
        assertEquals(26_781, gen.fePerTick(100, eval.combinedMultiplier))
        assertEquals(2_678, PumpParams.of(BuiltInRoles.parse("pump").params).mbPerTick(100, eval.combinedMultiplier))
        assertEquals(343, TransferParams.of(BuiltInRoles.parse("telekinesis").params).itemsPerBatch(100, eval.combinedMultiplier))
        assertEquals(343, TransferParams.of(BuiltInRoles.parse("magnet").params).itemsPerBatch(100, eval.combinedMultiplier))
        assertEquals(54, SoulCollectorParams.of(BuiltInRoles.parse("soul_collector").params).unitsPerBatch(100, eval.combinedMultiplier))
        assertEquals(8_034, HeaterMath.heatPerTick(1_500.0, 100, eval.combinedMultiplier))
        assertEquals(
            10.0 * expected,
            PncHeatMath.heatPerTick(PncHeaterParams(1_300.0, 100.0), 300.0, 100, eval.combinedMultiplier),
            1e-9,
        )

        val museParams = BuiltInRoles.parse("muse").params
        assertEquals(15, MuseBonus.bonus(100, museParams, apothicEnchanting = false, multiplier = eval.combinedMultiplier).bookshelves)
        assertEquals(100f, MuseBonus.bonus(100, museParams, apothicEnchanting = true, multiplier = eval.combinedMultiplier).quanta, 1e-6f)
        assertEquals(64f, MuseBonus.bonus(25, museParams, apothicEnchanting = true, multiplier = eval.combinedMultiplier).quanta, 1e-6f)

        assertEquals(7, GrowthPace.intervalTicks(100, 30, eval.combinedMultiplier))
        assertEquals(4, HarvestClock.ticksOf(BuiltInRoles.parse("harvester").params, eval.combinedMultiplier))
        assertEquals(4, HarvestClock.ticksOf(BuiltInRoles.parse("picker").params, eval.combinedMultiplier))
        assertEquals(1, FellClock.ticksPerLog(100, LumberjackParams.DEFAULT, eval.combinedMultiplier))
        assertEquals(4, FreezePace.intervalTicks(100, 60, eval.combinedMultiplier))
        assertEquals(2, BuddingRules.intervalOf(BuiltInRoles.parse("crystal_tender").params, eval.combinedMultiplier))

        assertTrue(GrowthPace.intervalTicks(100, 30, 500.0) >= 1)
        assertTrue(HarvestClock.ticksOf(BuiltInRoles.parse("harvester").params, 500.0) >= 1)
        assertTrue(FellClock.ticksPerLog(100, LumberjackParams.DEFAULT, 500.0) >= 1)
        assertTrue(FreezePace.intervalTicks(100, 60, 500.0) >= 1)
        assertTrue(BuddingRules.intervalOf(BuiltInRoles.parse("crystal_tender").params, 500.0) >= 1)
    }
}
