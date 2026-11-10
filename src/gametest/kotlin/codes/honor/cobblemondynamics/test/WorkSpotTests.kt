package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * CD-ASG-09 on real blocks, after the approver's play-test ruling (2026-11-10): a worker stands beside its target, on the floor that the
 * target stands on, and never on top of it. A furnace stands on the template's floor, so its base height is the floor height.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WorkSpotTests {
    private fun spotOf(helper: GameTestHelper, entity: PokemonEntity): BlockPos {
        val spot = TestKit.workerOf(helper, entity)?.spot ?: throw GameTestAssertException("a claim with no spot")
        return TestKit.relative(helper, spot)
    }

    /**
     * A furnace with input has open floor on all four sides and room on top. A Charmander is held 5 blocks south of it until it claims.
     * The Stoker reaches 6 blocks, so the old rule, nearest to the worker, gave it the cell where it stood. Its spot is beside the
     * furnace at floor height, not on the furnace, and it Works there.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg09SpotIsBesideAFurnaceOnTheFloor(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val furnace = BlockPos(7, 2, 4)
        TestKit.furnace(helper, furnace, ItemStack(Items.RAW_IRON, 64))
        helper.assertTrue(helper.getBlockState(furnace.above()).isAir, "the cell on top of the furnace is not open")
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val hold = BlockPos(7, 2, 9)
        var checked = false
        helper.onEachTick {
            if (!checked) {
                if (TestKit.claimOf(helper, charmander) == null) {
                    TestKit.moveTo(helper, charmander, hold)
                    return@onEachTick
                }
                val spot = spotOf(helper, charmander)
                helper.assertTrue(spot.y == furnace.y, "CD-ASG-09: the spot $spot is not at the floor height of the furnace at $furnace")
                val beside = abs(spot.x - furnace.x) + abs(spot.z - furnace.z) == 1
                helper.assertTrue(beside, "CD-ASG-09: the spot $spot is not beside the furnace at $furnace")
                checked = true
                return@onEachTick
            }
            if (TestKit.workerOf(helper, charmander)?.status == WorkerStatus.WORKING) helper.succeed()
        }
    }

    /**
     * The Stoker reaches 6 blocks, and it still works from beside its furnace. Stone walls two blocks high close three sides of the
     * furnace, so the only spot beside it is south of it. The Charmander, held 5 blocks away until it claims, takes that spot, Works
     * there, and keeps the furnace lit with an empty fuel slot.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdAsg09StokerWorksItsFurnaceFromBesideIt(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val furnaceRel = BlockPos(7, 2, 4)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64))
        for (y in 2..3) for (wall in listOf(BlockPos(6, y, 4), BlockPos(8, y, 4), BlockPos(7, y, 3))) helper.setBlock(wall, Blocks.STONE)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val hold = BlockPos(9, 2, 8)
        val front = BlockPos(7, 2, 5)
        var claimed = false
        helper.onEachTick {
            if (!claimed) {
                if (TestKit.claimOf(helper, charmander) == null) {
                    TestKit.moveTo(helper, charmander, hold)
                    return@onEachTick
                }
                claimed = true
            }
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            val spot = spotOf(helper, charmander)
            helper.assertTrue(spot == front, "CD-ASG-09: the Stoker took the spot $spot, not the one beside its furnace at $front")
            val lit = helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
            if (worker.status == WorkerStatus.WORKING && lit && furnace.getItem(1).isEmpty) helper.succeed()
        }
    }
}
