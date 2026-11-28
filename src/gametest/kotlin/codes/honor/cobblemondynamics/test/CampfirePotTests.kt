package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.campfirepot.CampfireBlock
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CampfirePotTests {
    private fun placePot(helper: GameTestHelper, rel: BlockPos, lidClosed: Boolean, recipeCount: Int): CampfireBlockEntity {
        val state = CobblemonBlocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LID, lidClosed)
        helper.setBlock(rel, state)
        val pot = helper.level.getBlockEntity(helper.absolutePos(rel)) as? CampfireBlockEntity
            ?: throw GameTestAssertException("no CampfireBlockEntity at $rel")
        if (recipeCount > 0) {
            pot.setItem(1, ItemStack(Items.APPLE, recipeCount))
            pot.setItem(2, ItemStack(Items.SUGAR, recipeCount))
            pot.setItem(3, ItemStack(Items.STICK, recipeCount))
        }
        return pot
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800, batch = "campfire_pot_demand")
    fun cdSnt06CharmanderClaimsOnlyClosedCookingPot(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val emptyClosed = placePot(helper, BlockPos(7, 2, 4), lidClosed = true, recipeCount = 0)
        val openCooking = placePot(helper, BlockPos(4, 2, 5), lidClosed = false, recipeCount = 32)
        val activePot = placePot(helper, BlockPos(11, 2, 2), lidClosed = true, recipeCount = 32)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val emptyKey = TestKit.keyOf(helper, emptyClosed.blockPos)
        val openKey = TestKit.keyOf(helper, openCooking.blockPos)
        val activeKey = TestKit.keyOf(helper, activePot.blockPos)
        var activeWorkingTicks = 0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            val claim = TestKit.claimOf(helper, charmander)
            if (worker.status == WorkerStatus.WORKING) {
                helper.assertFalse(claim?.key == emptyKey, "CD-SNT-06: Charmander entered Working on empty closed pot")
                helper.assertFalse(claim?.key == openKey, "CD-SNT-06: Charmander entered Working on open-lid pot")
                helper.assertTrue(
                    claim?.kind == ClaimKind.WORK &&
                        claim.key == activeKey &&
                        TestKit.engine(helper).roleOf(charmander.pokemon.uuid)?.definition?.id == id("stoker"),
                    "CD-SNT-06: Charmander worked unexpected target $claim",
                )
                if (++activeWorkingTicks >= 40) helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 800, batch = "campfire_pot_speed")
    fun cdSnt07StokerDoublesCampfirePotCookingSpeed(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(13, 1, 13), owner)
        TestKit.workMode(pasture, true)
        val unassisted = placePot(helper, BlockPos(2, 2, 2), lidClosed = true, recipeCount = 8)
        val stoked = placePot(helper, BlockPos(13, 2, 9), lidClosed = true, recipeCount = 8)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        TestKit.moveTo(helper, charmander, BlockPos(13, 2, 10))

        var unassistedFirstAt = -1L
        var unassistedDuration = -1L
        var stokedBaselineCount = -1
        var stokedStartAt = -1L
        var stokedDuration = -1L

        helper.onEachTick {
            val uResult = unassisted.getItem(CampfireBlockEntity.RESULT_SLOT)
            if (unassistedFirstAt < 0 && uResult.count == 1) {
                unassistedFirstAt = helper.tick
            } else if (unassistedFirstAt >= 0 && unassistedDuration < 0 && uResult.count == 2) {
                unassistedDuration = helper.tick - unassistedFirstAt
            }

            val isWorking = TestKit.workerOf(helper, charmander)?.status == WorkerStatus.WORKING
            val sResult = stoked.getItem(CampfireBlockEntity.RESULT_SLOT)
            if (stokedBaselineCount < 0) {
                if (isWorking) stokedBaselineCount = sResult.count
            } else if (stokedStartAt < 0) {
                helper.assertTrue(isWorking, "CD-SNT-07: Stoker left Working before completing baseline craft")
                if (sResult.count == stokedBaselineCount + 1) {
                    stokedStartAt = helper.tick
                }
            } else if (stokedDuration < 0) {
                helper.assertTrue(isWorking, "CD-SNT-07: Stoker dropped out of Working between back-to-back Campfire Pot crafts")
                if (sResult.count == stokedBaselineCount + 2) {
                    helper.assertTrue(
                        sResult.`is`(CobblemonItems.CANDIED_APPLE),
                        "CD-SNT-07: expected candied_apple result, got $sResult",
                    )
                    stokedDuration = helper.tick - stokedStartAt
                }
            }

            if (unassistedDuration > 0 && stokedDuration > 0) {
                helper.assertTrue(
                    unassistedDuration == 100L,
                    "CD-SNT-07: unassisted Campfire Pot took $unassistedDuration ticks instead of 100",
                )
                helper.assertTrue(
                    stokedDuration in 49L..53L,
                    "CD-SNT-07: Stoker-assisted Campfire Pot took $stokedDuration ticks (unassisted $unassistedDuration)",
                )
                helper.succeed()
            }
        }
    }
}
