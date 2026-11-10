package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.handler.create.DriveBehaviour
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The drive direction on real Create block entities. Create attaches the behaviour on a block entity's first tick or first load, so
 * each test waits for it.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class DriveBehaviourTests {
    private val wheel: BlockState = AllBlocks.WATER_WHEEL.defaultState.setValue(DirectionalKineticBlock.FACING, Direction.EAST)
    private val crank: BlockState = AllBlocks.HAND_CRANK.defaultState.setValue(DirectionalKineticBlock.FACING, Direction.UP)

    private fun place(helper: GameTestHelper, rel: BlockPos, state: BlockState): SmartBlockEntity {
        helper.setBlock(rel, state)
        return helper.getBlockEntity(rel)
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWhl04NewWheelsStartPositive(helper: GameTestHelper) {
        val a = place(helper, BlockPos(2, 2, 2), wheel)
        val b = place(helper, BlockPos(6, 2, 6), wheel)
        helper.onEachTick {
            val driveA = DriveBehaviour.of(a) ?: return@onEachTick
            val driveB = DriveBehaviour.of(b) ?: return@onEachTick
            helper.assertTrue(driveA.direction == 1 && driveB.direction == 1, "new wheels read ${driveA.direction} and ${driveB.direction}")
            driveA.flip()
            helper.assertTrue(driveA.direction == -1, "wheel A reads ${driveA.direction} after its flip")
            helper.assertTrue(driveB.direction == 1, "wheel B reads ${driveB.direction} after wheel A's flip")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_9x5x9")
    fun cdCrk05NewCrankStartsPositive(helper: GameTestHelper) {
        val be = place(helper, BlockPos(4, 2, 4), crank)
        helper.onEachTick {
            val drive = DriveBehaviour.of(be) ?: return@onEachTick
            helper.assertTrue(drive.direction == 1, "a new hand crank reads ${drive.direction}")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWhl07DirectionSurvivesReload(helper: GameTestHelper) = survivesReload(helper, wheel)

    @GameTest(template = "floor_9x5x9")
    fun cdCrk08DirectionSurvivesReload(helper: GameTestHelper) = survivesReload(helper, crank)

    /**
     * The GameTest form of a restart: save the block entity and load it into a new one. The flip must come back; the driven flag
     * must not, because no claim survives a restart (CD-CLM-07).
     */
    private fun survivesReload(helper: GameTestHelper, state: BlockState) {
        val be = place(helper, BlockPos(4, 2, 4), state)
        helper.onEachTick {
            val drive = DriveBehaviour.of(be) ?: return@onEachTick
            drive.flip()
            drive.driven = true
            val registries = helper.level.registryAccess()
            val saved = be.saveWithFullMetadata(registries)
            drive.driven = false
            val loaded = BlockEntity.loadStatic(be.blockPos, be.blockState, saved, registries)
            val copy = DriveBehaviour.of(loaded)
            helper.assertTrue(copy != null, "the loaded ${state.block.name.string} has no drive behaviour")
            helper.assertTrue(copy?.direction == -1, "the flipped ${state.block.name.string} reads ${copy?.direction} after a reload")
            helper.assertTrue(copy?.driven == false, "the ${state.block.name.string} came back driven after a reload")
            helper.succeed()
        }
    }
}
