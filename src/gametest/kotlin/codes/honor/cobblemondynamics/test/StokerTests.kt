package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class StokerTests {
    /** A Work Mode pasture at (7,1,7) with one Charmander, and a furnace at `furnaceRel`. */
    private fun scene(
        helper: GameTestHelper,
        furnaceRel: BlockPos = BlockPos(7, 1, 3),
        input: ItemStack? = ItemStack(Items.RAW_IRON, 8),
        fuel: ItemStack? = null,
        block: Block = Blocks.FURNACE,
    ): Triple<PokemonEntity, AbstractFurnaceBlockEntity, ServerPlayer> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnace = TestKit.furnace(helper, furnaceRel, input, fuel, block)
        return Triple(TestKit.tether(helper, pasture, owner, "charmander level=10"), furnace, owner)
    }

    private fun isWorking(helper: GameTestHelper, entity: PokemonEntity) = TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING

    private fun isLit(helper: GameTestHelper, furnace: AbstractFurnaceBlockEntity) =
        helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdStk01FurnaceSmokerAndBlastFurnaceAreClaimed(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnaces = listOf(
            TestKit.furnace(helper, BlockPos(3, 1, 3), ItemStack(Items.RAW_IRON, 8)),
            TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.BEEF, 8), block = Blocks.SMOKER),
            TestKit.furnace(helper, BlockPos(11, 1, 3), ItemStack(Items.RAW_GOLD, 8), block = Blocks.BLAST_FURNACE),
        )
        repeat(3) { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        helper.succeedWhen {
            // Tests in a batch share the claim registry, and heal claims from other tests target entities.
            val claimed = TestKit.engine(helper).claims.all().mapNotNull {
                (it.key as? TargetKey.Block)?.let { k -> BlockPos.of(k.pos) }
            }.toSet()
            furnaces.forEach { helper.assertTrue(it.blockPos in claimed, "${it.blockState.block} is not claimed") }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_200)
    fun cdStk02EightIronIn1600Ticks(helper: GameTestHelper) = cooksEight(helper, Blocks.FURNACE, Items.RAW_IRON, 1_580..1_620)

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_400)
    fun cdStk02SmokerCooksEightBeefIn800Ticks(helper: GameTestHelper) = cooksEight(helper, Blocks.SMOKER, Items.BEEF, 780..820)

    private fun cooksEight(helper: GameTestHelper, block: Block, input: Item, ticks: IntRange) {
        val (_, furnace, _) = scene(helper, input = ItemStack(input, 8), block = block)
        var workingSince = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val holder = engine.claims.holderOf(TestKit.keyOf(helper, furnace.blockPos))
            if (workingSince < 0 && holder != null && engine.worker(holder.worker)?.status == WorkerStatus.WORKING) {
                workingSince = helper.tick
            }
            if (furnace.getItem(2).count == 8) {
                helper.assertTrue(workingSince >= 0, "the furnace cooked with no Stoker")
                val took = helper.tick - workingSince
                helper.assertTrue(took in ticks, "8 smelts took $took ticks")
                helper.succeed()
            }
        }
    }

    /** Vanilla burns a coal that sits beside input in an unlit furnace, so the coal goes in on the tick Working starts. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_200)
    fun cdStk03CoalIsNeverConsumed(helper: GameTestHelper) {
        val (fire, furnace, _) = scene(helper)
        var coalIn = false
        helper.onEachTick {
            if (!coalIn && isWorking(helper, fire)) {
                furnace.setItem(1, ItemStack(Items.COAL))
                coalIn = true
            }
            if (coalIn) {
                val fuel = furnace.getItem(1)
                helper.assertTrue(fuel.`is`(Items.COAL) && fuel.count == 1, "the coal count dropped: $fuel")
            }
            if (furnace.getItem(2).count == 8) {
                helper.assertTrue(coalIn, "the furnace cooked with no Stoker")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdStk04LitIsSetOneTickAfterWorkingStarts(helper: GameTestHelper) {
        val (fire, furnace, _) = scene(helper)
        helper.assertFalse(isLit(helper, furnace), "the furnace is lit before any Stoker")
        var startedAt = -1L
        helper.onEachTick {
            if (startedAt < 0) {
                if (isWorking(helper, fire)) startedAt = helper.tick
            } else if (helper.tick == startedAt + 1) {
                helper.assertTrue(isLit(helper, furnace), "LIT is false 1 tick after Working started")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdStk05FullOutputGivesStandby(helper: GameTestHelper) {
        val (fire, furnace, _) = scene(helper)
        furnace.setItem(2, ItemStack(Items.IRON_INGOT, 64))
        var arrivedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            val spot = worker.spot ?: return@onEachTick
            helper.assertTrue(worker.status != WorkerStatus.WORKING, "the Stoker worked a furnace whose output is full")
            if (arrivedAt < 0 && WorkEngine.isArrived(fire, spot)) arrivedAt = helper.tick
            if (arrivedAt < 0) return@onEachTick
            if (worker.status == WorkerStatus.STANDBY) {
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - arrivedAt <= 20, "not in Standby 20 ticks after reaching the spot")
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdStk06ReleaseLetsAFurnaceWithNoFuelGoOut(helper: GameTestHelper) {
        val (fire, furnace, _) = scene(helper)
        var releasedAt = -1L
        helper.onEachTick {
            if (releasedAt < 0) {
                if (isWorking(helper, fire)) {
                    TestKit.release(helper, fire, furnace.blockPos)
                    releasedAt = helper.tick
                }
            } else if (!isLit(helper, furnace)) {
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - releasedAt < 2, "the furnace is lit 2 ticks after the Stoker stopped, with no fuel")
            }
        }
    }

    /** The coal goes in while the Stoker heats, so that vanilla cannot burn it before the Stoker stops. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 700)
    fun cdStk06ReleaseLetsVanillaBurnTheCoal(helper: GameTestHelper) {
        val (fire, furnace, _) = scene(helper)
        var coalSince = -1L
        var releasedAt = -1L
        helper.onEachTick {
            if (coalSince < 0) {
                if (isWorking(helper, fire)) {
                    furnace.setItem(1, ItemStack(Items.COAL))
                    coalSince = helper.tick
                }
            } else if (releasedAt < 0) {
                if (helper.tick - coalSince >= 40) {
                    helper.assertTrue(furnace.getItem(1).count == 1, "the coal burned while the Stoker heated the furnace")
                    TestKit.release(helper, fire, furnace.blockPos)
                    releasedAt = helper.tick
                }
            } else if (furnace.getItem(1).isEmpty) {
                helper.assertTrue(isLit(helper, furnace), "the furnace went out although it burned the coal")
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - releasedAt < 2, "the coal is unburned 2 ticks after the Stoker stopped")
            }
        }
    }

    /** The menu draws the flame from `litTime / litDuration`, so the check is that ratio, as the menu computes it. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdStk07GaugeIsNotEmptyWhileCooking(helper: GameTestHelper) {
        val (fire, furnace, _) = scene(helper)
        var checks = 0
        helper.onEachTick {
            if (!isWorking(helper, fire)) return@onEachTick
            val duration = if (furnace.litDuration == 0) AbstractFurnaceBlockEntity.BURN_TIME_STANDARD else furnace.litDuration
            val gauge = furnace.litTime.toFloat() / duration
            helper.assertTrue(furnace.litTime > 0 && furnace.litDuration > 0 && gauge >= 0.5f, "the flame gauge reads $gauge while cooking")
            if (++checks >= 100) helper.succeed()
        }
    }
}
