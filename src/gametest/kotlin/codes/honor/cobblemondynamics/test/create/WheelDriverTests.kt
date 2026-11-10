package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.create.DriveBehaviour
import codes.honor.cobblemondynamics.handler.create.DriveNbt
import codes.honor.cobblemondynamics.handler.create.RealFlowAccess
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/** The Wheel Driver on real Create wheels. Each wheel stands on the stone floor with no water nearby, unless the test adds it. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WheelDriverTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val wheelRel = BlockPos(7, 2, 5)

    /** A Work Mode pasture at [rel], south of the wheels, with its owner beside it. */
    private fun scene(helper: GameTestHelper, rel: BlockPos = BlockPos(7, 1, 9)): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, rel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    private fun squirtle(helper: GameTestHelper, scene: Scene): PokemonEntity =
        TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, wheel: WaterWheelBlockEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, wheel.blockPos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdWhl01Whl02SmallAndLargeWheelsTurnAtCreateValues(helper: GameTestHelper) {
        val scene = scene(helper, BlockPos(7, 1, 10))
        val small = CreateKit.smallWheel(helper, BlockPos(4, 2, 5))
        val large = CreateKit.largeWheel(helper, BlockPos(10, 3, 5))
        val workers = List(2) { squirtle(helper, scene) }
        helper.succeedWhen {
            for ((wheel, name) in listOf(small to "small", large to "large")) {
                helper.assertTrue(workers.any { isWorkingOn(helper, it, wheel) }, "no Squirtle works the dry $name wheel")
            }
            helper.assertTrue(small.getSpeed() == 8f, "the small wheel turns at ${small.getSpeed()} RPM")
            helper.assertTrue(CreateKit.capacity(small) == 256f, "the small wheel adds ${CreateKit.capacity(small)} SU")
            helper.assertTrue(CreateKit.networkCapacity(small) == 256f, "the small network has ${CreateKit.networkCapacity(small)} SU")
            helper.assertTrue(large.getSpeed() == 4f, "the large wheel turns at ${large.getSpeed()} RPM")
            helper.assertTrue(CreateKit.capacity(large) == 512f, "the large wheel adds ${CreateKit.capacity(large)} SU")
            helper.assertTrue(CreateKit.networkCapacity(large) == 512f, "the large network has ${CreateKit.networkCapacity(large)} SU")
        }
    }

    /**
     * The source on the wheel's top can only flow north, over the edge, into a walled one-block column on the wheel's north side.
     * Both cells turn the wheel the same way, so Create sees real flow, and the walls keep the water where it is until the end.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdWhl03RealWaterMeansStandby(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val walls = listOf(BlockPos(7, 3, 6), BlockPos(6, 3, 5), BlockPos(8, 3, 5), BlockPos(6, 2, 4), BlockPos(8, 2, 4), BlockPos(7, 2, 3))
        walls.forEach { helper.setBlock(it, Blocks.STONE) }
        val entity = squirtle(helper, scene)
        var workingSince = -1L
        var wateredAt = -1L
        var standbySince = -1L
        var stamina = 0.0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, entity) ?: return@onEachTick
            when {
                wateredAt < 0 -> {
                    if (workingSince < 0 && isWorkingOn(helper, entity, wheel)) workingSince = helper.tick
                    if (workingSince < 0) return@onEachTick
                    helper.assertTrue(worker.status == WorkerStatus.WORKING, "the Squirtle is ${worker.status} at a dry wheel")
                    if (helper.tick - workingSince >= 40) {
                        helper.setBlock(BlockPos(7, 3, 5), Blocks.WATER)
                        wateredAt = helper.tick
                    }
                }

                standbySince < 0 -> if (worker.status == WorkerStatus.STANDBY) {
                    val real = (wheel as RealFlowAccess).`cobblemon_dynamics$realFlowScore`()
                    helper.assertTrue(real != 0, "the Squirtle waits, but Create sees no real flow")
                    standbySince = helper.tick
                    stamina = worker.stamina.fraction
                } else {
                    helper.assertTrue(helper.tick - wateredAt < 20, "the Squirtle is ${worker.status} 20 ticks after the water")
                }

                else -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "the Squirtle left Standby: ${worker.status}")
                    helper.assertTrue(worker.stamina.fraction >= stamina, "Work Stamina drained in Standby: ${worker.stamina}")
                    stamina = worker.stamina.fraction
                    if (helper.tick - standbySince >= 40) helper.succeed()
                }
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdWhl05BinderFlips(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val entity = squirtle(helper, scene)
        val binding = TestKit.bind(helper, scene.owner, entity, wheelRel)
        helper.assertTrue(binding?.target == wheel.blockPos, "no binding to the wheel: $binding")
        var clickedAt = -1L
        helper.onEachTick {
            if (clickedAt < 0) {
                if (isWorkingOn(helper, entity, wheel) && wheel.getSpeed() == 8f) {
                    val result = TestKit.clickBlock(helper, scene.owner, wheelRel, sneak = true)
                    helper.assertTrue(result.consumesAction(), "the binder's sneak-click returned $result")
                    clickedAt = helper.tick
                }
            } else if (helper.tick - clickedAt >= 2) {
                helper.assertTrue(wheel.getSpeed() == -8f, "the wheel turns at ${wheel.getSpeed()} RPM 2 ticks after the binder's flip")
                helper.succeed()
            }
        }
    }

    /** Worker B takes over from the side opposite to worker A's. The binding fixes each worker's spot: the nearest to it. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdWhl06TakeoverKeepsDirection(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val a = squirtle(helper, scene)
        val b = squirtle(helper, scene)
        TestKit.moveTo(helper, a, BlockPos(7, 2, 2))
        TestKit.bind(helper, scene.owner, a, wheelRel)
        var step = "a"
        helper.onEachTick {
            when (step) {
                "a" -> if (isWorkingOn(helper, a, wheel) && wheel.getSpeed() != 0f) {
                    val spot = TestKit.workerOf(helper, a)?.spot
                    helper.assertTrue(spot != null && spot.z < wheel.blockPos.z, "worker A works from $spot, not north of the wheel")
                    helper.assertTrue(wheel.getSpeed() == 8f, "worker A turns the wheel at ${wheel.getSpeed()} RPM")
                    // The owner's sneak-click clears A's binding, and the release ends its claim.
                    TestKit.clickEntity(scene.owner, a, sneak = true)
                    TestKit.release(helper, a, wheel.blockPos)
                    TestKit.moveTo(helper, b, BlockPos(7, 2, 8))
                    TestKit.bind(helper, scene.owner, b, wheelRel)
                    step = "b"
                }

                "b" -> if (isWorkingOn(helper, b, wheel) && wheel.getSpeed() != 0f) {
                    val spot = TestKit.workerOf(helper, b)?.spot
                    helper.assertTrue(spot != null && spot.z > wheel.blockPos.z, "worker B works from $spot, not south of the wheel")
                    helper.assertTrue(wheel.getSpeed() == 8f, "the wheel turns at ${wheel.getSpeed()} RPM after the takeover")
                    helper.succeed()
                }
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdWhl08StopWithinTwoTicks(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val entity = squirtle(helper, scene)
        var releasedAt = -1L
        helper.onEachTick {
            if (releasedAt < 0) {
                if (isWorkingOn(helper, entity, wheel) && wheel.getSpeed() == 8f) {
                    TestKit.release(helper, entity, wheel.blockPos)
                    releasedAt = helper.tick
                }
            } else if (wheel.getSpeed() == 0f) {
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - releasedAt < 2, "the wheel turns at ${wheel.getSpeed()} RPM 2 ticks after the release")
            }
        }
    }

    /** The goggle view is a demo; this checks that what the client computes it from reaches the client, and never a save. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdWhl09ClientSeesCapacity(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val entity = squirtle(helper, scene)
        helper.succeedWhen {
            helper.assertTrue(isWorkingOn(helper, entity, wheel), "the Squirtle does not work the wheel")
            val registries = helper.level.registryAccess()
            val packet = wheel.getUpdateTag(registries)
            helper.assertTrue(packet.getBoolean(DriveNbt.DRIVEN), "the client packet does not say the wheel is driven: $packet")
            val direction = packet.getInt(DriveNbt.DIRECTION)
            helper.assertTrue(direction == 1, "the client packet's direction is $direction")
            helper.assertTrue(packet.getInt("FlowScore") == 1, "the client packet's flow score is ${packet.getInt("FlowScore")}")
            val client = DriveBehaviour(wheel).apply { read(packet, registries, true) }
            helper.assertTrue(client.driven && client.direction == 1, "a client reads ${client.driven} and ${client.direction}")
            helper.assertFalse(wheel.saveWithoutMetadata(registries).contains(DriveNbt.DRIVEN), "the saved wheel holds the driven flag")
        }
    }

    /** Worker A binds through one ring block; worker B, through another ring block of the same wheel, is refused. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdClm04LargeWheelIsOneTarget(helper: GameTestHelper) {
        val scene = scene(helper, BlockPos(7, 1, 10))
        val centerRel = BlockPos(7, 3, 5)
        val wheel = CreateKit.largeWheel(helper, centerRel)
        val a = TestKit.tether(helper, scene.pasture, scene.owner, "vaporeon level=30")
        val b = TestKit.tether(helper, scene.pasture, scene.owner, "vaporeon level=30")
        val ringKeys = CreateKit.ringOf(centerRel).map { TestKit.keyOf(helper, helper.absolutePos(it)) }
        val controller = TestKit.keyOf(helper, wheel.blockPos)
        var clicked = false
        var claimedAt = -1L
        helper.onEachTick {
            if (!clicked) {
                if (!CreateKit.isRingBuilt(helper, centerRel)) return@onEachTick
                val bound = TestKit.bind(helper, scene.owner, a, BlockPos(7, 4, 5))
                helper.assertTrue(bound?.target == wheel.blockPos, "the binding through ring block 1 names ${bound?.target}")
                TestKit.leash(scene.owner)
                TestKit.clickEntity(scene.owner, b)
                TestKit.clickBlock(helper, scene.owner, BlockPos(7, 2, 6))
                val stored = Cobblemon.storage.getPC(scene.owner)[b.pokemon.uuid]
                helper.assertTrue(stored != null && BindingStore.read(stored) == null, "worker B got a binding through ring block 2")
                val reply = Feedback.recorded[scene.owner.uuid].orEmpty().lastOrNull()?.string
                helper.assertTrue(reply?.startsWith("Already assigned to") == true, "ring block 2 answered '$reply'")
                clicked = true
                return@onEachTick
            }
            val claims = TestKit.engine(helper).claims
            helper.assertTrue(ringKeys.none { claims.holderOf(it) != null }, "a ring block holds a claim of its own")
            helper.assertTrue(TestKit.claimOf(helper, b) == null, "worker B got a claim")
            if (claimedAt < 0 && claims.holderOf(controller)?.worker == a.pokemon.uuid) claimedAt = helper.tick
            if (claimedAt >= 0 && helper.tick - claimedAt >= 100) helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_800)
    fun cdBal04WheelsNeverExceedCreate(helper: GameTestHelper) {
        val scene = scene(helper, BlockPos(7, 1, 10))
        val small = CreateKit.smallWheel(helper, BlockPos(4, 2, 5))
        val large = CreateKit.largeWheel(helper, BlockPos(10, 3, 5))
        repeat(2) { squirtle(helper, scene) }
        var bothTurningSince = -1L
        helper.onEachTick {
            helper.assertTrue(abs(small.getSpeed()) <= 8f, "the small wheel turns at ${small.getSpeed()} RPM")
            helper.assertTrue(abs(large.getSpeed()) <= 4f, "the large wheel turns at ${large.getSpeed()} RPM")
            if (bothTurningSince < 0 && small.getSpeed() != 0f && large.getSpeed() != 0f) bothTurningSince = helper.tick
            if (bothTurningSince >= 0 && helper.tick - bothTurningSince >= 1_200) helper.succeed()
        }
    }
}
