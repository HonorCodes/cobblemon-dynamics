package codes.honor.cobblemondynamics.test

import appeng.blockentity.misc.ChargerBlockEntity
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The Cranker on AE2's real Crank and Charger, which AE2's own `ICrankable` API joins (CD-KIN-06). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class Ae2CrankTests {
    /** In the floor, so that the crank on its top stands at the height of the Machop. */
    private val chargerRel = BlockPos(7, 1, 5)
    private val crankRel = chargerRel.above()

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, helper.absolutePos(crankRel)) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /**
     * A Lv10 Machop at an AE2 Crank on a Charger that holds a certus quartz crystal. Each turn gives the charger 160 AE and a crystal
     * takes 1,600, so one turn every 18 ticks fills the charger in about 180 ticks of work, and the charger then charges the crystal.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdKin06MachopChargesCertusThroughTheCrank(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        // A charger takes a turn on every face but its front, so its front faces north, away from the crank on its top.
        helper.setBlock(chargerRel, facing(TestKit.ae2Block("ae2:charger").defaultBlockState(), Direction.NORTH))
        helper.setBlock(crankRel, facing(TestKit.ae2Block("ae2:crank").defaultBlockState(), Direction.UP))
        val charger = helper.level.getBlockEntity(helper.absolutePos(chargerRel)) as? ChargerBlockEntity
            ?: throw GameTestAssertException("no AE2 Charger")
        val crystal = ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:certus_quartz_crystal")))
        helper.assertTrue(charger.internalInventory.insertItem(0, crystal, false).isEmpty, "the charger does not take the crystal")
        val machop = TestKit.tether(helper, pasture, owner, "machop level=10")
        var workingSince = -1L
        helper.onEachTick {
            if (workingSince < 0) {
                if (!isWorkingOn(helper, machop)) return@onEachTick
                val role = TestKit.engine(helper).roleOf(machop.pokemon.uuid)?.definition?.id
                helper.assertTrue(role == id("cranker_ae2"), "the Machop works the AE2 Crank as $role")
                workingSince = helper.tick
                return@onEachTick
            }
            if (BuiltInRegistries.ITEM.getKey(charger.internalInventory.getStackInSlot(0).item) == CHARGED) {
                helper.succeed()
                return@onEachTick
            }
            helper.assertTrue(helper.tick - workingSince <= 600, "CD-KIN-06: the crystal is not charged after 600 ticks of work")
        }
    }

    /** [state] of an AE2 block with its `facing` property set to [direction]. */
    private fun facing(state: BlockState, direction: Direction): BlockState {
        val property = state.block.stateDefinition.getProperty("facing") as? DirectionProperty
            ?: throw GameTestAssertException("${state.block} has no facing property")
        return state.setValue(property, direction)
    }

    private companion object {
        val CHARGED: ResourceLocation = ResourceLocation.parse("ae2:charged_certus_quartz_crystal")
    }
}
