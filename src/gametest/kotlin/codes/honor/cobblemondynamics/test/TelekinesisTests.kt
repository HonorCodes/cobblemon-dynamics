package codes.honor.cobblemondynamics.test

import appeng.api.parts.PartHelper
import appeng.api.stacks.AEItemKey
import appeng.api.util.AEColor
import appeng.blockentity.storage.MEChestBlockEntity
import appeng.core.definitions.AEBlocks
import appeng.core.definitions.AEItems
import appeng.core.definitions.AEParts
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.item.TransferParams
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.neoforged.neoforge.items.ItemHandlerHelper

/**
 * Telekinesis through real item handlers: a vanilla chest and furnace, Create's item vault, depot and chute, and AE2's ME Interface
 * block and cable part. The tests place Create's blocks by id, so they name no Create class.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class TelekinesisTests {
    private class Scene(val owner: ServerPlayer, val worker: PokemonEntity)

    /** A Work Mode pasture at (7, 1, 9) with one Pokémon of [spec], and its owner beside the pasture. */
    private fun scene(helper: GameTestHelper, spec: String): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, TestKit.tether(helper, pasture, owner, spec))
    }

    /**
     * A chest of coal is the source, and a furnace clicked on its east side is the destination: all coal lands in the fuel slot
     * (CD-TLK-04).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdTlk04CoalGoesIntoTheFuelSlot(helper: GameTestHelper) {
        val chestRel = BlockPos(5, 2, 4)
        val furnaceRel = BlockPos(9, 2, 4)
        helper.setBlock(chestRel, Blocks.CHEST)
        helper.getBlockEntity<ChestBlockEntity>(chestRel).setItem(0, ItemStack(Items.COAL, 16))
        val furnace = TestKit.furnace(helper, furnaceRel, input = null)
        val scene = scene(helper, "alakazam level=100")
        val binding = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, furnaceRel, destinationFace = Direction.EAST)
        helper.assertTrue(binding?.destinationFace == Direction.EAST, "the clicks gave $binding")
        helper.succeedWhen {
            val elsewhere = !furnace.getItem(0).isEmpty || !furnace.getItem(2).isEmpty
            helper.assertFalse(elsewhere, "CD-TLK-04: coal reached the input or the output slot")
            val fuel = furnace.getItem(1)
            helper.assertTrue(fuel.`is`(Items.COAL) && fuel.count == 16, "CD-TLK-04: the fuel slot holds $fuel")
        }
    }

    /**
     * 2,000 cobblestone in a double chest. A Lv100 Alakazam moves them into a Create item vault, then a depot, then a chute, one
     * destination at a time, and each destination breaks three batches after the worker starts on it. After each break, the
     * cobblestone in the chest, in the destination and on the ground still adds up to 2,000 (CD-TLK-07). The depot and the chute keep
     * the stack object that they get, so a missed copy shows up here. Each destination has its own place, because a depot or a chute
     * takes the items that land on it, so one in the drops of the last destination would start full.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1600)
    fun cdTlk07NoItemIsLostOrMade(helper: GameTestHelper) {
        val chestRel = BlockPos(3, 2, 3)
        val chest = TestKit.doubleChest(helper, chestRel, Direction.NORTH)
        for (n in List(31) { 64 } + 16) {
            val rest = ItemHandlerHelper.insertItemStacked(chest, ItemStack(Items.COBBLESTONE, n), false)
            if (!rest.isEmpty) throw GameTestAssertException("the double chest took only ${n - rest.count} of $n cobblestone")
        }
        val destinations = listOf(
            "create:item_vault" to BlockPos(11, 2, 3),
            "create:depot" to BlockPos(11, 2, 7),
            "create:chute" to BlockPos(11, 2, 11),
        )
        val scene = scene(helper, "alakazam level=100")
        fun total(): Int = TestKit.countIn(helper, chestRel, Items.COBBLESTONE) +
            destinations.sumOf { (_, rel) -> TestKit.countIn(helper, rel, Items.COBBLESTONE) } +
            TestKit.countOnTheGround(helper, Items.COBBLESTONE)
        fun isWorking(): Boolean = TestKit.workerOf(helper, scene.worker)?.status == WorkerStatus.WORKING
        helper.assertTrue(total() == 2_000, "the scene holds ${total()} cobblestone, not 2,000")
        var sequence = helper.startSequence()
        for ((id, destinationRel) in destinations) {
            sequence = sequence
                .thenExecute { helper.setBlock(destinationRel, TestKit.ae2Block(id)) }
                .thenIdle(1)
                .thenExecute {
                    TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, destinationRel)
                    val reply = TestKit.lastMessage(scene.owner)
                    helper.assertTrue(reply?.startsWith("Bound ") == true, "the bind to $id answered '$reply'")
                }
                .thenWaitUntil {
                    val status = TestKit.workerOf(helper, scene.worker)?.status
                    val held = TestKit.countIn(helper, destinationRel, Items.COBBLESTONE)
                    helper.assertTrue(isWorking(), "the Alakazam does not Work on the $id, which holds $held cobblestone: $status")
                }
                .thenIdle(3 * TransferParams.BATCH_TICKS)
                .thenExecute {
                    val moved = TestKit.countIn(helper, destinationRel, Items.COBBLESTONE)
                    helper.assertTrue(moved > 0, "the $id holds no cobblestone before it breaks, so the scene proves nothing")
                    helper.destroyBlock(destinationRel)
                }
                .thenIdle(2)
                .thenExecute { helper.assertTrue(total() == 2_000, "CD-TLK-07: after the $id broke, ${total()} cobblestone are left") }
        }
        sequence.thenSucceed()
    }

    /**
     * A creative energy cell, an ME chest with a 1k item cell and an ME Interface block touch, so they form one network with no
     * controller. 64 coal from a chest reach the cell through the interface block, clicked on its top. Then 64 more reach it through
     * an interface cable part on the south side of a cable bus that touches the energy cell, clicked on the bus's south face. Each
     * time the coal reaches the network's storage within 40 ticks of the batch that takes it from the chest (CD-TLK-08).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdTlk08ItemsReachTheNetworkThroughBothInterfaces(helper: GameTestHelper) {
        val chestRel = BlockPos(3, 2, 3)
        helper.setBlock(chestRel, Blocks.CHEST)
        val chest = helper.getBlockEntity<ChestBlockEntity>(chestRel)
        chest.setItem(0, ItemStack(Items.COAL, 64))
        val interfaceRel = BlockPos(8, 2, 3)
        val cellRel = BlockPos(9, 2, 3)
        val storageRel = BlockPos(10, 2, 3)
        val busRel = BlockPos(9, 2, 4)
        helper.setBlock(interfaceRel, AEBlocks.INTERFACE.block())
        helper.setBlock(cellRel, AEBlocks.CREATIVE_ENERGY_CELL.block())
        helper.setBlock(storageRel, AEBlocks.ME_CHEST.block())
        helper.getBlockEntity<MEChestBlockEntity>(storageRel).setCell(AEItems.ITEM_CELL_1K.stack())
        val bus = helper.absolutePos(busRel)
        val cable = PartHelper.setPart(helper.level, bus, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT))
        val part = PartHelper.setPart(helper.level, bus, Direction.SOUTH, null, AEParts.INTERFACE.asItem())
        helper.assertTrue(cable != null && part != null, "AE2 placed the cable $cable and the interface part $part")
        fun stored(): Long =
            helper.getBlockEntity<MEChestBlockEntity>(storageRel).getCellInventory(0)?.availableStacks?.get(AEItemKey.of(Items.COAL)) ?: 0L
        val scene = scene(helper, "alakazam level=100")
        fun bind(rel: BlockPos, face: Direction) {
            val binding = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, rel, destinationFace = face)
            val bound = binding?.destination == helper.absolutePos(rel) && binding.destinationFace == face
            helper.assertTrue(bound, "the bind to the ${helper.getBlockState(rel).block} gave $binding")
        }
        var movedAt = -1L
        fun reached(expected: Long, through: String) {
            if (movedAt < 0) {
                helper.assertTrue(chest.isEmpty, "the Alakazam has not taken the coal for the $through yet")
                movedAt = helper.tick
            }
            val waited = helper.tick - movedAt
            helper.assertTrue(waited <= 40, "CD-TLK-08: the cell holds ${stored()} coal $waited ticks after the batch to the $through")
            helper.assertTrue(stored() == expected, "the cell holds ${stored()} coal through the $through")
        }
        helper.startSequence()
            .thenExecute { bind(interfaceRel, Direction.UP) }
            .thenWaitUntil { reached(64L, "interface block") }
            .thenExecute {
                movedAt = -1L
                chest.setItem(0, ItemStack(Items.COAL, 64))
                bind(busRel, Direction.SOUTH)
            }
            .thenWaitUntil { reached(128L, "interface part") }
            .thenSucceed()
    }

    /**
     * Ruling 5 of the Phase 2 plan: a double chest and a Create item vault of two blocks are each one container. A click on one half
     * of the chest as the source, then on its other half as the destination, is refused as the same container, and so is a click on
     * each block of the vault (CD-TLK-10).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdTlk10TheBlocksOfOneInventoryAreOneContainer(helper: GameTestHelper) {
        val left = BlockPos(3, 2, 3)
        TestKit.doubleChest(helper, left, Direction.NORTH)
        val vault = listOf(BlockPos(9, 2, 3), BlockPos(10, 2, 3))
        for (rel in vault) helper.setBlock(rel, TestKit.ae2Block("create:item_vault"))
        val scene = scene(helper, "alakazam level=40")
        helper.runAfterDelay(5) {
            val handlers = vault.map { TestKit.items(helper, it) }
            helper.assertTrue(handlers[0] != null && handlers[0] === handlers[1], "the two vault blocks did not form one vault: $handlers")
            for ((what, pair) in listOf("double chest" to (left to left.east()), "item vault" to (vault[0] to vault[1]))) {
                val binding = TestKit.bindTransfer(helper, scene.owner, scene.worker, pair.first, pair.second)
                helper.assertTrue(binding == null, "CD-TLK-10: the two blocks of one $what were bound: $binding")
                val reply = TestKit.lastMessage(scene.owner)
                helper.assertTrue(reply == "Destination must differ from the source", "CD-TLK-10: the $what answered '$reply'")
            }
            helper.succeed()
        }
    }
}
