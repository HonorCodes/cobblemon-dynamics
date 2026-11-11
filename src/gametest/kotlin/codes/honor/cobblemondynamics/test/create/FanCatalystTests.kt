package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.create.FanState
import codes.honor.cobblemondynamics.handler.create.FireMode
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * Pastured Fan Catalysts that the owner binds with the Work Leash to Create encased fans: the work spot, the demand, the flip and the
 * exemption of the worker from its own air current. The fans sit at x = 3 and face east, over the stone floor.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class FanCatalystTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    /** A Work Mode pasture at (11,1,5) with its owner beside it, within 8 blocks of every fan, and out of every fan's current. */
    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 5), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /**
     * A Charmander bound to a fan that faces east takes the block east of the fan as its spot and Works while 64 raw iron sit on a
     * depot downstream (CD-FAN-03). With the motor stopped, it waits in Standby within 20 ticks. With the motor on again and the
     * depot emptied, it waits in Standby within 20 ticks too (CD-FAN-15).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1000)
    fun cdFan03Fan15TheWorkerStandsInFrontAndWaitsWithNoWork(helper: GameTestHelper) {
        val fanRel = BlockPos(3, 2, 7)
        CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        val motor = helper.getBlockEntity<CreativeMotorBlockEntity>(fanRel.west())
        val depot = CreateKit.depot(helper, BlockPos(6, 1, 7), ItemStack(Items.RAW_IRON, 64))
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=20")
        fun status() = TestKit.workerOf(helper, charmander)?.status
        var since = 0L
        helper.startSequence()
            .thenIdle(1)
            .thenExecute { TestKit.bind(helper, scene.owner, charmander, fanRel) ?: throw GameTestAssertException("no binding") }
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Charmander is ${status()}") }
            .thenExecute {
                val spot = TestKit.workerOf(helper, charmander)?.spot
                helper.assertTrue(spot == helper.absolutePos(fanRel.east()), "CD-FAN-03: the spot is $spot")
                CreateKit.setRpm(motor, 0)
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Charmander is ${status()} with the fan stopped")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-FAN-15: no Standby within 20 ticks of the stop")
            }
            .thenExecute { CreateKit.setRpm(motor, 64) }
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Charmander is ${status()} with the fan on again") }
            .thenExecute {
                depot.setHeldItem(ItemStack.EMPTY)
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Charmander is ${status()} with no item in the current")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-FAN-15: no Standby within 20 ticks of the last item")
            }
            .thenSucceed()
    }

    /**
     * The binder sneak-clicks a worked fan twice: smoking, then blasting, each with its action-bar message (CD-FAN-10, CD-FAN-16).
     * With the claim released, the binder's sneak-click on the fan that no worker drives changes nothing (CD-SEC-10).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFan10Fan16Sec10TheBinderFlipsOnlyADrivenFan(helper: GameTestHelper) {
        val fanRel = BlockPos(3, 2, 7)
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        CreateKit.depot(helper, BlockPos(6, 1, 7), ItemStack(Items.RAW_IRON, 64))
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=20")
        fun mode() = FanState.of(fan)?.fireMode
        fun flip() {
            TestKit.leash(scene.owner)
            TestKit.clickBlock(helper, scene.owner, fanRel, sneak = true)
        }
        helper.startSequence()
            .thenIdle(1)
            .thenExecute { TestKit.bind(helper, scene.owner, charmander, fanRel) ?: throw GameTestAssertException("no binding") }
            .thenWaitUntil { helper.assertTrue(TestKit.workerOf(helper, charmander)?.status == WorkerStatus.WORKING, "not Working") }
            .thenExecute {
                flip()
                helper.assertTrue(mode() == FireMode.SMOKING, "CD-FAN-10: the first flip left ${mode()}")
                helper.assertTrue(TestKit.lastMessage(scene.owner) == "Fan mode: Smoking", "CD-FAN-16: ${TestKit.lastMessage(scene.owner)}")
                flip()
                helper.assertTrue(mode() == FireMode.BLASTING, "CD-FAN-10: the second flip left ${mode()}")
                helper.assertTrue(
                    TestKit.lastMessage(scene.owner) == "Fan mode: Blasting",
                    "CD-FAN-16: ${TestKit.lastMessage(scene.owner)}",
                )
                TestKit.release(helper, charmander, helper.absolutePos(fanRel))
            }
            .thenIdle(3)
            .thenExecute {
                flip()
                helper.assertTrue(mode() == FireMode.BLASTING, "CD-SEC-10: a fan that no worker drives flipped to ${mode()}")
            }
            .thenSucceed()
    }

    /**
     * Three fans at 256 RPM, with a bound Charmander, Squirtle and Gastly, each with 64 items on a depot downstream. Over 1,200 ticks
     * of Working each, no worker moves more than 0.5 blocks from its spot, takes damage, burns, or gains Blindness or Slowness
     * (CD-FAN-12). The test counts each worker's Working ticks, so a worker that never Works fails it.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1800)
    fun cdFan12ThreeWorkersStayPutAt256Rpm(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.AIR_CURRENT), "the air current mixin did not apply")
        val scene = scene(helper)
        val workers = listOf(
            Triple("charmander level=20", 3, ItemStack(Items.RAW_IRON, 64)),
            Triple("squirtle level=20", 7, ItemStack(Items.RED_WOOL, 64)),
            Triple("gastly level=20", 11, ItemStack(Items.SAND, 64)),
        ).map { (spec, z, stack) ->
            CreateKit.fan(helper, BlockPos(3, 2, z), Direction.EAST, rpm = 256)
            CreateKit.depot(helper, BlockPos(6, 1, z), stack)
            BlockPos(3, 2, z) to TestKit.tether(helper, scene.pasture, scene.owner, spec)
        }
        val working = IntArray(workers.size)
        helper.runAfterDelay(1) { for ((fanRel, worker) in workers) TestKit.bind(helper, scene.owner, worker, fanRel) }
        helper.onEachTick {
            workers.forEachIndexed { i, (fanRel, worker) ->
                val state = TestKit.workerOf(helper, worker)
                val spot = state?.takeIf { it.status == WorkerStatus.WORKING || it.status == WorkerStatus.STANDBY }?.spot
                    ?: return@forEachIndexed
                val name = worker.pokemon.species.name
                helper.assertTrue(spot == helper.absolutePos(fanRel.east()), "$name has the spot $spot")
                helper.assertTrue(worker.position().distanceTo(Vec3.atBottomCenterOf(spot)) <= 0.5, "CD-FAN-12: the fan moved $name")
                helper.assertTrue(
                    worker.health >= worker.maxHealth && worker.remainingFireTicks <= 0,
                    "CD-FAN-12: $name took damage or burns",
                )
                val effects = worker.hasEffect(MobEffects.BLINDNESS) || worker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                helper.assertFalse(effects, "CD-FAN-12: $name gained Blindness or Slowness")
                if (state.status == WorkerStatus.WORKING) working[i]++
            }
        }
        helper.succeedWhen {
            for ((i, pair) in workers.withIndex()) {
                val name = pair.second.pokemon.species.name
                helper.assertTrue(working[i] >= 1200, "CD-FAN-12: $name has Worked ${working[i]} of 1,200 ticks")
            }
        }
    }
}
