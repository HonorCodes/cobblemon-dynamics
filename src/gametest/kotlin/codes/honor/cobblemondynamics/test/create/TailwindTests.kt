package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.create.TailwindBonus
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * Pastured Tailwind workers that the owner binds with the Work Leash to a Create windmill: the assembly rule, the bonus and its end,
 * and Create's speed cap. The bearing sits at [bearingRel] and faces up, so a Pidgey stands beside it, under the sails.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class TailwindTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val bearingRel = BlockPos(7, 2, 9)

    /** A Work Mode pasture at (7,1,2) with its owner beside it, south of the sails. */
    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 2), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** The speed of the shaft below the bearing, which the windmill drives. */
    private fun rpm(helper: GameTestHelper): Float = abs(helper.getBlockEntity<KineticBlockEntity>(bearingRel.below()).speed)

    /**
     * A bearing with 7 real sails and a bound Lv100 Pidgey: the owner's empty-hand click does not assemble it (CD-TLW-05). With an
     * 8th real sail, the same click does, which shows that the click reaches the bearing.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 300)
    fun cdTlw05SevenSailsNeverAssemble(helper: GameTestHelper) {
        val bearing = CreateKit.windmill(helper, bearingRel, sails = 7)
        val scene = scene(helper)
        val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=100")
        fun click() {
            scene.owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            TestKit.clickBlock(helper, scene.owner, bearingRel)
        }
        helper.startSequence()
            .thenIdle(1)
            .thenExecute {
                helper.assertTrue(TestKit.bind(helper, scene.owner, pidgey, bearingRel) != null, "the bind to the bearing was refused")
                click()
            }
            .thenIdle(20)
            .thenExecute {
                helper.assertFalse(bearing.isRunning, "CD-TLW-05: the windmill assembled with 7 real sails")
                helper.setBlock(bearingRel.offset(0, 1, 1), CreateKit.sail())
                click()
            }
            .thenIdle(20)
            .thenExecute { helper.assertTrue(bearing.isRunning, "the click did not assemble the windmill with 8 real sails") }
            .thenSucceed()
    }

    /**
     * A 64-sail windmill turns its shaft at 8 RPM. A Lv50 Pidgey adds 32 virtual sails, so 2 ticks after it starts to Work the
     * shaft turns at 12 RPM (CD-TLW-03, CD-TLW-06). Releasing the claim takes the sails away: 8 RPM 2 ticks later (CD-TLW-09).
     * Working again after the release's 200-tick skip, the Pidgey waits in Standby within 20 ticks of the disassembly (CD-TLW-08).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1000)
    fun cdTlw06Tlw09Tlw08TheBonusFollowsTheWork(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.WINDMILL_BEARING), "the windmill mixin did not apply")
        val bearing = CreateKit.windmill(helper, bearingRel, sails = 64)
        val scene = scene(helper)
        val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=50")
        fun status() = TestKit.workerOf(helper, pidgey)?.status
        var since = 0L
        helper.startSequence()
            .thenIdle(1)
            .thenExecute { bearing.assemble() }
            .thenWaitUntil { helper.assertTrue(rpm(helper) == 8f, "the 64-sail windmill turns at ${rpm(helper)} RPM") }
            .thenExecute { TestKit.bind(helper, scene.owner, pidgey, bearingRel) ?: throw GameTestAssertException("no binding") }
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Pidgey is ${status()}") }
            .thenIdle(2)
            .thenExecute {
                helper.assertTrue(rpm(helper) == 12f, "CD-TLW-06: ${rpm(helper)} RPM 2 ticks after the Pidgey started to Work")
                TestKit.release(helper, pidgey, helper.absolutePos(bearingRel))
            }
            .thenIdle(2)
            .thenExecute { helper.assertTrue(rpm(helper) == 8f, "CD-TLW-09: ${rpm(helper)} RPM 2 ticks after the release") }
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Pidgey is ${status()} after the skip") }
            .thenExecute {
                bearing.disassemble()
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Pidgey is ${status()} at a disassembled windmill")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-TLW-08: no Standby within 20 ticks")
            }
            .thenSucceed()
    }

    /**
     * A Pidgey that stands on the turning sails over a work spot when its claim starts has arrived: the engine puts it on the spot, and
     * it Works (CD-TLW-11). The pathfinder does not see the sails, so the Pidgey would never reach the spot below them. It stands over
     * the cell east of the bearing, which is the spot nearest to it, and its wander pauses until the claim, as a held worker's does.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdTlw11AWorkerOnItsSailsOverItsSpotArrives(helper: GameTestHelper) {
        val bearing = CreateKit.windmill(helper, bearingRel, sails = 64)
        val scene = scene(helper)
        val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=50")
        val east = bearingRel.east()
        fun worker() = TestKit.workerOf(helper, pidgey)
        fun onTheSails() = bearing.movedContraption?.collidingEntities?.containsKey(pidgey) == true
        var bound = 0L
        helper.startSequence()
            .thenIdle(1)
            .thenExecute { bearing.assemble() }
            .thenIdle(1)
            .thenExecute {
                val top = helper.absoluteVec(Vec3(east.x + 0.5, east.y + 1.7, east.z + 0.5))
                pidgey.teleportTo(top.x, top.y, top.z)
                BrainBridge.pauseWander(pidgey, 40)
            }
            .thenWaitUntil { helper.assertTrue(onTheSails(), "the Pidgey has not landed on the sails") }
            .thenExecute {
                TestKit.bind(helper, scene.owner, pidgey, bearingRel) ?: throw GameTestAssertException("no binding")
                bound = helper.level.gameTime
            }
            .thenWaitUntil { helper.assertTrue(worker()?.status == WorkerStatus.WORKING, "CD-TLW-11: the Pidgey is ${worker()?.status}") }
            .thenExecute {
                val spot = worker()?.spot
                val ticks = helper.level.gameTime - bound
                helper.assertTrue(spot == helper.absolutePos(east), "the Pidgey works from $spot, not from the cell east of the bearing")
                helper.assertTrue(spot != null && WorkEngine.isArrived(pidgey, spot), "the Pidgey is not on its spot")
                helper.assertTrue(ticks <= 20, "CD-TLW-11: Working $ticks ticks after the bind")
            }
            .thenSucceed()
    }

    /** 100 real sails and a Lv100 Pidgey: a bonus of 28, 128 sails and 16 RPM, never more over 200 ticks (CD-TLW-04, CD-BAL-04). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBal04Tlw04TheWindmillNeverPassesSixteenRpm(helper: GameTestHelper) {
        val bearing = CreateKit.windmill(helper, bearingRel, sails = 100)
        val scene = scene(helper)
        val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=100")
        helper.startSequence()
            .thenIdle(1)
            .thenExecute { bearing.assemble() }
            .thenExecute { TestKit.bind(helper, scene.owner, pidgey, bearingRel) ?: throw GameTestAssertException("no binding") }
            .thenWaitUntil { helper.assertTrue(TestKit.workerOf(helper, pidgey)?.status == WorkerStatus.WORKING, "not Working") }
            .thenExecuteFor(200) { helper.assertTrue(rpm(helper) <= 16f, "CD-BAL-04: the windmill turns at ${rpm(helper)} RPM") }
            .thenExecute {
                helper.assertTrue(
                    TailwindBonus.of(bearing)?.sails == 28,
                    "CD-TLW-04: the bonus is ${TailwindBonus.of(bearing)?.sails}, not 28",
                )
                helper.assertTrue(rpm(helper) == 16f, "the worked windmill turns at ${rpm(helper)} RPM, not 16")
            }
            .thenSucceed()
    }
}
