package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Work Leash on Create blocks: a bound target that Create takes out of the world loses its binding, as a broken one does
 * (CD-LSH-08), and a basin that two roles fit binds the role that sorts first (CD-LSH-16).
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CreateLeashTests {
    private fun owner(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return owner to pasture
    }

    /** The binding of [entity] and the index entry of [target] are both gone. */
    private fun assertUnbound(helper: GameTestHelper, owner: ServerPlayer, entity: PokemonEntity, target: BlockPos, cause: String) {
        val stored = Cobblemon.storage.getPC(owner)[entity.pokemon.uuid]
        helper.assertTrue(stored != null && BindingStore.read(stored) == null, "the binding remains 20 ticks after $cause")
        val index = helper.level.getData(DynamicsAttachments.LEASH_INDEX)
        helper.assertTrue(index.get(target) == null, "the target is still reserved 20 ticks after $cause")
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdLsh22WrenchPickupClearsBinding(helper: GameTestHelper) {
        val (owner, pasture) = owner(helper)
        val crankRel = BlockPos(7, 2, 5)
        val crank = CreateKit.handCrank(helper, crankRel, Direction.UP)
        val machop = TestKit.tether(helper, pasture, owner, "machop level=10")
        helper.assertTrue(TestKit.bind(helper, owner, machop, crankRel)?.target == crank.blockPos, "the Machop did not bind to the crank")
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllItems.WRENCH.get()))
        val result = TestKit.clickBlock(helper, owner, crankRel, sneak = true)
        helper.assertTrue(helper.getBlockState(crankRel).isAir, "the wrench did not pick up the crank: $result")
        helper.assertTrue(owner.inventory.hasAnyOf(setOf(AllBlocks.HAND_CRANK.get().asItem())), "the owner did not get the crank back")
        helper.runAfterDelay(20) {
            assertUnbound(helper, owner, machop, crank.blockPos, "the wrench pickup")
            helper.succeed()
        }
    }

    /**
     * A mechanical piston faces west with one extension pole behind it and the bound furnace in front. A creative motor on its north
     * side turns it at +16 RPM, which extends a west-facing piston, so the piston takes the furnace into a contraption.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdLsh23ContraptionClearsBinding(helper: GameTestHelper) {
        val (owner, pasture) = owner(helper)
        val furnaceRel = BlockPos(7, 2, 4)
        val pistonRel = furnaceRel.east()
        val furnace = TestKit.furnace(helper, furnaceRel, null)
        val pole = AllBlocks.PISTON_EXTENSION_POLE.defaultState.setValue(BlockStateProperties.FACING, Direction.WEST)
        helper.setBlock(pistonRel.east(), pole)
        val piston = AllBlocks.MECHANICAL_PISTON.defaultState
            .setValue(DirectionalKineticBlock.FACING, Direction.WEST)
            .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false)
        helper.setBlock(pistonRel, piston)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val binding = TestKit.bind(helper, owner, fire, furnaceRel)
        helper.assertTrue(binding?.target == furnace.blockPos, "the Charmander did not bind to the furnace: $binding")
        // The motor's shaft points south, into the piston, whose rotation axis is z.
        helper.setBlock(pistonRel.north(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(DirectionalKineticBlock.FACING, Direction.SOUTH))
        val around = AABB(helper.absolutePos(pistonRel)).inflate(4.0)
        var assembledAt = -1L
        helper.onEachTick {
            if (assembledAt < 0) {
                if (helper.getBlockState(furnaceRel).`is`(Blocks.FURNACE)) {
                    helper.assertTrue(helper.tick < 100, "the piston did not take the furnace in 100 ticks")
                    return@onEachTick
                }
                val contraptions = helper.level.getEntitiesOfClass(AbstractContraptionEntity::class.java, around)
                helper.assertTrue(contraptions.isNotEmpty(), "the furnace left its place, but no contraption holds it")
                assembledAt = helper.tick
            } else if (helper.tick - assembledAt >= 20) {
                assertUnbound(helper, owner, fire, furnace.blockPos, "the assembly")
                helper.succeed()
            }
        }
    }

    /**
     * A Water- and Psychic-type Slowbro clicks a Create basin, which both the Pump and Telekinesis match: the Pump binds with one click
     * (CD-LSH-16).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh16SlowbroOnABasinBindsAsPump(helper: GameTestHelper) {
        val basinRel = BlockPos(5, 2, 4)
        CreateKit.basin(helper, basinRel)
        val (owner, pasture) = owner(helper)
        val slowbro = TestKit.tether(helper, pasture, owner, "slowbro level=30")
        helper.runAfterDelay(1) {
            helper.assertTrue(TestKit.items(helper, basinRel) != null, "the basin has no item handler, so Telekinesis does not fit it")
            val binding = TestKit.bind(helper, owner, slowbro, basinRel)
            helper.assertTrue(binding?.roleId == id("pump") && binding.destination == null, "CD-LSH-16: the click gave $binding")
            helper.succeed()
        }
    }
}
