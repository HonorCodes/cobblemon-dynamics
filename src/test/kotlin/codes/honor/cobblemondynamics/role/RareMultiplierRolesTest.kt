package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.compat.mekanism.HeaterMath
import codes.honor.cobblemondynamics.compat.mekanism.HeaterParams
import codes.honor.cobblemondynamics.compat.pneumaticcraft.PncHeatMath
import codes.honor.cobblemondynamics.compat.pneumaticcraft.PncHeaterParams
import codes.honor.cobblemondynamics.handler.capability.GeneratorParams
import codes.honor.cobblemondynamics.handler.capability.PumpParams
import codes.honor.cobblemondynamics.handler.cobblemon.HealingCharge
import codes.honor.cobblemondynamics.handler.farming.CultivatorParams
import codes.honor.cobblemondynamics.handler.farming.GrowthPace
import codes.honor.cobblemondynamics.handler.farming.HarvestClock
import codes.honor.cobblemondynamics.handler.item.TransferParams
import codes.honor.cobblemondynamics.handler.tree.FellClock
import codes.honor.cobblemondynamics.handler.tree.LumberjackParams
import codes.honor.cobblemondynamics.handler.vanilla.BuddingRules
import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus
import codes.honor.cobblemondynamics.handler.water.FreezePace
import codes.honor.cobblemondynamics.handler.water.FreezerParams
import codes.honor.cobblemondynamics.handler.xp.SoulCollectorParams
import codes.honor.cobblemondynamics.stamina.WorkTraits
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RareMultiplierRolesTest {
    private val outputRoleFiles = listOf(
        "generator",
        "generator_healing_machine",
        "pump",
        "telekinesis",
        "magnet",
        "cultivator",
        "cultivator_botanypots",
        "harvester",
        "harvester_botanypots",
        "picker",
        "lumberjack",
        "soul_collector",
        "freezer",
        "crystal_tender",
        "heater_mekanism",
        "heater_pneumaticcraft",
        "muse",
    )

    @Test
    fun `CD-PRG-08 legendary and mythical give 2_0x once across all 13 output and pace roles, while ultra_beast and paradox stay 1_0x`() {
        for (file in outputRoleFiles) {
            val role = BuiltInRoles.parse(file)
            assertTrue(WorkTraits.isOutputOrPace(role.handler), "$file (${role.handler}) must be output/pace")
            for (label in listOf("legendary", "mythical")) {
                val m = WorkTraits.evaluate(
                    labels = setOf(label),
                    rareLabels = role.rareLabels,
                    rareMultiplier = role.rareMultiplier,
                    outputOrPace = WorkTraits.isOutputOrPace(role.handler),
                ).combinedMultiplier
                assertEquals(2.0, m, 1e-9, "$file with $label")
            }
            for (label in listOf("ultra_beast", "paradox")) {
                val m = WorkTraits.evaluate(
                    labels = setOf(label),
                    rareLabels = role.rareLabels,
                    rareMultiplier = role.rareMultiplier,
                    outputOrPace = WorkTraits.isOutputOrPace(role.handler),
                ).combinedMultiplier
                assertEquals(1.0, m, 1e-9, "$file with $label")
            }
        }

        val stoker = BuiltInRoles.parse("stoker")
        assertFalse(WorkTraits.isOutputOrPace(stoker.handler))
        val legStoker = WorkTraits.evaluate(
            labels = setOf("legendary"),
            rareLabels = stoker.rareLabels,
            rareMultiplier = stoker.rareMultiplier,
            outputOrPace = WorkTraits.isOutputOrPace(stoker.handler),
        ).combinedMultiplier
        assertEquals(1.0, legStoker, 1e-9)

        assertFalse(WorkTraits.isOutputOrPace(ResourceLocation.parse("cobblemon_dynamics:unknown_future_handler")))
    }

    @Test
    fun `CD-PRG-08 Generator multiplies unrounded curve once before rounding, pinning Lv50 at 433 and Lv100 at 10,000 FE per tick`() {
        val gen = GeneratorParams.of(BuiltInRoles.parse("generator").params)
        val leg50 = WorkTraits.evaluate(labels = setOf("legendary"), outputOrPace = true).combinedMultiplier
        assertEquals(2.0, leg50, 1e-9)
        assertEquals(433, gen.fePerTick(50, leg50))
        assertEquals(10_000, gen.fePerTick(100, leg50))
        assertEquals(433, gen.fePerTick(50, setOf("legendary"), traitMultiplier = 1.0))
        assertEquals(10_000, gen.fePerTick(100, setOf("legendary"), traitMultiplier = 1.0))

        assertEquals(1_000, PumpParams.of(BuiltInRoles.parse("pump").params).mbPerTick(100, 2.0))
        assertEquals(2.0 * 4.0, HealingCharge.workerPerTick(4.0, 100, 1.0, 2.0), 1e-9)
        assertEquals(128, TransferParams.of(BuiltInRoles.parse("telekinesis").params).itemsPerBatch(100, 2.0))
        assertEquals(128, TransferParams.of(BuiltInRoles.parse("magnet").params).itemsPerBatch(100, 2.0))
        assertEquals(
            20,
            GrowthPace.intervalTicks(100, CultivatorParams.of(BuiltInRoles.parse("cultivator").params).usesPerMinuteAt100, 2.0),
        )
        assertEquals(10, HarvestClock.ticksOf(BuiltInRoles.parse("harvester").params, 2.0))
        assertEquals(10, HarvestClock.ticksOf(BuiltInRoles.parse("picker").params, 2.0))
        assertEquals(10, FellClock.ticksPerLog(1, LumberjackParams.of(BuiltInRoles.parse("lumberjack").params), 2.0))
        assertEquals(20, SoulCollectorParams.of(BuiltInRoles.parse("soul_collector").params).unitsPerBatch(100, 2.0))
        assertEquals(10, FreezePace.intervalTicks(100, FreezerParams.of(BuiltInRoles.parse("freezer").params).cellsPerMinuteAt100, 2.0))
        assertEquals(5, BuddingRules.intervalOf(BuiltInRoles.parse("crystal_tender").params, 2.0))
        assertEquals(
            3_000,
            HeaterMath.heatPerTick(HeaterParams.of(BuiltInRoles.parse("heater_mekanism").params).heatPerTickAt100, 100, 2.0),
        )
        assertEquals(
            20.0,
            PncHeatMath.heatPerTick(PncHeaterParams.of(BuiltInRoles.parse("heater_pneumaticcraft").params), 300.0, 100, 2.0),
            1e-9,
        )
        val museParams = BuiltInRoles.parse("muse").params
        assertEquals(8, MuseBonus.bonus(25, museParams, apothicEnchanting = false, multiplier = 2.0).bookshelves)
        assertEquals(90f, MuseBonus.bonus(100, museParams, apothicEnchanting = true, multiplier = 2.0).quanta, 1e-6f)
    }
}
