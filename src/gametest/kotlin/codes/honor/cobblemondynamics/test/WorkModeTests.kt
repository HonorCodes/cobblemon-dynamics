package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import codes.honor.cobblemondynamics.pasture.WorkModeService
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WorkModeTests {
    @GameTest(template = "floor_9x5x9")
    fun cdWmd01NewPastureIsOff(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        helper.assertFalse(PastureAdapter.isWorkMode(pasture), "a new pasture is in Work Mode")
        helper.succeed()
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWmd02WorkModeSurvivesSaveAndLoad(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        WorkModeService.apply(helper.level, pasture.blockPos, true)
        val tag = pasture.saveWithFullMetadata(helper.level.registryAccess())
        val reloaded = BlockEntity.loadStatic(pasture.blockPos, pasture.blockState, tag, helper.level.registryAccess())
            as PokemonPastureBlockEntity
        helper.assertTrue(PastureAdapter.isWorkMode(reloaded), "Work Mode reads off after a reload")
        helper.succeed()
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWmd03PlacerTogglesWithinOneTick(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), owner)
        TestKit.standBeside(owner, pasture)
        WorkModeService.request(owner, pasture.blockPos, true)
        helper.runAfterDelay(1) { helper.assertTrue(PastureAdapter.isWorkMode(pasture), "on was not applied") }
        // The second request waits out the 10-tick cooldown of CD-SEC-03.
        helper.runAfterDelay(12) { WorkModeService.request(owner, pasture.blockPos, false) }
        helper.runAfterDelay(13) {
            helper.assertFalse(PastureAdapter.isWorkMode(pasture), "off was not applied")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWmd04OperatorTogglesAnyPasture(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        val operator = TestKit.operator(helper)
        TestKit.standBeside(operator, pasture)
        WorkModeService.request(operator, pasture.blockPos, true)
        helper.runAfterDelay(1) {
            helper.assertTrue(PastureAdapter.isWorkMode(pasture), "an operator's request left Work Mode off")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_9x5x9")
    fun cdSec01StrangerCannotToggle(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        WorkModeService.apply(helper.level, pasture.blockPos, true)
        val stranger = TestKit.owner(helper, "stranger")
        TestKit.standBeside(stranger, pasture)
        val decision = WorkModeService.request(stranger, pasture.blockPos, false)
        helper.assertTrue(decision is Decision.Reject, "a stranger's request was not rejected: $decision")
        helper.runAfterDelay(1) {
            helper.assertTrue(PastureAdapter.isWorkMode(pasture), "a stranger turned Work Mode off")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_9x5x9")
    fun cdSec02OwnerlessPastureAcceptsOperatorsOnly(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        pasture.ownerId = null
        val player = TestKit.owner(helper, "player")
        TestKit.standBeside(player, pasture)
        WorkModeService.request(player, pasture.blockPos, true)
        helper.runAfterDelay(1) {
            helper.assertFalse(PastureAdapter.isWorkMode(pasture), "a regular player turned on an ownerless pasture")
            val operator = TestKit.operator(helper)
            TestKit.standBeside(operator, pasture)
            WorkModeService.request(operator, pasture.blockPos, true)
        }
        helper.runAfterDelay(2) {
            helper.assertTrue(PastureAdapter.isWorkMode(pasture), "an operator could not turn on an ownerless pasture")
            helper.succeed()
        }
    }

    /** The Nether request comes from a fake player made in the Nether, because a fake player cannot change dimension. */
    @GameTest(template = "floor_9x5x9")
    fun cdSec04DistanceAndDimension(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), owner)
        val center = Vec3.atCenterOf(pasture.blockPos)

        TestKit.place(owner, center.x + 10.1, center.y, center.z)
        helper.assertTrue(WorkModeService.request(owner, pasture.blockPos, true) is Decision.Reject, "the 10.1-block request passed")

        val nether = helper.level.server.getLevel(Level.NETHER) ?: throw IllegalStateException("no Nether")
        val inNether = FakePlayerFactory.get(nether, owner.gameProfile)
        TestKit.place(inNether, center.x, center.y, center.z)
        val fromNether = WorkModeService.request(inNether, pasture.blockPos, true, helper.level)
        helper.assertTrue(fromNether is Decision.Reject, "the request from the Nether passed")
        helper.assertFalse(PastureAdapter.isWorkMode(pasture), "a rejected request turned Work Mode on")

        TestKit.place(owner, center.x + 10.0, center.y, center.z)
        helper.assertTrue(WorkModeService.request(owner, pasture.blockPos, true) == Decision.Apply, "the 10.0-block request failed")
        helper.assertTrue(PastureAdapter.isWorkMode(pasture), "the 10.0-block request left Work Mode off")
        helper.succeed()
    }

    @GameTest(template = "floor_9x5x9")
    fun cdSec05RepeatedOnStaysOn(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), owner)
        TestKit.standBeside(owner, pasture)
        WorkModeService.request(owner, pasture.blockPos, true)
        helper.runAfterDelay(10) {
            val again = WorkModeService.request(owner, pasture.blockPos, true)
            helper.assertTrue(again == Decision.Apply, "the repeated request 10 ticks later was not applied: $again")
        }
        helper.runAfterDelay(11) {
            helper.assertTrue(PastureAdapter.isWorkMode(pasture), "a repeated on request turned Work Mode off")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_9x5x9")
    fun cdSec06TwoOperatorsSameTick(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        val first = TestKit.operator(helper, "op_a")
        val second = TestKit.operator(helper, "op_b")
        TestKit.standBeside(first, pasture)
        TestKit.standBeside(second, pasture)
        val on = WorkModeService.request(first, pasture.blockPos, true)
        val off = WorkModeService.request(second, pasture.blockPos, false)
        helper.assertTrue(on == Decision.Apply && off == Decision.Apply, "both requests should apply: $on, $off")
        helper.runAfterDelay(1) {
            helper.assertFalse(PastureAdapter.isWorkMode(pasture), "the final state is on")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 450)
    fun cdWmd06OffPastureGivesNoClaims(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        TestKit.moveTo(helper, fire, BlockPos(7, 1, 5))
        helper.onEachTick { helper.assertTrue(TestKit.claimOf(helper, fire) == null, "a Pokémon in a pasture that is off got a claim") }
        helper.runAtTickTime(400) { helper.succeed() }
    }

    /** No per-player limit, so only the pasture's own limit can refuse the Pokémon. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdBal06SeventeenthPokemonIsRefused(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        WorkModeService.apply(helper.level, pasture.blockPos, true)
        repeat(15) { TestKit.tether(helper, pasture, owner, "rattata level=5") }
        helper.assertTrue(
            pasture.canAddPokemon(owner, TestKit.pokemon("rattata level=5"), Int.MAX_VALUE),
            "Cobblemon refused a 16th Pokémon",
        )
        TestKit.tether(helper, pasture, owner, "rattata level=5")
        helper.assertFalse(
            pasture.canAddPokemon(owner, TestKit.pokemon("rattata level=5"), Int.MAX_VALUE),
            "Cobblemon accepted a 17th Pokémon",
        )
        helper.succeed()
    }
}
