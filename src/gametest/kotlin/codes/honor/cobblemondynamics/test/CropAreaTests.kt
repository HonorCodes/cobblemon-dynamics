package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The area of a farming use (R94) in the real engine, with its work area and lanes. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CropAreaTests {
    /**
     * CD-CUL-11: a level 20 Diglett leashed to the middle of a row of five wheat crops at age 0. At its first use, the two crops next to
     * the target grow too, each through a growth use with no player, and the two at the ends do not. No random tick grows a crop here.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdCul11AreaGrowsTheNearestCrops(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val row = (3..7).map { x -> BlockPos(x, 2, 3) }
        for (crop in row) {
            helper.setBlock(crop.below(), Blocks.FARMLAND)
            helper.setBlock(crop, Blocks.WHEAT.defaultBlockState())
        }
        val target = row[2]
        val diglett = TestKit.tether(helper, pasture, owner, "diglett level=20")
        val bound = TestKit.bind(helper, owner, diglett, target)
        helper.assertTrue(bound?.roleId == id("cultivator"), "the Diglett bound as ${bound?.roleId}")
        val since = helper.level.gameTime
        val ageAt = { rel: BlockPos -> helper.getBlockState(rel).getOptionalValue(CropBlock.AGE).orElse(-1) }
        helper.onEachTick {
            if (ageAt(target) == 0) return@onEachTick
            val ages = row.map(ageAt)
            helper.assertTrue(ages[1] > 0 && ages[3] > 0, "CD-CUL-11: the crops next to the target hold ages $ages after the first use")
            helper.assertTrue(ages[0] == 0 && ages[4] == 0, "CD-CUL-11: the crops at the ends hold ages $ages after the first use")
            val used = BonemealUses.uses.filter { it.tick >= since && it.player == null }.map { it.pos }
            for (crop in row.subList(1, 4)) {
                helper.assertTrue(helper.absolutePos(crop) in used, "CD-CUL-11: no growth use posted its event at $crop")
            }
            helper.succeed()
        }
    }
}
