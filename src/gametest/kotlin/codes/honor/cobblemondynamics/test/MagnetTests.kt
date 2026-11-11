package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
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

    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity, val worker: PokemonEntity)

    /** A chest at [chestRel], a Work Mode pasture at (11, 1, 11) with one Pokémon of [spec], and its owner beside the pasture. */
    private fun scene(helper: GameTestHelper, spec: String): Scene {
        helper.setBlock(chestRel, Blocks.CHEST)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture, TestKit.tether(helper, pasture, owner, spec))
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

    /**
     * The approver's flow (CD-MAG-09, CD-CLM-10, CD-LSH-28): 256 andesite lie on the ground. A Lv50 Klink binds chest A and collects the
     * andesite into it, and a Lv50 Alakazam binds chest A as its source and chest B as its destination. Both binds succeed, both
     * workers Work in the same tick under shared claims on chest A, the andesite on the ground and in both chests adds up to 256 on
     * every tick, and chest B ends with all of it.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 900)
    fun cdMag09Clm10MagnetFillsTheChestThatTelekinesisEmpties(helper: GameTestHelper) {
        val chestB = BlockPos(5, 2, 5)
        val scene = scene(helper, "klink level=50")
        helper.setBlock(chestB, Blocks.CHEST)
        val alakazam = TestKit.tether(helper, scene.pasture, scene.owner, "alakazam level=50")
        val collector = TestKit.bind(helper, scene.owner, scene.worker, chestRel)
        helper.assertTrue(collector != null, "CD-MAG-01: the Klink's bind to chest A answered '${TestKit.lastMessage(scene.owner)}'")
        val mover = TestKit.bindTransfer(helper, scene.owner, alakazam, chestRel, chestB)
        val ends = mover?.target == helper.absolutePos(chestRel) && mover.destination == helper.absolutePos(chestB)
        helper.assertTrue(ends, "CD-LSH-28: the Alakazam's bind gave $mover and answered '${TestKit.lastMessage(scene.owner)}'")
        for (rel in listOf(BlockPos(14, 2, 13), BlockPos(16, 2, 13), BlockPos(14, 2, 15), BlockPos(16, 2, 15))) {
            drop(helper, rel, ItemStack(Items.ANDESITE, 64))
        }
        val chestA = TestKit.keyOf(helper, helper.absolutePos(chestRel))
        val workers = listOf(scene.worker, alakazam)
        val expected = 4 * 64
        fun total(): Int = TestKit.countOnTheGround(helper, Items.ANDESITE) + TestKit.countIn(helper, chestRel, Items.ANDESITE) +
            TestKit.countIn(helper, chestB, Items.ANDESITE)
        var together = false
        helper.onEachTick {
            helper.assertTrue(total() == expected, "CD-MAG-09: the andesite adds up to ${total()}, not $expected")
            if (workers.any { TestKit.workerOf(helper, it)?.status != WorkerStatus.WORKING }) return@onEachTick
            for (worker in workers) {
                val claim = TestKit.claimOf(helper, worker)
                val onChestA = claim?.key == chestA && claim.shared
                helper.assertTrue(onChestA, "CD-CLM-10: the ${worker.pokemon.species.name} Works under $claim")
            }
            together = true
        }
        helper.succeedWhen {
            helper.assertTrue(together, "CD-MAG-09: the Klink and the Alakazam have not Worked in the same tick")
            val moved = TestKit.countIn(helper, chestB, Items.ANDESITE)
            helper.assertTrue(moved == expected, "CD-MAG-09: chest B holds $moved of the $expected andesite")
        }
    }
}
