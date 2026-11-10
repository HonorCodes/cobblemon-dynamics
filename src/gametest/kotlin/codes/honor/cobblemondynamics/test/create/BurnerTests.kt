package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The Burner Keeper and the Superheater on real Blaze Burners, each filled, with no fuel, and with a basin or a fluid tank above. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class BurnerTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val burnerRel = BlockPos(7, 2, 5)
    private val aboveRel = BlockPos(7, 3, 5)

    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** How many ticks [entity] has worked [burner] for, or null while it does not. */
    private fun workedTicks(helper: GameTestHelper, entity: PokemonEntity, burner: BlazeBurnerBlockEntity): Long? {
        val claim = TestKit.claimOf(helper, entity)
        val worker = TestKit.workerOf(helper, entity)
        if (claim?.kind != ClaimKind.WORK || claim.key != TestKit.keyOf(helper, burner.blockPos)) return null
        if (worker?.status != WorkerStatus.WORKING) return null
        return helper.level.gameTime - worker.statusSince
    }

    /** CD-BRN-01, then CD-BRN-06 on the same burner: heat 1 tick after Working starts, and the burner's own heat 2 ticks after release. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBrn01Brn06KindledWhileWorkedSmoulderingAfterRelease(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var releasedAt = -1L
        helper.onEachTick {
            if (releasedAt < 0) {
                val worked = workedTicks(helper, fire, burner) ?: return@onEachTick
                if (worked < 1) return@onEachTick
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(heat.isAtLeast(HeatLevel.KINDLED), "the burner reads $heat $worked ticks after Working starts")
                TestKit.release(helper, fire, burner.blockPos)
                releasedAt = helper.tick
            } else if (helper.tick - releasedAt >= 2) {
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(heat == HeatLevel.SMOULDERING, "the burner reads $heat 2 ticks after the release")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBrn05BoilerCountsOneHeat(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.fluidTank(helper, aboveRel)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        helper.succeedWhen {
            val worked = workedTicks(helper, fire, burner)
            helper.assertTrue(worked != null && worked >= 1, "the Charmander does not work the burner under the boiler")
            assertBoiler(helper, HeatLevel.KINDLED, 1)
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdSup01Lv50Seething(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, "dragonair level=50")
        helper.onEachTick {
            val worked = workedTicks(helper, dragon, burner) ?: return@onEachTick
            if (worked < 1) return@onEachTick
            val heat = CreateKit.heatAt(helper, burnerRel)
            helper.assertTrue(heat == HeatLevel.SEETHING, "the burner reads $heat $worked ticks after a Lv50 Superheater starts")
            helper.succeed()
        }
    }

    /**
     * A Lv49 Dragonair holds the burner under a boiler at KINDLED with 1 heat for 20 ticks (CD-SUP-04); then it levels up through
     * Cobblemon, and the burner turns SEETHING with 2 heat (CD-SUP-04, CD-SUP-03).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdSup03Sup04Lv49ThenLv50(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.fluidTank(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, "dragonair level=49")
        var kindledSince = -1L
        var leveledAt = -1L
        helper.onEachTick {
            if (leveledAt < 0) {
                val worked = workedTicks(helper, dragon, burner) ?: return@onEachTick
                if (worked < 1) return@onEachTick
                assertBoiler(helper, HeatLevel.KINDLED, 1)
                if (kindledSince < 0) kindledSince = helper.tick
                if (helper.tick - kindledSince < 20) return@onEachTick
                val stored = Cobblemon.storage.getPC(scene.owner)[dragon.pokemon.uuid]
                    ?: throw GameTestAssertException("no Dragonair in the PC")
                stored.levelUp(SidemodExperienceSource(MOD_ID))
                helper.assertTrue(stored.level == 50, "the Dragonair is level ${stored.level} after its level-up")
                leveledAt = helper.tick
            } else if (CreateKit.heatAt(helper, burnerRel) != HeatLevel.SEETHING) {
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(helper.tick - leveledAt < 5, "the burner reads $heat ${helper.tick - leveledAt} ticks after Lv50")
            } else {
                assertBoiler(helper, HeatLevel.SEETHING, 2)
                helper.succeed()
            }
        }
    }

    private fun assertBoiler(helper: GameTestHelper, expected: HeatLevel, heat: Int) {
        val level = CreateKit.heatAt(helper, burnerRel)
        helper.assertTrue(level == expected, "the burner under the boiler reads $level, expected $expected")
        val heater = CreateKit.heaterHeat(helper, burnerRel)
        helper.assertTrue(heater == heat.toFloat(), "Create's boiler heater registry gives the burner $heater heat, expected $heat")
        val boiler = CreateKit.boilerHeat(helper, aboveRel)
        helper.assertTrue(boiler == heat, "the boiler counts $boiler heat from the burner, expected $heat")
    }
}
