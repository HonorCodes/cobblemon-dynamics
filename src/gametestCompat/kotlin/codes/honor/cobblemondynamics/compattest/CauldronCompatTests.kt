package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.compat.irons_spellbooks.CauldronBrewing
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.lang.reflect.Field

/**
 * The Brewer on the real Alchemist Cauldron of Iron's Spells 'n Spellbooks 3.16.3 (CD-MCH-07). The cauldron boils while it holds any
 * fluid, counts the cook time of each of its 4 input slots up by 1 on each of its ticks, and melts the input on the tick that takes the
 * count past 100. An icy fang in at least 250 mB of water melts into 250 mB of ice venom, the jar's only brew on plain water.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CauldronCompatTests {
    private val cauldronRel = BlockPos(7, 2, 5)

    /**
     * CD-MCH-07: a Grimer at a cauldron with one input. The Grimer is bound with the Work Leash, so it walks to the cauldron and waits
     * there in Standby before any cook runs: the role has demand only while a cook time runs, and a waiting worker checks for demand
     * every 20 ticks of Standby (CD-ASG-12). The icy fang goes in 2 ticks before such a check, so the Brewer works from the cook's
     * second tick. The fang must melt within 55 ticks of going in, where the cauldron alone takes 101; no cook time may pass 100, and
     * each tick after a worked tick advances the cook by its own step and one extra step, unless the extra step would pass the end.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = TIMEOUT_TICKS)
    fun cdMch07GrimerMeltsOneInputWithin55Ticks(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.ALCHEMIST_CAULDRON), "the Alchemist Cauldron mixin did not apply")
        helper.assertTrue(RoleRegistry.current.get(ROLE) != null, "the role $ROLE is not active")
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val cauldron = cauldron(helper)
        val grimer = TestKit.tether(helper, pasture, owner, "grimer level=10")
        val binding = TestKit.bind(helper, owner, grimer, cauldronRel)
        helper.assertTrue(
            binding?.roleId == ROLE,
            "the leash bound the Grimer as ${binding?.roleId}, not $ROLE; the reply was '${TestKit.lastMessage(owner)}'",
        )
        val fang = ItemStack(item(ICY_FANG))
        var insertedAt = -1L
        var last = cooktimes(cauldron)
        var workedLastTick = false
        helper.runAtTickTime(TIMEOUT_TICKS - 10L) {
            val worker = TestKit.workerOf(helper, grimer)
            helper.fail(
                "the Grimer is ${worker?.status} at ${TestKit.relative(helper, grimer.blockPosition())}, spot " +
                    "${worker?.spot?.let { TestKit.relative(helper, it) }}; the fang went in at tick $insertedAt; the input slot holds " +
                    "${cauldron.getItem(0)}; the cook times read ${cooktimes(cauldron).toList()}",
            )
        }
        helper.onEachTick {
            val times = cooktimes(cauldron)
            for (time in times) helper.assertTrue(time in 0..END, "CD-MCH-07: a cook time reads $time, past its end")
            val worker = TestKit.workerOf(helper, grimer) ?: return@onEachTick
            if (insertedAt < 0) {
                helper.assertTrue(worker.status != WorkerStatus.WORKING, "the Grimer works an empty cauldron")
                if (worker.status == WorkerStatus.STANDBY && !worker.missedArrival && isOnCauldron(helper, grimer, cauldron)) {
                    // The engine checks in the tick whose game time is statusSince plus a multiple of 20.
                    if ((helper.level.gameTime + 2 - worker.statusSince) % STANDBY_CHECK_TICKS == 0L) {
                        cauldron.setItem(0, fang.copy())
                        insertedAt = helper.tick
                    }
                }
                last = times
                return@onEachTick
            }
            if (cauldron.getItem(0).isEmpty) {
                val took = helper.tick - insertedAt
                helper.assertTrue(took <= 55, "CD-MCH-07: the icy fang took $took ticks to melt, more than 55")
                helper.assertTrue(venom(cauldron) == 250, "the melt left ${venom(cauldron)} mB of ice venom, not 250")
                helper.succeed()
                return@onEachTick
            }
            val step = times[0] - last[0]
            if (workedLastTick) {
                helper.assertTrue(
                    step == 2 || (times[0] == END && step == 1),
                    "CD-MCH-07: a worked tick took the cook from ${last[0]} to ${times[0]}, not one extra step",
                )
            } else {
                helper.assertTrue(step == 1, "the cook went from ${last[0]} to ${times[0]} on a tick that no Brewer worked")
            }
            last = times
            workedLastTick = worker.status == WorkerStatus.WORKING
        }
    }

    /** A cauldron with 1,000 mB of water, filled through its fluid handler, as a pipe fills it. */
    private fun cauldron(helper: GameTestHelper): AlchemistCauldronTile {
        helper.setBlock(cauldronRel, block(CAULDRON))
        val cauldron = helper.getBlockEntity<AlchemistCauldronTile>(cauldronRel)
        helper.assertTrue((cauldron as Any) is CauldronBrewing, "the cauldron does not take the Brewer's grants")
        val fluids = helper.level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(cauldronRel), Direction.UP)
            ?: throw GameTestAssertException("the cauldron has no fluid handler")
        val filled = fluids.fill(FluidStack(Fluids.WATER, WATER_MB), IFluidHandler.FluidAction.EXECUTE)
        helper.assertTrue(filled == WATER_MB, "the cauldron took $filled mB of water")
        return cauldron
    }

    private fun isOnCauldron(helper: GameTestHelper, entity: PokemonEntity, cauldron: AlchemistCauldronTile): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK && claim.key == TestKit.keyOf(helper, cauldron.blockPos)
    }

    /** The ice venom in the cauldron, in mB. */
    private fun venom(cauldron: AlchemistCauldronTile): Int {
        val fluids: IFluidHandler = cauldron.fluidInventory
        return (0 until fluids.tanks).map { fluids.getFluidInTank(it) }
            .filter { BuiltInRegistries.FLUID.getKey(it.fluid) == ICE_VENOM }
            .sumOf { it.amount }
    }

    /** The cauldron's private cook times, one for each input slot, as a copy. */
    private fun cooktimes(cauldron: AlchemistCauldronTile): IntArray = (COOKTIMES.get(cauldron) as IntArray).copyOf()

    private fun block(id: ResourceLocation) =
        BuiltInRegistries.BLOCK.getOptional(id).orElseThrow { GameTestAssertException("no block $id is registered") }

    private fun item(id: ResourceLocation) =
        BuiltInRegistries.ITEM.getOptional(id).orElseThrow { GameTestAssertException("no item $id is registered") }

    private companion object {
        const val TIMEOUT_TICKS = 700
        const val WATER_MB = 1_000

        /** The last cook time that the cauldron shows between its ticks: its next step takes the count past it and melts the input. */
        const val END = 100

        /** CD-ASG-12: a worker in Standby checks for demand every 20 ticks. */
        const val STANDBY_CHECK_TICKS = 20L

        val ROLE: ResourceLocation = id("brewer_irons_spellbooks")
        val CAULDRON: ResourceLocation = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "alchemist_cauldron")
        val ICY_FANG: ResourceLocation = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "icy_fang")
        val ICE_VENOM: ResourceLocation = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice_venom")

        val COOKTIMES: Field = AlchemistCauldronTile::class.java.getDeclaredField("cooktimes").apply { isAccessible = true }
    }
}
