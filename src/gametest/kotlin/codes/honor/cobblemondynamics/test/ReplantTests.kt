package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.handler.farming.HarvestApply
import codes.honor.cobblemondynamics.handler.farming.SeedBlock
import codes.honor.cobblemondynamics.handler.tree.LeafSaplings
import codes.honor.cobblemondynamics.handler.tree.TreeRules
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CocoaBlock
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The replants of 1.2.1 (R94) against the real loot tables and blocks of Minecraft. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ReplantTests {
    /** CD-LUM-31: the leaf source reads the sapling from the real loot tables, also where the names differ. */
    @GameTest(template = "floor_15x6x15")
    fun cdLum31LeafSaplingsOnRealTables(helper: GameTestHelper) {
        val origin = helper.absolutePos(BlockPos(7, 2, 7))
        val jungle = LeafSaplings.of(helper.level, ResourceLocation.withDefaultNamespace("jungle_leaves"), origin)
        helper.assertTrue(jungle == Items.JUNGLE_SAPLING, "CD-LUM-31: jungle leaves give $jungle")
        val azalea = LeafSaplings.of(helper.level, ResourceLocation.withDefaultNamespace("azalea_leaves"), origin)
        helper.assertTrue(azalea == Items.AZALEA, "CD-LUM-31: azalea leaves give $azalea")
        val stone = LeafSaplings.of(helper.level, ResourceLocation.withDefaultNamespace("stone"), origin)
        helper.assertTrue(stone == null, "CD-LUM-31: stone gives $stone")
        helper.succeed()
    }

    /**
     * CD-LUM-18, CD-LUM-32: a level 100 Scyther bound to a hand-built 2 × 2 jungle trunk with natural jungle leaves fells it, and the four
     * cells of the square hold jungle saplings after the fell, whatever the drops held.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdLum18FullSquareOnAGiantJungle(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val base = BlockPos(11, 2, 9)
        val square = TreeRules.square(base)
        for (cell in square) {
            helper.setBlock(cell.below(), Blocks.DIRT)
            for (y in 0 until 8) helper.setBlock(cell.above(y), Blocks.JUNGLE_LOG)
        }
        fun layer(y: Int, radius: Int) {
            for (x in base.x - radius..base.x + 1 + radius) {
                for (z in base.z - radius..base.z + 1 + radius) {
                    val pos = BlockPos(x, y, z)
                    if (square.none { it.x == x && it.z == z } || y > base.y + 7) helper.setBlock(pos, Blocks.JUNGLE_LEAVES)
                }
            }
        }
        for (y in 6..8) layer(y, 3)
        layer(9, 2)
        layer(10, 1)
        val scyther = TestKit.tether(helper, pasture, owner, "scyther level=100")
        val binding = TestKit.bind(helper, owner, scyther, base.above(3).east())
        helper.assertTrue(binding?.roleId == id("lumberjack"), "the Scyther bound as ${binding?.roleId}")
        helper.assertTrue(binding?.target == helper.absolutePos(base), "CD-LUM-03: the binding names ${binding?.target}, not the corner")
        helper.onEachTick {
            if (helper.getBlockState(base).`is`(Blocks.JUNGLE_LOG)) return@onEachTick
            // The base cell is the last step, and the replant comes in the tick of the last removal.
            for (cell in square) {
                val state = helper.getBlockState(cell)
                helper.assertTrue(state.`is`(Blocks.JUNGLE_SAPLING), "CD-LUM-18: the cell $cell of the square holds $state after the fell")
            }
            helper.assertTrue(BindingStore.read(scyther.pokemon) != null, "CD-LUM-19: the Scyther lost its binding at the fell")
            helper.succeed()
        }
    }

    /** CD-HRV-22: the seed item of each vanilla crop is the item of a pick-block, and it places the crop's own block. */
    @GameTest(template = "floor_15x6x15")
    fun cdHrv22SeedItemsOfRealCrops(helper: GameTestHelper) {
        val crops = listOf(
            Blocks.WHEAT to Items.WHEAT_SEEDS,
            Blocks.CARROTS to Items.CARROT,
            Blocks.POTATOES to Items.POTATO,
            Blocks.BEETROOTS to Items.BEETROOT_SEEDS,
        )
        for ((i, crop) in crops.withIndex()) {
            val pos = BlockPos(3 + 2 * i, 2, 5)
            val block = crop.first as CropBlock
            helper.setBlock(pos.below(), Blocks.FARMLAND)
            helper.setBlock(pos, block.getStateForAge(block.maxAge))
        }
        val cocoa = BlockPos(7, 2, 9)
        helper.setBlock(cocoa.north(), Blocks.JUNGLE_LOG)
        helper.setBlock(cocoa, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.NORTH).setValue(CocoaBlock.AGE, 2))
        val cells = crops.indices.map { BlockPos(3 + 2 * it, 2, 5) } + cocoa
        val expected = crops.map { it.second } + Items.COCOA_BEANS
        for ((rel, item) in cells.zip(expected)) {
            val pos = helper.absolutePos(rel)
            val state = helper.level.getBlockState(pos)
            val seed = HarvestApply.seedItemOf(helper.level, pos, state)
            helper.assertTrue(seed == item, "CD-HRV-22: the seed item of $state is $seed, not $item")
            val block = HarvestApply.seedBlockOf(helper.level, pos, state)
            helper.assertTrue(block == SeedBlock.Own, "CD-HRV-22: the seed item of $state places $block")
        }
        helper.succeed()
    }
}
