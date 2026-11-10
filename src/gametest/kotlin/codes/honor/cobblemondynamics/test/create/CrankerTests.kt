package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.create.DriveBehaviour
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The Cranker on real Create hand cranks, each with no network behind it. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CrankerTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val crankRel = BlockPos(7, 2, 5)

    /** A Work Mode pasture at [rel], south of the cranks, with its owner beside it. */
    private fun scene(helper: GameTestHelper, rel: BlockPos = BlockPos(7, 1, 9)): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, rel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    private fun machop(helper: GameTestHelper, scene: Scene): PokemonEntity =
        TestKit.tether(helper, scene.pasture, scene.owner, "machop level=10")

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, crank: HandCrankBlockEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, crank.blockPos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /** CD-CRK-02 and CD-BAL-04 in one: exactly 32 RPM on every tick never drops below Create's speed and never passes it. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdCrk02Bal04CrankHolds32RpmFor600Ticks(helper: GameTestHelper) {
        val scene = scene(helper)
        val crank = CreateKit.handCrank(helper, crankRel, Direction.UP)
        val entity = machop(helper, scene)
        var turningSince = -1L
        helper.onEachTick {
            if (turningSince < 0) {
                if (!isWorkingOn(helper, entity, crank) || crank.getSpeed() != 32f) return@onEachTick
                // CD-ASG-10 in game: the Cranker works from a spot within 2 blocks of the crank's center.
                val spot = TestKit.workerOf(helper, entity)?.spot
                val distance = spot?.let { Vec3.atCenterOf(it).distanceTo(Vec3.atCenterOf(crank.blockPos)) }
                helper.assertTrue(distance != null && distance <= 2.0, "the Cranker works from $spot, $distance blocks from the crank")
                turningSince = helper.tick
            }
            helper.assertTrue(crank.getSpeed() == 32f, "the crank turns at ${crank.getSpeed()} RPM ${helper.tick - turningSince} ticks in")
            if (helper.tick - turningSince >= 600) helper.succeed()
        }
    }

    /**
     * A crank that faces up and one that faces north: Create gives a turn of the second one the opposite sign, and the Cranker
     * still starts both at +32 RPM (CD-CRK-05), with 256 SU each (CD-CRK-03).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdCrk03Crk05NewCranksTurnAtCreateValues(helper: GameTestHelper) {
        val scene = scene(helper, BlockPos(7, 1, 10))
        val up = CreateKit.handCrank(helper, BlockPos(4, 2, 5), Direction.UP)
        val north = CreateKit.handCrank(helper, BlockPos(10, 2, 5), Direction.NORTH)
        val workers = List(2) { machop(helper, scene) }
        helper.succeedWhen {
            for ((crank, name) in listOf(up to "upward", north to "northward")) {
                helper.assertTrue(workers.any { isWorkingOn(helper, it, crank) }, "no Machop works the $name crank")
                helper.assertTrue(crank.getSpeed() == 32f, "the $name crank turns at ${crank.getSpeed()} RPM")
                helper.assertTrue(CreateKit.capacity(crank) == 256f, "the $name crank adds ${CreateKit.capacity(crank)} SU")
                val network = CreateKit.networkCapacity(crank)
                helper.assertTrue(network == 256f, "the network of the $name crank has $network SU")
            }
        }
    }

    /** Create turns a crank on any click whose item is not its wrench, so the leash reaches the crank only through its mixin. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdCrk06BinderFlips(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.ITEM_USE_OVERRIDES), "the mixin that lets the leash reach a crank did not apply")
        val scene = scene(helper)
        val crank = CreateKit.handCrank(helper, crankRel, Direction.UP)
        val entity = machop(helper, scene)
        val binding = TestKit.bind(helper, scene.owner, entity, crankRel)
        val reply = Feedback.recorded[scene.owner.uuid].orEmpty().lastOrNull()?.string
        helper.assertTrue(binding?.target == crank.blockPos, "no binding to the crank: $binding, the leash answered '$reply'")
        var clickedAt = -1L
        helper.onEachTick {
            if (clickedAt < 0) {
                if (isWorkingOn(helper, entity, crank) && crank.getSpeed() == 32f) {
                    val result = TestKit.clickBlock(helper, scene.owner, crankRel, sneak = true)
                    helper.assertTrue(result.consumesAction(), "the binder's sneak-click returned $result")
                    clickedAt = helper.tick
                }
            } else if (helper.tick - clickedAt >= 5) {
                helper.assertTrue(crank.getSpeed() == -32f, "the crank turns at ${crank.getSpeed()} RPM 5 ticks after the binder's flip")
                helper.assertTrue(DriveBehaviour.of(crank)?.direction == -1, "the crank's stored direction did not flip")
                helper.succeed()
            }
        }
    }
}
