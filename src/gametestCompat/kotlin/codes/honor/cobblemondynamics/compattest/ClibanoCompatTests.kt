package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.compat.forbidden_arcanus.ClibanoAccess
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoFireType
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity
import com.stal111.forbidden_arcanus.common.inventory.clibano.ClibanoMenu
import com.stal111.forbidden_arcanus.common.item.mundabitur.MundabiturInteractions
import com.stal111.forbidden_arcanus.common.item.mundabitur.TransformPatternInteraction
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.lang.reflect.Field

/**
 * The Stoker on a real Clibano of Forbidden & Arcanus 2.6.1 (CD-FUEL-10), the furnace that a 3 × 3 × 3 cube of polished darkstone
 * becomes when Mundabitur Dust touches the Clibano Core in one of its faces. Its main block entity sits in the middle of the cube. It
 * smelts only its own `clibano_combustion` recipes, which turn iron ore into an iron ingot in 100 ticks of normal fire, and it takes a
 * fuel item only on a tick that starts with no burn time left.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ClibanoCompatTests {
    /** The middle of the cube, on the floor at y = 2 to 4, where the main block entity stands. */
    private val centerRel = BlockPos(7, 3, 5)

    /** The face of the cube toward the pasture, which holds the Clibano Core before the cube forms. */
    private val front = Direction.SOUTH

    /**
     * CD-FUEL-10: a Charmander at a Clibano with iron ore and no fuel. From the Charmander's first worked tick, each tick of the Clibano
     * starts with a burn of at least 2 and an equal burn duration, so it leaves a burn time of 1 and a duration of 2, its fire stays
     * normal fire, and it smelts the ore within 200 ticks of the start of the work. A coal goes into the fuel slot after the first lit
     * tick, and the Clibano never takes it.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = TIMEOUT_TICKS)
    fun cdFuel10CharmanderSmeltsIronOreInAClibano(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.CLIBANO), "the Clibano mixin did not apply")
        helper.assertTrue(RoleRegistry.current.get(ROLE) != null, "the role $ROLE is not active")
        val clibano = clibano(helper)
        clibano.setStack(INPUT_SLOT, ItemStack(Items.IRON_ORE))
        val owner = TestKit.owner(helper)
        // Cobblemon tethers a Pokémon south of its pasture, and one too near the template's edge climbs onto its barrier ceiling. The
        // pasture also stays off the straight line between that place and the cube.
        val pasture = TestKit.placePasture(helper, BlockPos(10, 1, 9), owner)
        TestKit.workMode(pasture, true)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        var workingSince = -1L
        var coalIn = false
        helper.runAtTickTime(TIMEOUT_TICKS - 10L) {
            helper.fail(
                "${CompatDiag.describe(helper, charmander)}; working since tick $workingSince; the Clibano holds " +
                    "${clibano.getStack(INPUT_SLOT)} in, ${results(clibano)} out, burn ${int(BURN_TIME, clibano)}, smelts " +
                    "${(clibano as? ClibanoAccess)?.`cobblemon_dynamics$smelts`()}",
            )
        }
        helper.onEachTick {
            val fire = FIRE_TYPE.get(clibano)
            helper.assertTrue(fire == ClibanoFireType.FIRE, "CD-FUEL-10: the Clibano burns $fire")
            if (coalIn) {
                val fuel = clibano.getStack(ClibanoMenu.FUEL_SLOT)
                helper.assertTrue(fuel.`is`(Items.COAL) && fuel.count == 1, "CD-FUEL-10: the Clibano used its fuel, which reads $fuel")
            }
            if (results(clibano).any { it.`is`(Items.IRON_INGOT) }) {
                helper.assertTrue(workingSince >= 0, "the Clibano smelted with no Stoker")
                val took = helper.tick - workingSince
                helper.assertTrue(took <= 200, "CD-FUEL-10: the Clibano smelted the ore $took ticks after the work began")
                helper.assertTrue(coalIn, "the Clibano smelted before the coal went in")
                helper.succeed()
                return@onEachTick
            }
            val worked = workedTicks(helper, charmander)
            if (workingSince < 0) {
                if (worked == null) return@onEachTick
                workingSince = helper.tick
            }
            helper.assertTrue(helper.tick - workingSince <= 200, "CD-FUEL-10: the Clibano did not smelt within 200 ticks of work")
            // The Clibano's tick reads the grant of the engine's previous tick, so its first lit tick is the second worked tick.
            if (worked == null || worked < 1) return@onEachTick
            val burn = int(BURN_TIME, clibano)
            val duration = int(BURN_DURATION, clibano)
            helper.assertTrue(
                burn == HEAT_TICKS - 1 && duration == HEAT_TICKS,
                "CD-FUEL-10: a worked tick left burn time $burn and duration $duration, not ${HEAT_TICKS - 1} and $HEAT_TICKS",
            )
            if (!coalIn) {
                clibano.setStack(ClibanoMenu.FUEL_SLOT, ItemStack(Items.COAL))
                coalIn = true
            }
        }
    }

    /**
     * A formed Clibano: the cube of the pattern `CLIBANO_COMBUSTION_BASE`, polished darkstone at the corners and polished darkstone
     * bricks on the edges and the faces, with air in the middle and the Clibano Core in the front face, which the interaction of
     * Mundabitur Dust then turns into the Clibano, as a player's click with the dust does.
     */
    private fun clibano(helper: GameTestHelper): ClibanoMainBlockEntity {
        val corner = block("polished_darkstone")
        val brick = block("polished_darkstone_bricks")
        for (x in -1..1) {
            for (y in -1..1) {
                for (z in -1..1) {
                    val middles = listOf(x, y, z).count { it == 0 }
                    val block = when (middles) {
                        3 -> Blocks.AIR
                        0 -> corner
                        else -> brick
                    }
                    helper.setBlock(centerRel.offset(x, y, z), block)
                }
            }
        }
        val coreRel = centerRel.relative(front)
        helper.setBlock(coreRel, block("clibano_core"))
        val context = TransformPatternInteraction.TransformPatternContext.of(
            helper.level,
            helper.absolutePos(coreRel),
            InteractionHand.MAIN_HAND,
            front,
        )
        val form = MundabiturInteractions.CREATE_CLIBANO.get()
        helper.assertTrue(form.canInteract(context), "the Clibano pattern did not match")
        form.interact(context)
        return helper.level.getBlockEntity(helper.absolutePos(centerRel)) as? ClibanoMainBlockEntity
            ?: throw GameTestAssertException("no Clibano main block entity in the middle of the formed cube")
    }

    /** How many ticks [entity] has worked the Clibano for, or null while it does not. */
    private fun workedTicks(helper: GameTestHelper, entity: PokemonEntity): Long? {
        val claim = TestKit.claimOf(helper, entity)
        val worker = TestKit.workerOf(helper, entity)
        if (claim?.kind != ClaimKind.WORK || claim.key != TestKit.keyOf(helper, helper.absolutePos(centerRel))) return null
        if (worker?.status != WorkerStatus.WORKING) return null
        return helper.level.gameTime - worker.statusSince
    }

    private fun results(clibano: ClibanoMainBlockEntity): List<ItemStack> = RESULT_SLOTS.map { clibano.getStack(it) }

    private fun int(field: Field, clibano: ClibanoMainBlockEntity): Int = field.getInt(clibano)

    private fun block(path: String): Block {
        val id = ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path)
        return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow { GameTestAssertException("no block $id is registered") }
    }

    private companion object {
        const val TIMEOUT_TICKS = 800

        /** CD-FUEL-10: the burn that the mixin keeps at the start of each worked tick of the Clibano (`ClibanoRule.HEAT_TICKS`). */
        const val HEAT_TICKS = 2

        val ROLE: ResourceLocation = id("stoker_forbidden_arcanus")
        val INPUT_SLOT: Int = ClibanoMenu.INPUT_SLOTS.first
        val RESULT_SLOTS: List<Int> = listOf(ClibanoMenu.RESULT_SLOTS.first, ClibanoMenu.RESULT_SLOTS.second)

        val BURN_TIME: Field = field("burnTime")
        val BURN_DURATION: Field = field("burnDuration")
        val FIRE_TYPE: Field = field("fireType")

        private fun field(name: String): Field = ClibanoMainBlockEntity::class.java.getDeclaredField(name).apply { isAccessible = true }
    }
}
