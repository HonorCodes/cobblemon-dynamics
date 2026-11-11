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
import codes.honor.cobblemondynamics.leash.BindingStore
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
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
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.neoforged.neoforge.items.ItemHandlerHelper

/**
 * Telekinesis through real item handlers: a vanilla chest, barrel and furnace, Create's item vault, depot and chute, and AE2's ME
 * Interface block and cable part. The tests place Create's blocks by id, so they name no Create class.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class TelekinesisTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity, val worker: PokemonEntity)

    /** A Work Mode pasture at [pastureRel] with one Pokémon of [spec], and its owner beside the pasture. */
    private fun scene(helper: GameTestHelper, spec: String, pastureRel: BlockPos = BlockPos(7, 1, 9)): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture, TestKit.tether(helper, pasture, owner, spec))
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
     * One owner's Alakazam and Espeon bind two chests to one barrel: 64 dirt from one chest and 64 cobblestone from the other. Both
     * binds succeed, and the barrel is never reserved or claimed. Each worker Works under a claim on its own chest, and the barrel
     * gets the items of both (CD-TLK-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdTlk05TwoSourcesFeedOneBarrel(helper: GameTestHelper) {
        val barrelRel = BlockPos(7, 2, 4)
        val chests = listOf(BlockPos(3, 2, 4), BlockPos(11, 2, 4))
        val items = listOf(Items.DIRT, Items.COBBLESTONE)
        helper.setBlock(barrelRel, Blocks.BARREL)
        for ((rel, item) in chests.zip(items)) {
            helper.setBlock(rel, Blocks.CHEST)
            helper.getBlockEntity<ChestBlockEntity>(rel).setItem(0, ItemStack(item, 64))
        }
        val scene = scene(helper, "alakazam level=40")
        val workers = listOf(scene.worker, TestKit.tether(helper, scene.pasture, scene.owner, "espeon level=40"))
        val barrel = helper.absolutePos(barrelRel)
        for ((worker, chestRel) in workers.zip(chests)) {
            val binding = TestKit.bindTransfer(helper, scene.owner, worker, chestRel, barrelRel)
            val bound = binding?.target == helper.absolutePos(chestRel) && binding.destination == barrel
            val reply = TestKit.lastMessage(scene.owner)
            helper.assertTrue(bound, "CD-TLK-05: the bind of the ${worker.pokemon.species.name} gave $binding and answered '$reply'")
        }
        val reserved = TestKit.reservationAt(helper, barrel)
        helper.assertTrue(reserved == null, "CD-TLK-05: the barrel is reserved for $reserved")
        val worked = BooleanArray(workers.size)
        helper.onEachTick {
            val holder = TestKit.engine(helper).claims.holderOf(TestKit.keyOf(helper, barrel))
            helper.assertTrue(holder == null, "CD-TLK-05: the barrel holds the claim $holder")
            workers.forEachIndexed { i, worker ->
                if (TestKit.workerOf(helper, worker)?.status != WorkerStatus.WORKING) return@forEachIndexed
                val key = TestKit.claimOf(helper, worker)?.key
                val own = TestKit.keyOf(helper, helper.absolutePos(chests[i]))
                helper.assertTrue(key == own, "CD-TLK-05: the ${worker.pokemon.species.name} Works under a claim on $key")
                worked[i] = true
            }
        }
        helper.succeedWhen {
            helper.assertTrue(worked.all { it }, "the Alakazam and the Espeon have not both Worked: ${worked.toList()}")
            for (item in items) {
                val count = TestKit.countIn(helper, barrelRel, item)
                helper.assertTrue(count == 64, "the barrel holds $count of the 64 $item")
            }
        }
    }

    /**
     * A Lv10 Alakazam moves dirt from a chest to a barrel, 7 items a batch. When the test empties the chest, the Alakazam is in Standby
     * within 20 ticks. With dirt in the chest again it Works, and when the test fills the barrel with stone, it is in Standby within 20
     * ticks while the chest still holds dirt (CD-TLK-06).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdTlk06EmptySourceOrFullDestinationMeansStandby(helper: GameTestHelper) {
        val chestRel = BlockPos(5, 2, 4)
        val barrelRel = BlockPos(9, 2, 4)
        helper.setBlock(chestRel, Blocks.CHEST)
        helper.setBlock(barrelRel, Blocks.BARREL)
        val chest = helper.getBlockEntity<ChestBlockEntity>(chestRel)
        val barrel = helper.getBlockEntity<BarrelBlockEntity>(barrelRel)
        chest.setItem(0, ItemStack(Items.DIRT, 64))
        val scene = scene(helper, "alakazam level=10")
        val binding = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, barrelRel)
        helper.assertTrue(binding?.destination == helper.absolutePos(barrelRel), "the chest and the barrel gave $binding")
        fun status() = TestKit.workerOf(helper, scene.worker)?.status
        var since = 0L
        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Alakazam is ${status()}") }
            .thenExecute {
                chest.clearContent()
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Alakazam is ${status()} with an empty chest")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-TLK-06: no Standby within 20 ticks of the chest emptying")
            }
            .thenExecute { chest.setItem(0, ItemStack(Items.DIRT, 64)) }
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.WORKING, "the Alakazam is ${status()} with dirt again") }
            .thenExecute {
                for (slot in 0 until barrel.containerSize) barrel.setItem(slot, ItemStack(Items.STONE, 64))
                since = helper.level.gameTime
            }
            .thenWaitUntil {
                helper.assertTrue(status() == WorkerStatus.STANDBY, "the Alakazam is ${status()} with a full barrel")
                helper.assertTrue(helper.level.gameTime - since <= 20, "CD-TLK-06: no Standby within 20 ticks of the barrel filling")
                val dirt = TestKit.countIn(helper, chestRel, Items.DIRT)
                helper.assertTrue(dirt > 0, "the chest holds no dirt, so the Standby proves nothing about the full barrel")
            }
            .thenSucceed()
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
     * With the default config, a chest at horizontal offset 9 from the pasture block is refused as the source with its message, and the
     * click on a barrel inside the work area after it binds nothing. A chest at offset 8 binds to another barrel inside the work area
     * (CD-TLK-09).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100)
    fun cdTlk09SourceAtOffset9IsRefused(helper: GameTestHelper) {
        val beyond = BlockPos(2, 2, 11)
        val edge = BlockPos(19, 2, 11)
        val barrels = listOf(BlockPos(11, 2, 7), BlockPos(11, 2, 15))
        for (rel in listOf(beyond, edge)) helper.setBlock(rel, Blocks.CHEST)
        for (rel in barrels) helper.setBlock(rel, Blocks.BARREL)
        val scene = scene(helper, "alakazam level=40", BlockPos(11, 1, 11))
        TestKit.leash(scene.owner)
        TestKit.clickEntity(scene.owner, scene.worker)
        TestKit.clickBlock(helper, scene.owner, beyond)
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(reply == "Outside its pasture's work area", "CD-TLK-09: the chest at offset 9 answered '$reply'")
        TestKit.clickBlock(helper, scene.owner, barrels[0])
        val refused = BindingStore.read(scene.worker.pokemon)
        helper.assertTrue(refused == null, "CD-TLK-09: the chest at offset 9 was bound: $refused")
        val bound = TestKit.bindTransfer(helper, scene.owner, scene.worker, edge, barrels[1])
        val ends = bound?.target == helper.absolutePos(edge) && bound.destination == helper.absolutePos(barrels[1])
        helper.assertTrue(ends, "CD-TLK-09: the chest at offset 8 gave $bound")
        helper.succeed()
    }

    /** A click on a chest as the source, then on the same chest as the destination, binds nothing and says why (CD-TLK-10). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdTlk10SameChestTwiceIsRefused(helper: GameTestHelper) {
        val chestRel = BlockPos(5, 2, 4)
        helper.setBlock(chestRel, Blocks.CHEST)
        val scene = scene(helper, "alakazam level=40")
        val binding = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, chestRel)
        helper.assertTrue(binding == null, "CD-TLK-10: the chest was bound to itself: $binding")
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(reply == "Destination must differ from the source", "CD-TLK-10: the second click answered '$reply'")
        helper.succeed()
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

    /**
     * One owner binds an Alakazam from one chest to one barrel, and an Espeon from a second chest to a second barrel. The test breaks the
     * Alakazam's barrel and replaces the Espeon's chest with stone. 20 ticks later neither binding remains and neither chest is
     * reserved (CD-TLK-11), and the binder's chat names each cause.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdTlk11BrokenDestinationOrReplacedSourceClears(helper: GameTestHelper) {
        val chests = listOf(BlockPos(3, 2, 4), BlockPos(11, 2, 4))
        val barrels = listOf(BlockPos(3, 2, 7), BlockPos(11, 2, 7))
        for (rel in chests) helper.setBlock(rel, Blocks.CHEST)
        for (rel in barrels) helper.setBlock(rel, Blocks.BARREL)
        val scene = scene(helper, "alakazam level=40")
        val workers = listOf(scene.worker, TestKit.tether(helper, scene.pasture, scene.owner, "espeon level=40"))
        for ((i, worker) in workers.withIndex()) {
            val binding = TestKit.bindTransfer(helper, scene.owner, worker, chests[i], barrels[i])
            val bound = binding?.destination == helper.absolutePos(barrels[i])
            helper.assertTrue(bound, "the ${worker.pokemon.species.name} was bound to $binding")
        }
        helper.destroyBlock(barrels[0])
        helper.setBlock(chests[1], Blocks.STONE)
        helper.runAfterDelay(20) {
            for ((i, worker) in workers.withIndex()) {
                val name = worker.pokemon.species.name
                val binding = BindingStore.read(worker.pokemon)
                helper.assertTrue(binding == null, "CD-TLK-11: the $name keeps $binding")
                val reserved = TestKit.reservationAt(helper, helper.absolutePos(chests[i]))
                helper.assertTrue(reserved == null, "CD-TLK-11: the chest of the $name is still reserved: $reserved")
            }
            val causes = listOf(
                "Alakazam lost its work binding: its destination was broken or replaced",
                "Espeon lost its work binding: its target was broken or replaced",
            )
            val messages = TestKit.messages(scene.owner)
            helper.assertTrue(messages.containsAll(causes), "the binder got $messages, not $causes")
            helper.succeed()
        }
    }

    /**
     * With the default Telekinesis role file, a barrel 33 blocks east of the pasture block is refused as the destination with its
     * message, and a barrel 32 blocks east binds (CD-TLK-12).
     */
    @GameTest(template = "floor_41x8x41", timeoutTicks = 100)
    fun cdTlk12DestinationAt33IsTooFar(helper: GameTestHelper) {
        val pastureRel = BlockPos(4, 1, 20)
        val chestRel = BlockPos(4, 2, 16)
        val tooFar = pastureRel.offset(33, 1, 0)
        val inReach = pastureRel.offset(32, 1, 0)
        helper.setBlock(chestRel, Blocks.CHEST)
        for (rel in listOf(tooFar, inReach)) helper.setBlock(rel, Blocks.BARREL)
        val scene = scene(helper, "alakazam level=40", pastureRel)
        val refused = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, tooFar)
        helper.assertTrue(refused == null, "CD-TLK-12: the barrel at offset 33 was bound: $refused")
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(reply == "Too far from its pasture", "CD-TLK-12: the barrel at offset 33 answered '$reply'")
        val bound = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, inReach)
        helper.assertTrue(bound?.destination == helper.absolutePos(inReach), "CD-TLK-12: the barrel at offset 32 gave $bound")
        helper.succeed()
    }
}
