package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.create.DriveBehaviour
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** Who may flip a worked wheel or crank with the Work Leash, and a flip that a restart must keep. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CreateFlipTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val wheelRel = BlockPos(4, 2, 5)
    private val crankRel = BlockPos(10, 2, 5)

    /** A Work Mode pasture at (7,1,9), south of the targets, with its owner beside it. */
    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private fun direction(be: BlockEntity): Int? = DriveBehaviour.of(be)?.direction

    /** Binds [spec] to the target at [rel] with the owner's Work Leash. */
    private fun bound(helper: GameTestHelper, scene: Scene, spec: String, rel: BlockPos): PokemonEntity {
        val entity = TestKit.tether(helper, scene.pasture, scene.owner, spec)
        val binding = TestKit.bind(helper, scene.owner, entity, rel)
        helper.assertTrue(binding?.target == helper.absolutePos(rel), "the $spec did not bind to $rel: $binding")
        return entity
    }

    /**
     * A Squirtle and a Machop that the owner bound work a wheel at +8 RPM and a crank at +32 RPM. A second player sneak-clicks both
     * with a Work Leash, and the binder sneak-clicks a third wheel that no worker drives. For 20 ticks no direction changes and the
     * worked targets keep their speeds (CD-SEC-10, the wheel and crank parts).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdSec10StrangersAndIdleTargetsNeverFlip(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.ITEM_USE_OVERRIDES), "the mixin that lets the leash reach a crank did not apply")
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val crank = CreateKit.handCrank(helper, crankRel, Direction.UP)
        val idleRel = BlockPos(7, 2, 3)
        val idle = CreateKit.smallWheel(helper, idleRel)
        val squirtle = bound(helper, scene, "squirtle level=10", wheelRel)
        val machop = bound(helper, scene, "machop level=10", crankRel)
        val stranger = TestKit.owner(helper, "stranger")
        TestKit.standBeside(stranger, scene.pasture)
        var clickedAt = -1L
        helper.onEachTick {
            if (clickedAt < 0) {
                val worked = isWorkingOn(helper, squirtle, wheel.blockPos) && isWorkingOn(helper, machop, crank.blockPos)
                if (!worked || wheel.getSpeed() != 8f || crank.getSpeed() != 32f || DriveBehaviour.of(idle) == null) return@onEachTick
                TestKit.leash(stranger)
                TestKit.clickBlock(helper, stranger, wheelRel, sneak = true)
                TestKit.clickBlock(helper, stranger, crankRel, sneak = true)
                TestKit.leash(scene.owner)
                TestKit.clickBlock(helper, scene.owner, idleRel, sneak = true)
                clickedAt = helper.tick
                return@onEachTick
            }
            val directions = listOf(direction(wheel), direction(crank), direction(idle))
            helper.assertTrue(directions == listOf(1, 1, 1), "CD-SEC-10: after the clicks the wheel, crank and idle wheel read $directions")
            helper.assertTrue(wheel.getSpeed() == 8f, "CD-SEC-10: the stranger's click left the wheel at ${wheel.getSpeed()} RPM")
            helper.assertTrue(crank.getSpeed() == 32f, "CD-SEC-10: the stranger's click left the crank at ${crank.getSpeed()} RPM")
            helper.assertTrue(
                DriveBehaviour.of(idle)?.driven == false && idle.getSpeed() == 0f,
                "the idle wheel turns at ${idle.getSpeed()} RPM",
            )
            helper.assertTrue(BindingStore.read(squirtle.pokemon)?.target == wheel.blockPos, "the clicks changed the Squirtle's binding")
            helper.assertTrue(BindingStore.read(machop.pokemon)?.target == crank.blockPos, "the clicks changed the Machop's binding")
            if (helper.tick - clickedAt >= 20) helper.succeed()
        }
    }

    /**
     * The binder flips a wheel and a crank that their bound workers drive, to -8 and -32 RPM. Then the server restarts: every claim
     * goes, and each block entity is replaced by one loaded from its save, as a chunk load does. When the workers work again, both
     * targets read direction -1 and turn at -8 and -32 RPM (CD-WHL-07, CD-CRK-08). The restart drops the whole engine, so the test
     * runs alone in its own batch.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200, batch = "restart_whl07")
    fun cdWhl07Crk08FlipsByBoundWorkersSurviveARestart(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.ITEM_USE_OVERRIDES), "the mixin that lets the leash reach a crank did not apply")
        val scene = scene(helper)
        CreateKit.smallWheel(helper, wheelRel)
        CreateKit.handCrank(helper, crankRel, Direction.UP)
        val squirtle = bound(helper, scene, "squirtle level=10", wheelRel)
        val machop = bound(helper, scene, "machop level=10", crankRel)
        val wheelPos = helper.absolutePos(wheelRel)
        val crankPos = helper.absolutePos(crankRel)
        fun wheel(): KineticBlockEntity = helper.getBlockEntity(wheelRel)
        fun crank(): KineticBlockEntity = helper.getBlockEntity(crankRel)
        var step = "work"
        var since = -1L
        helper.onEachTick {
            val working = isWorkingOn(helper, squirtle, wheelPos) && isWorkingOn(helper, machop, crankPos)
            when (step) {
                "work" -> if (working && wheel().getSpeed() == 8f && crank().getSpeed() == 32f) {
                    TestKit.clickBlock(helper, scene.owner, wheelRel, sneak = true)
                    TestKit.clickBlock(helper, scene.owner, crankRel, sneak = true)
                    since = helper.tick
                    step = "flipped"
                }

                "flipped" -> if (wheel().getSpeed() == -8f && crank().getSpeed() == -32f) {
                    helper.assertTrue(direction(wheel()) == -1 && direction(crank()) == -1, "the binder's flips stored no -1")
                    TestKit.simulateRestart(helper)
                    val registries = helper.level.registryAccess()
                    for (be in listOf(wheel(), crank())) {
                        val loaded = BlockEntity.loadStatic(be.blockPos, be.blockState, be.saveWithFullMetadata(registries), registries)
                            ?: throw GameTestAssertException("the ${be.blockState.block.name.string} did not load from its save")
                        helper.level.setBlockEntity(loaded)
                    }
                    helper.assertTrue(wheel() !== crank(), "the reload left no block entities")
                    since = helper.tick
                    step = "restarted"
                } else {
                    helper.assertTrue(
                        helper.tick - since < 10,
                        "the binder's flips left ${wheel().getSpeed()} and ${crank().getSpeed()} RPM",
                    )
                }

                "restarted" -> {
                    helper.assertTrue(direction(wheel()) != 1 && direction(crank()) != 1, "a direction came back as +1 after the restart")
                    if (!working) return@onEachTick
                    if (wheel().getSpeed() == -8f && crank().getSpeed() == -32f && direction(wheel()) == -1 && direction(crank()) == -1) {
                        helper.succeed()
                        return@onEachTick
                    }
                    if (since >= 0 && helper.tick - since > 600) {
                        helper.fail("after the restart: wheel ${wheel().getSpeed()} RPM, crank ${crank().getSpeed()} RPM")
                    }
                }
            }
        }
    }
}
