package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
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

    /**
     * An empty Blaze Burner with a basin above, and a Charmander beside it for 400 ticks: no claim, and the Work Leash finds no role for
     * it (CD-BRN-02). Then the burner is filled, and the Charmander claims it, which shows that the empty one was in its reach.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 700)
    fun cdBrn02EmptyBurnerIsNeverClaimed(helper: GameTestHelper) {
        val scene = scene(helper)
        helper.setBlock(burnerRel, AllBlocks.BLAZE_BURNER.defaultState.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE))
        CreateKit.basin(helper, aboveRel)
        val burner = helper.absolutePos(burnerRel)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        TestKit.moveTo(helper, fire, BlockPos(7, 2, 7))
        val binding = TestKit.bind(helper, scene.owner, fire, burnerRel)
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(binding == null, "CD-BRN-02: the leash bound the Charmander to the empty burner: $binding")
        helper.assertTrue(reply == "No role fits", "CD-BRN-02: the click on the empty burner answered '$reply'")
        var filled = false
        helper.onEachTick {
            val claim = TestKit.claimOf(helper, fire)
            if (!filled) {
                helper.assertFalse(claim?.key == TestKit.keyOf(helper, burner), "CD-BRN-02: the Charmander claimed the empty burner")
                if (helper.tick < 400) return@onEachTick
                CreateKit.filledBurner(helper, burnerRel)
                filled = true
                return@onEachTick
            }
            if (claim?.key == TestKit.keyOf(helper, burner)) helper.succeed()
        }
    }

    /** The basin above a worked burner is removed, and its worker is in Standby within 20 ticks (CD-BRN-03). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBrn03NoBasinMeansStandby(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var removedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            if (removedAt < 0) {
                val worked = workedTicks(helper, fire, burner) ?: return@onEachTick
                if (worked < 1) return@onEachTick
                helper.setBlock(aboveRel, Blocks.AIR)
                removedAt = helper.tick
            } else if (worker.status == WorkerStatus.STANDBY) {
                helper.assertTrue(
                    TestKit.claimOf(helper, fire)?.key == TestKit.keyOf(helper, burner.blockPos),
                    "the worker left its burner",
                )
                helper.succeed()
            } else {
                val waited = helper.tick - removedAt
                helper.assertTrue(waited < 20, "CD-BRN-03: $waited ticks after the basin went the worker is ${worker.status}")
            }
        }
    }

    /**
     * Two burners, each with 1,000 ticks of its own normal fuel and a basin above. A Charmander bound to the first heats it for 400
     * ticks, and the two remaining burn times are equal: the worker neither burns nor adds fuel (CD-BRN-04).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdBrn04FuelBurnsDownAsNormal(helper: GameTestHelper) {
        val scene = scene(helper)
        val otherRel = BlockPos(11, 2, 5)
        val worked = fueledBurner(helper, burnerRel)
        val unworked = fueledBurner(helper, otherRel)
        CreateKit.basin(helper, aboveRel)
        CreateKit.basin(helper, otherRel.above())
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val binding = TestKit.bind(helper, scene.owner, fire, burnerRel)
        helper.assertTrue(binding?.target == worked.blockPos, "the Charmander did not bind to the first burner: $binding")
        helper.onEachTick {
            helper.assertTrue(
                TestKit.claimOf(helper, fire)?.key != TestKit.keyOf(helper, unworked.blockPos),
                "the worker took the second burner",
            )
            val ticks = workedTicks(helper, fire, worked) ?: return@onEachTick
            val left = worked.getRemainingBurnTime()
            val other = unworked.getRemainingBurnTime()
            helper.assertTrue(left == other, "CD-BRN-04: the burn times read $left and $other")
            if (ticks >= 400) {
                helper.assertTrue(left > 0, "the burners ran out of fuel before 400 ticks of work")
                helper.succeed()
            }
        }
    }

    /** A filled burner at [rel] with 1,000 ticks of normal fuel, as Create saves one that burns coal. */
    private fun fueledBurner(helper: GameTestHelper, rel: BlockPos): BlazeBurnerBlockEntity {
        val burner = CreateKit.filledBurner(helper, rel)
        val registries = helper.level.registryAccess()
        val saved = burner.saveWithFullMetadata(registries)
        saved.putInt("fuelLevel", BlazeBurnerBlockEntity.FuelType.NORMAL.ordinal)
        saved.putInt("burnTimeRemaining", FUEL_TICKS)
        burner.loadWithComponents(saved, registries)
        val fuel = burner.getActiveFuel()
        helper.assertTrue(fuel == BlazeBurnerBlockEntity.FuelType.NORMAL, "the burner at $rel holds $fuel")
        val left = burner.getRemainingBurnTime()
        helper.assertTrue(left == FUEL_TICKS, "the burner at $rel holds $left ticks of fuel")
        return burner
    }

    private companion object {
        const val FUEL_TICKS = 1_000
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
