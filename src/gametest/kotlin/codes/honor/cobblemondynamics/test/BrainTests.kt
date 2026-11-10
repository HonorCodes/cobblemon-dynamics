package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.ai.DynamicsActivities
import codes.honor.cobblemondynamics.ai.LookMath
import codes.honor.cobblemondynamics.ai.WorkLookTask
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.CobblemonBehaviours
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.nio.file.Files
import kotlin.math.abs

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class BrainTests {
    /** A Work Mode pasture at (7,1,7) with one Charmander, and a furnace with 8 raw iron at (7,1,3). */
    private fun stoker(helper: GameTestHelper): Pair<PokemonEntity, AbstractFurnaceBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        return TestKit.tether(helper, pasture, owner, "charmander level=10") to furnace
    }

    private fun status(helper: GameTestHelper, entity: PokemonEntity): WorkerStatus? = TestKit.workerOf(helper, entity)?.status

    private fun isHolding(status: WorkerStatus?) = status == WorkerStatus.WORKING || status == WorkerStatus.STANDBY

    /** How far the body yaw is from the direction to [target], in degrees. */
    private fun yawError(entity: PokemonEntity, target: Vec3): Float =
        abs(Mth.wrapDegrees(entity.yBodyRot - LookMath.yawTowards(entity.position(), target)))

    /** The worker walks toward the furnace, so the test turns it away once and the bridge must turn it back. */
    private fun turnAway(entity: PokemonEntity) {
        entity.yRot += 180f
        entity.yBodyRot += 180f
        entity.yHeadRot += 180f
    }

    /** Cobblemon's `look_at_target` task wraps vanilla's `LookAtTargetSink`. */
    private fun isCobblemonLookTask(task: BehaviorControl<*>): Boolean =
        task is LookAtTargetSink || (task as? WrapperLivingEntityTask<*>)?.task is LookAtTargetSink

    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdAi01BrainListsWorkActivity(helper: GameTestHelper) {
        val file = ModList.get().getModFileById(MOD_ID).file.findResource("data", MOD_ID, "behaviours", "pokemon", "auto", "work.json")
        helper.assertTrue(Files.exists(file), "the mod holds no data/$MOD_ID/behaviours/pokemon/auto/work.json")
        val behaviour = CobblemonBehaviours.behaviours[id("work")]
        helper.assertTrue(
            behaviour != null && behaviour in CobblemonBehaviours.autoPokemonBehaviours,
            "Cobblemon did not load the work behaviour as an auto behaviour",
        )
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        fire.remakeBrain()
        helper.assertTrue(
            DynamicsActivities.WORK.get() in TestKit.activitiesOf(fire.brain),
            "the brain of a newly pastured Charmander lacks the work activity",
        )
        helper.succeed()
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdAi02WorkingWorkerHoldsItsSpot(helper: GameTestHelper) {
        val (fire, _) = stoker(helper)
        var spot: Vec3? = null
        var since = -1L
        helper.onEachTick {
            val held = spot ?: run {
                val worker = TestKit.workerOf(helper, fire)
                if (worker?.status != WorkerStatus.WORKING) return@onEachTick
                since = helper.tick
                Vec3.atBottomCenterOf(worker.spot ?: throw GameTestAssertException("a Working worker with no spot")).also { spot = it }
            }
            val off = fire.position().distanceTo(held)
            helper.assertTrue(off <= 0.5, "the worker is $off blocks from its spot ${helper.tick - since} ticks after it started working")
            if (helper.tick - since >= 600) helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAi03ReleaseRestoresActivitiesWithin20Ticks(helper: GameTestHelper) {
        val (fire, furnace) = stoker(helper)
        var releasedAt = -1L
        helper.onEachTick {
            if (releasedAt < 0) {
                if (status(helper, fire) != WorkerStatus.WORKING) return@onEachTick
                helper.assertTrue(BrainBridge.isWorking(fire), "the work activity is not active while the worker is Working")
                TestKit.release(helper, fire, furnace.blockPos)
                releasedAt = helper.tick
            } else if (helper.tick - releasedAt == 20L) {
                helper.assertFalse(BrainBridge.isWorking(fire), "the work activity is still active 20 ticks after the release")
                helper.assertTrue(fire.brain.isActive(Activity.IDLE), "Cobblemon's idle activity is not back 20 ticks after the release")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAi04RemakeBrainRestoresWorkNextTick(helper: GameTestHelper) {
        val (fire, _) = stoker(helper)
        var remadeAt = -1L
        helper.onEachTick {
            if (remadeAt < 0) {
                if (status(helper, fire) != WorkerStatus.WORKING || !BrainBridge.isWorking(fire)) return@onEachTick
                fire.remakeBrain()
                helper.assertFalse(BrainBridge.isWorking(fire), "the rebuilt brain kept the work activity")
                remadeAt = helper.tick
            } else if (helper.tick - remadeAt == 1L) {
                helper.assertTrue(BrainBridge.isWorking(fire), "the work activity is not active 1 tick after remakeBrain()")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdAi05FacesTheTarget(helper: GameTestHelper) {
        val (fire, furnace) = stoker(helper)
        val center = Vec3.atCenterOf(furnace.blockPos)
        var turnedAt = -1L
        helper.onEachTick {
            val status = status(helper, fire)
            if (!isHolding(status)) {
                helper.assertTrue(turnedAt < 0, "the worker left Working and Standby: $status")
                return@onEachTick
            }
            val error = yawError(fire, center)
            helper.assertTrue(error <= 15f, "the body yaw is $error degrees off the furnace center while $status")
            if (turnedAt < 0) {
                turnAway(fire)
                turnedAt = helper.tick
            } else if (helper.tick - turnedAt >= 600) {
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_400)
    fun cdAi06NonWorkersKeepCobblemonActivities(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val pastured = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val wild = TestKit.wild(helper, BlockPos(3, 1, 11), "charmander level=10")
        val entities = listOf("wild" to wild, "pastured" to pastured)
        helper.onEachTick {
            for ((name, entity) in entities) {
                helper.assertFalse(
                    BrainBridge.isWorking(entity),
                    "the $name Charmander runs the work activity",
                )
            }
        }
        helper.runAtTickTime(1_200) {
            // The behaviour gives every Pokémon the activity; only a work order may start it.
            for ((name, entity) in entities) {
                helper.assertTrue(
                    DynamicsActivities.WORK.get() in TestKit.activitiesOf(entity.brain),
                    "the $name Charmander lacks the activity",
                )
            }
            helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdAi09LookTaskRunsInsideWork(helper: GameTestHelper) {
        val (fire, furnace) = stoker(helper)
        val center = Vec3.atCenterOf(furnace.blockPos)
        val work = DynamicsActivities.WORK.get()
        var workSince = -1L
        var turnedAt = -1L
        helper.onEachTick {
            if (!BrainBridge.isWorking(fire)) {
                helper.assertTrue(workSince < 0, "the work activity stopped while the worker held its claim")
                return@onEachTick
            }
            if (workSince < 0) workSince = helper.tick
            val brain = fire.brain
            val running = TestKit.runningTasks(brain)
            helper.assertTrue(TestKit.tasksOf(brain, work).any { it is WorkLookTask }, "the work activity holds no look task")
            helper.assertFalse(brain.isActive(Activity.IDLE), "Cobblemon's idle activity runs beside the work activity")
            helper.assertFalse(running.any(::isCobblemonLookTask), "Cobblemon's idle look task runs inside the work activity")
            val status = status(helper, fire)
            if (!isHolding(status)) return@onEachTick
            // The brain ticks before the engine, so the look task starts on the tick after the activity does.
            if (helper.tick > workSince) helper.assertTrue(running.any { it is WorkLookTask }, "the look task is not running")
            val error = yawError(fire, center)
            helper.assertTrue(error <= 15f, "the body yaw is $error degrees off the furnace center while $status")
            if (turnedAt < 0) {
                turnAway(fire)
                turnedAt = helper.tick
            } else if (helper.tick - turnedAt >= 600) {
                helper.succeed()
            }
        }
    }
}
