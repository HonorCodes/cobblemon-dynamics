package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.cell.ChargeCellBlockEntity
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ComparatorBlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Charge Cell on real blocks (the approver's ruling, 2026-11-10): NeoForge's energy capability on each face, a Generator worker,
 * the test battery, two cells side by side, comparators, the loot table and the recipe.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ChargeCellTests {
    /** A new Charge Cell at [rel], given [fe] FE through its own capability. */
    private fun cellAt(helper: GameTestHelper, rel: BlockPos, fe: Int = 0): ChargeCellBlockEntity {
        helper.setBlock(rel, DynamicsBlocks.CHARGE_CELL.get())
        val cell = helper.getBlockEntity<ChargeCellBlockEntity>(rel)
        if (fe > 0) {
            val taken = capability(helper, rel, Direction.UP)?.receiveEnergy(fe, false)
            helper.assertTrue(taken == fe, "the cell at $rel took $taken of $fe FE")
        }
        return cell
    }

    private fun capability(helper: GameTestHelper, rel: BlockPos, side: Direction): IEnergyStorage? =
        helper.level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(rel), side)

    private fun stored(helper: GameTestHelper, rel: BlockPos): Int = capability(helper, rel, Direction.UP)?.energyStored ?: -1

    /**
     * CD-GEN-08: the cell exposes NeoForge's energy capability on all six faces, and each face takes FE. A Lv100 Pikachu then charges
     * it in offers of 5,000 FE (CD-GEN-01, CD-GEN-02). The cell stops at exactly 50,000 FE, and the Pikachu waits in Standby.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdGen08PikachuChargesAChargeCellToFull(helper: GameTestHelper) {
        val cellRel = BlockPos(7, 2, 3)
        val cell = cellAt(helper, cellRel)
        for (side in Direction.entries) {
            val storage = capability(helper, cellRel, side)
            helper.assertTrue(storage != null && storage.canReceive(), "CD-GEN-08: no capability that can receive on face $side")
            helper.assertTrue(storage?.receiveEnergy(1, true) == 1, "CD-GEN-08: face $side refuses 1 FE")
            helper.assertTrue(storage?.maxEnergyStored == 50_000, "CD-GEN-08: face $side holds up to ${storage?.maxEnergyStored} FE")
        }
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=100")
        val key = TestKit.keyOf(helper, cell.blockPos)
        var last = 0
        var fullAt = -1L
        helper.onEachTick {
            val fe = stored(helper, cellRel)
            helper.assertTrue(fe in 0..50_000, "CD-GEN-08: the cell holds $fe FE")
            helper.assertTrue(fe - last == 0 || fe - last == 5_000, "the cell gained ${fe - last} FE in one tick")
            last = fe
            if (fe < 50_000) return@onEachTick
            if (fullAt < 0) fullAt = helper.tick
            val worker = TestKit.workerOf(helper, pikachu) ?: return@onEachTick
            if (worker.status == WorkerStatus.STANDBY) {
                helper.assertTrue(TestKit.claimOf(helper, pikachu)?.key == key, "the Pikachu waits at another target")
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - fullAt < 20, "the Pikachu is ${worker.status} 20 ticks after the cell filled")
            }
        }
    }

    /**
     * CD-GEN-09: a cell with 10,000 FE beside a test battery that takes 1,000 FE and two that take any amount. In its first tick it
     * gives them 1,000, 4,500 and 4,500 FE, one real insert each.
     */
    @GameTest(template = "floor_9x5x9")
    fun cdGen09CellSplitsItsFeFairlyBetweenThreeBatteries(helper: GameTestHelper) {
        val batteries = listOf(
            TestKit.testBattery(helper, BlockPos(4, 2, 3), capacity = 1_000),
            TestKit.testBattery(helper, BlockPos(5, 2, 4)),
            TestKit.testBattery(helper, BlockPos(3, 2, 4)),
        )
        val cellRel = BlockPos(4, 2, 4)
        cellAt(helper, cellRel, fe = 10_000)
        helper.succeedWhen {
            helper.assertTrue(stored(helper, cellRel) == 0, "the cell still holds ${stored(helper, cellRel)} FE")
            val got = batteries.map { it.stored }
            helper.assertTrue(got == listOf(1_000, 4_500, 4_500), "CD-GEN-09: the batteries got $got FE")
            val inserts = batteries.map { battery -> battery.offers.map { it.accepted } }
            helper.assertTrue(inserts == got.map { listOf(it) }, "CD-GEN-09: the real inserts were $inserts")
        }
    }

    /** CD-GEN-09: two cells side by side, with 10,000 and 0 FE, settle at 5,000 FE each and hold it for 40 ticks. */
    @GameTest(template = "floor_9x5x9")
    fun cdGen09TwoCellsSettleAtOneLevelAndStop(helper: GameTestHelper) {
        val fullRel = BlockPos(3, 2, 4)
        val emptyRel = BlockPos(4, 2, 4)
        cellAt(helper, fullRel, fe = 10_000)
        cellAt(helper, emptyRel)
        var settledAt = -1L
        helper.onEachTick {
            val a = stored(helper, fullRel)
            val b = stored(helper, emptyRel)
            helper.assertTrue(a + b == 10_000, "CD-GEN-09: the cells hold $a and $b FE, ${a + b} in all")
            if (settledAt < 0) {
                if (a == 5_000 && b == 5_000) {
                    settledAt = helper.tick
                } else {
                    helper.assertTrue(helper.tick < 5, "the cells hold $a and $b FE after ${helper.tick} ticks")
                }
                return@onEachTick
            }
            helper.assertTrue(a == 5_000 && b == 5_000, "CD-GEN-09: FE moved between settled cells: $a and $b")
            if (helper.tick - settledAt >= 40) helper.succeed()
        }
    }

    /** CD-GEN-10: comparators behind cells with 0, 25,000 and 50,000 FE read 0, 8 and 15. */
    @GameTest(template = "floor_9x5x9")
    fun cdGen10ComparatorsReadTheCellsFill(helper: GameTestHelper) {
        val cases = listOf(BlockPos(1, 2, 2) to 0, BlockPos(4, 2, 2) to 25_000, BlockPos(7, 2, 2) to 50_000)
        val comparators = cases.map { (cellRel, _) ->
            cellAt(helper, cellRel)
            val comparatorRel = cellRel.south()
            val toCell = helper.absolutePos(cellRel).subtract(helper.absolutePos(comparatorRel))
            val facing = Direction.fromDelta(toCell.x, toCell.y, toCell.z) ?: error("no cell beside $comparatorRel")
            helper.setBlock(comparatorRel, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing))
            comparatorRel
        }
        for ((cellRel, fe) in cases) if (fe > 0) capability(helper, cellRel, Direction.UP)?.receiveEnergy(fe, false)
        helper.succeedWhen {
            val signals = comparators.map { helper.getBlockEntity<ComparatorBlockEntity>(it).outputSignal }
            helper.assertTrue(signals == listOf(0, 8, 15), "CD-GEN-10: the comparators read $signals")
        }
    }

    /**
     * CD-GEN-11: a cell with 12,345 FE is broken. Its drop holds 12,345 FE as a data component and shows it in its tooltip, and the
     * cell that the drop places holds 12,345 FE.
     */
    @GameTest(template = "floor_9x5x9")
    fun cdGen11BrokenCellKeepsItsFe(helper: GameTestHelper) {
        val cellRel = BlockPos(4, 2, 4)
        cellAt(helper, cellRel, fe = 12_345)
        // GameTestHelper.destroyBlock drops nothing, so break the cell through the level, as a player's break does.
        helper.level.destroyBlock(helper.absolutePos(cellRel), true)
        val drops = helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB(helper.absolutePos(cellRel)).inflate(2.0))
        helper.assertTrue(drops.size == 1, "the broken cell dropped ${drops.map { it.item }}")
        val stack = drops.single().item
        helper.assertTrue(stack.`is`(DynamicsBlocks.CHARGE_CELL_ITEM.get()) && stack.count == 1, "the broken cell dropped $stack")
        val component = stack.get(DynamicsBlocks.STORED_ENERGY.get())
        helper.assertTrue(component == 12_345, "CD-GEN-11: the drop's stored_energy component is $component")
        val owner = TestKit.owner(helper)
        val tooltip = stack.getTooltipLines(Item.TooltipContext.of(helper.level), owner, TooltipFlag.NORMAL).map { it.string }
        helper.assertTrue("12,345 / 50,000 FE" in tooltip, "CD-GEN-11: the tooltip reads $tooltip")
        drops.single().discard()
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack.copy())
        TestKit.clickBlock(helper, owner, BlockPos(6, 1, 4), Direction.UP)
        val placedRel = BlockPos(6, 2, 4)
        helper.assertBlockPresent(DynamicsBlocks.CHARGE_CELL.get(), placedRel)
        helper.assertTrue(stored(helper, placedRel) == 12_345, "CD-GEN-11: the placed cell holds ${stored(helper, placedRel)} FE")
        helper.succeed()
    }

    /** CD-GEN-12: the recipe of the Recipes table makes one cell, and the same grid without the Thunder Stone makes nothing. */
    @GameTest(template = "floor_9x5x9")
    fun cdGen12CellCraftsFromCopperRedstoneAndAThunderStone(helper: GameTestHelper) {
        val stone = BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:thunder_stone"))
        helper.assertTrue(stone != Items.AIR, "Cobblemon registers no cobblemon:thunder_stone")
        val copper = Items.COPPER_INGOT
        val redstone = Items.REDSTONE
        val grid = listOf(copper, redstone, copper, copper, stone, copper, copper, redstone, copper)
        val level = helper.level
        fun craft(items: List<Item>): ItemStack {
            val input = CraftingInput.of(3, 3, items.map { if (it == Items.AIR) ItemStack.EMPTY else ItemStack(it) })
            return level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level)
                .map { it.value().assemble(input, level.registryAccess()) }
                .orElse(ItemStack.EMPTY)
        }
        val made = craft(grid)
        helper.assertTrue(made.`is`(DynamicsBlocks.CHARGE_CELL_ITEM.get()) && made.count == 1, "CD-GEN-12: the grid makes $made")
        val without = craft(grid.map { if (it == stone) Items.AIR else it })
        helper.assertTrue(without.isEmpty, "CD-GEN-12: the grid without the Thunder Stone makes $without")
        helper.succeed()
    }
}
