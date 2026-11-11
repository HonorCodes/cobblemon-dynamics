package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Magnet with real item entities, their pickup delay and a real entity query. Each test binds a Klink to a chest with the Work
 * Leash, which is the bind of CD-MAG-01.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class MagnetTests {
    private val chestRel = BlockPos(11, 2, 8)

    private class Scene(val owner: ServerPlayer, val worker: PokemonEntity)

    /** A chest at [chestRel], a Work Mode pasture at (11, 1, 11) with one Pokémon of [spec], and its owner beside the pasture. */
    private fun scene(helper: GameTestHelper, spec: String): Scene {
        helper.setBlock(chestRel, Blocks.CHEST)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, TestKit.tether(helper, pasture, owner, spec))
    }

    /** An item entity of [stack] at the bottom center of [rel], with no motion and no pickup delay. */
    private fun drop(helper: GameTestHelper, rel: BlockPos, stack: ItemStack): ItemEntity {
        val at = helper.absoluteVec(rel.bottomCenter)
        val item = ItemEntity(helper.level, at.x, at.y, at.z, stack, 0.0, 0.0, 0.0)
        item.setNoPickUpDelay()
        helper.level.addFreshEntity(item)
        return item
    }

    /**
     * Items at horizontal offsets 8 and 9 from the pasture block: the Lv100 Klink takes the first and leaves the second (CD-MAG-01,
     * CD-MAG-04).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200)
    fun cdMag04OnlyItemsInsideTheWorkArea(helper: GameTestHelper) {
        val scene = scene(helper, "klink level=100")
        helper.assertTrue(TestKit.bind(helper, scene.owner, scene.worker, chestRel) != null, "CD-MAG-01: the bind to a chest was refused")
        val inside = drop(helper, BlockPos(19, 2, 11), ItemStack(Items.DIRT))
        val outside = drop(helper, BlockPos(20, 2, 11), ItemStack(Items.DIRT))
        helper.runAfterDelay(120) {
            helper.assertTrue(!inside.isAlive, "CD-MAG-04: the item at offset 8 is still on the ground")
            helper.assertTrue(outside.isAlive && outside.item.count == 1, "CD-MAG-04: the item at offset 9 was taken")
            val dirt = TestKit.countIn(helper, chestRel, Items.DIRT)
            helper.assertTrue(dirt == 1, "the chest holds $dirt dirt")
            helper.succeed()
        }
    }

    /**
     * A drop with a 40-tick pickup delay stays on the ground while the delay lasts, and the Magnet takes it within 40 ticks after
     * (CD-MAG-05).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300)
    fun cdMag05PickupDelayIsRespected(helper: GameTestHelper) {
        val scene = scene(helper, "klink level=100")
        TestKit.bind(helper, scene.owner, scene.worker, chestRel)
        helper.startSequence()
            .thenWaitUntil {
                val status = TestKit.workerOf(helper, scene.worker)?.status
                helper.assertTrue(status == WorkerStatus.STANDBY, "the Klink is $status, not in Standby yet")
            }
            .thenExecute {
                val item = drop(helper, BlockPos(13, 2, 11), ItemStack(Items.DIRT)).apply { setPickUpDelay(40) }
                val droppedAt = helper.level.gameTime
                helper.onEachTick {
                    val age = helper.level.gameTime - droppedAt
                    if (age < 40) helper.assertTrue(item.isAlive, "CD-MAG-05: the Magnet took the item $age ticks after the drop")
                    if (age == 80L) {
                        helper.assertFalse(item.isAlive, "CD-MAG-05: the item is still on the ground 40 ticks after its delay ended")
                        helper.succeed()
                    }
                }
            }
    }

    /**
     * A chest with room for 10 cobblestone, 64 cobblestone on the ground and a Lv100 Klink: the chest gains 10, and 54 stay on the ground
     * (CD-MAG-06).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200)
    fun cdMag06PartialInsertLeavesTheRest(helper: GameTestHelper) {
        val scene = scene(helper, "klink level=100")
        val chest = helper.getBlockEntity<ChestBlockEntity>(chestRel)
        for (slot in 0 until 26) chest.setItem(slot, ItemStack(Items.DIRT, 64))
        chest.setItem(26, ItemStack(Items.COBBLESTONE, 54))
        TestKit.bind(helper, scene.owner, scene.worker, chestRel)
        val stack = drop(helper, BlockPos(13, 2, 11), ItemStack(Items.COBBLESTONE, 64))
        helper.runAfterDelay(100) {
            val stored = TestKit.countIn(helper, chestRel, Items.COBBLESTONE)
            helper.assertTrue(stored == 64, "CD-MAG-06: the chest gained ${stored - 54} cobblestone, not 10")
            helper.assertTrue(stack.isAlive && stack.item.count == 54, "CD-MAG-06: the ground stack holds ${stack.item.count}")
            helper.succeed()
        }
    }

    /**
     * A Lv10 Klink collects a stack of 64 dirt, 7 items a batch. When the test takes the stack off the ground, the Klink is in Standby
     * within 20 ticks. With a new stack on the ground it Works, and when the test fills the chest with stone, it is in Standby within
     * 20 ticks while the stack still lies on the ground (CD-MAG-07).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600)
    fun cdMag07NoItemsOrAFullChestMeansStandby(helper: GameTestHelper) {
        val scene = scene(helper, "klink level=10")
        val chest = helper.getBlockEntity<ChestBlockEntity>(chestRel)
        helper.assertTrue(TestKit.bind(helper, scene.owner, scene.worker, chestRel) != null, "the bind to the chest was refused")
        fun status() = TestKit.workerOf(helper, scene.worker)?.status
        var stack = drop(helper, BlockPos(13, 2, 11), ItemStack(Items.DIRT, 64))
        var since = 0L
        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Klink is ${status()}") }
            .thenExecute {
                stack.discard()
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Klink is ${status()} with no item on the ground")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-MAG-07: no Standby within 20 ticks of the last item leaving")
            }
            .thenExecute { stack = drop(helper, BlockPos(13, 2, 11), ItemStack(Items.DIRT, 64)) }
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Klink is ${status()} with dirt on the ground again") }
            .thenExecute {
                for (slot in 0 until chest.containerSize) chest.setItem(slot, ItemStack(Items.STONE, 64))
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Klink is ${status()} with a full chest")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-MAG-07: no Standby within 20 ticks of the chest filling")
                helper.assertTrue(stack.isAlive, "the dirt left the ground, so the Standby proves nothing about the full chest")
            }
            .thenSucceed()
    }
}
