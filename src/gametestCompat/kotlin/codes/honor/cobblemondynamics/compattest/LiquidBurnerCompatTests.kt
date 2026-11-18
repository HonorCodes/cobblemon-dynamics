package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlockEntity
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Superheater on the real Liquid Blaze Burner of Create Crafts & Additions 1.7.2 (CD-MCH-01). The burner is no Create Blaze Burner,
 * but its block state carries Create's heat level, which a basin above reads. It burns the fluid in its tank: lava is its normal fuel,
 * which reads `KINDLED`, and with no fuel it reads `SMOULDERING` and writes its heat into its block state on each of its own ticks.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class LiquidBurnerCompatTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val burnerRel = BlockPos(7, 2, 5)

    /** A twin burner with no heat user above it, so no Superheater can claim it (CD-BRN-03). Its fuel burns as the worked one's. */
    private val twinRel = BlockPos(11, 2, 5)

    /**
     * CD-MCH-01: a level 50 Dragonair at a liquid burner under a basin. The burner and its twin each take 1,000 mB of lava at the same
     * tick, their own fuel. From 1 tick after the Dragonair starts Working, the burner reads `SEETHING` on each tick for 100 ticks, while
     * its fuel type, its burn time and its tank equal the twin's on every tick of the test, and the unworked twin reads `KINDLED`.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = TIMEOUT_TICKS)
    fun cdMch01Lv50DragonairHoldsTheLiquidBurnerSeething(helper: GameTestHelper) {
        assertActive(helper)
        val scene = scene(helper)
        val burner = burner(helper, burnerRel, LAVA_MB)
        val twin = burner(helper, twinRel, LAVA_MB)
        helper.setBlock(burnerRel.above(), AllBlocks.BASIN.defaultState)
        val dragonair = TestKit.tether(helper, scene.pasture, scene.owner, "dragonair level=50")
        var seething = 0
        failAtTimeout(helper, dragonair, burner) { "after $seething ticks of SEETHING" }
        helper.onEachTick {
            assertSameFuel(helper, burner, twin)
            helper.assertFalse(isClaimed(helper, dragonair, twin), "the Dragonair claimed the twin, which has no heat user above")
            val worked = workedTicks(helper, dragonair, burner) ?: return@onEachTick
            if (worked < 1) return@onEachTick
            val heat = heatAt(helper, burnerRel)
            helper.assertTrue(heat == HeatLevel.SEETHING, "CD-MCH-01: the burner reads $heat $worked ticks after a Lv50 Superheater began")
            val own = heatAt(helper, twinRel)
            helper.assertTrue(own == HeatLevel.KINDLED, "the unworked twin reads $own on its lava")
            if (++seething >= HOLD_TICKS) helper.succeed()
        }
    }

    /**
     * CD-MCH-01 below level 50: a level 10 Dratini at an unfueled liquid burner under a basin, whose own heat is `SMOULDERING`. From 1
     * tick after the Dratini starts Working, the burner reads `KINDLED` on each tick for 100 ticks, and on every tick of the test it
     * has no fuel type, no burn time and an empty tank.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = TIMEOUT_TICKS)
    fun cdMch01Lv10DratiniHoldsAnUnfueledLiquidBurnerKindled(helper: GameTestHelper) {
        assertActive(helper)
        val scene = scene(helper)
        val burner = burner(helper, burnerRel, 0)
        helper.setBlock(burnerRel.above(), AllBlocks.BASIN.defaultState)
        val dratini = TestKit.tether(helper, scene.pasture, scene.owner, "dratini level=10")
        var kindled = 0
        failAtTimeout(helper, dratini, burner) { "after $kindled ticks of KINDLED" }
        helper.onEachTick {
            val fuel = burner.getActiveFuel()
            val left = burner.getRemainingBurnTime()
            val tank = tankOf(helper, burner)
            helper.assertTrue(
                fuel == LiquidBlazeBurnerBlockEntity.FuelType.NONE && left == 0 && tank.isEmpty,
                "CD-MCH-01: the unfueled burner holds fuel $fuel, $left burn ticks and ${tank.amount} mB",
            )
            val worked = workedTicks(helper, dratini, burner) ?: return@onEachTick
            if (worked < 1) return@onEachTick
            val heat = heatAt(helper, burnerRel)
            helper.assertTrue(heat == HeatLevel.KINDLED, "CD-MCH-01: the burner reads $heat $worked ticks after a Lv10 Superheater started")
            if (++kindled >= HOLD_TICKS) helper.succeed()
        }
    }

    /** Fails 10 ticks before the timeout with what the worker and the burner show, so that a stalled test says where it stopped. */
    private fun failAtTimeout(helper: GameTestHelper, entity: PokemonEntity, burner: LiquidBlazeBurnerBlockEntity, progress: () -> String) {
        helper.runAtTickTime(TIMEOUT_TICKS - 10L) {
            val worker = TestKit.workerOf(helper, entity)
            helper.fail(
                "the ${entity.pokemon.species.name} is ${worker?.status} at ${TestKit.relative(helper, entity.blockPosition())}, " +
                    "claim ${TestKit.claimOf(helper, entity)?.key}; the burner reads ${heatAt(helper, burnerRel)} with fuel " +
                    "${burner.getActiveFuel()}, ${progress()}",
            )
        }
    }

    private fun assertActive(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.LIQUID_BLAZE_BURNER), "the Liquid Blaze Burner mixin did not apply")
        helper.assertTrue(RoleRegistry.current.get(ROLE) != null, "the role $ROLE is not active")
    }

    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** A liquid burner at [rel] with [lava] mB of lava in its tank, filled through its fluid handler, as a pipe fills it. */
    private fun burner(helper: GameTestHelper, rel: BlockPos, lava: Int): LiquidBlazeBurnerBlockEntity {
        helper.setBlock(rel, BuiltInRegistries.BLOCK.getOptional(BURNER).orElseThrow { GameTestAssertException("no block $BURNER") })
        val burner = helper.getBlockEntity<LiquidBlazeBurnerBlockEntity>(rel)
        if (lava > 0) {
            val tank = helper.level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(rel), Direction.UP)
                ?: throw GameTestAssertException("the liquid burner at $rel has no fluid handler")
            val filled = tank.fill(FluidStack(Fluids.LAVA, lava), IFluidHandler.FluidAction.EXECUTE)
            helper.assertTrue(filled == lava, "the liquid burner at $rel took $filled mB of lava")
        }
        return burner
    }

    private fun tankOf(helper: GameTestHelper, burner: LiquidBlazeBurnerBlockEntity): FluidStack =
        helper.level.getCapability(Capabilities.FluidHandler.BLOCK, burner.blockPos, Direction.UP)?.getFluidInTank(0)
            ?: throw GameTestAssertException("the liquid burner has no fluid handler")

    /** CD-MCH-01: the worked burner's own fuel follows the twin's: the same fuel type, burn time and tank on every tick. */
    private fun assertSameFuel(helper: GameTestHelper, burner: LiquidBlazeBurnerBlockEntity, twin: LiquidBlazeBurnerBlockEntity) {
        val fuel = burner.getActiveFuel() to twin.getActiveFuel()
        val left = burner.getRemainingBurnTime() to twin.getRemainingBurnTime()
        val tank = tankOf(helper, burner).amount to tankOf(helper, twin).amount
        helper.assertTrue(
            fuel.first == fuel.second && left.first == left.second && tank.first == tank.second,
            "CD-MCH-01: the worked burner's fuel changed: fuel $fuel, burn ticks $left, tank $tank (worked, twin)",
        )
    }

    /** The heat level in the block state at [rel], which the basin above reads. */
    private fun heatAt(helper: GameTestHelper, rel: BlockPos): HeatLevel = helper.getBlockState(rel).getValue(BlazeBurnerBlock.HEAT_LEVEL)

    private fun isClaimed(helper: GameTestHelper, entity: PokemonEntity, burner: LiquidBlazeBurnerBlockEntity): Boolean =
        TestKit.claimOf(helper, entity)?.key == TestKit.keyOf(helper, burner.blockPos)

    /** How many ticks [entity] has worked [burner] for, or null while it does not. */
    private fun workedTicks(helper: GameTestHelper, entity: PokemonEntity, burner: LiquidBlazeBurnerBlockEntity): Long? {
        val claim = TestKit.claimOf(helper, entity)
        val worker = TestKit.workerOf(helper, entity)
        if (claim?.kind != ClaimKind.WORK || claim.key != TestKit.keyOf(helper, burner.blockPos)) return null
        if (worker?.status != WorkerStatus.WORKING) return null
        return helper.level.gameTime - worker.statusSince
    }

    private companion object {
        const val TIMEOUT_TICKS = 800
        const val LAVA_MB = 1_000

        /** The ticks of work for which a test reads the heat on every tick. */
        const val HOLD_TICKS = 100

        val ROLE: ResourceLocation = id("superheater_createaddition")
        val BURNER: ResourceLocation = ResourceLocation.fromNamespaceAndPath("createaddition", "liquid_blaze_burner")
    }
}
