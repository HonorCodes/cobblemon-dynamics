package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.create.RealFlowAccess
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The water-wheel versions of engine and leash rows whose first part M1 tested on a furnace: who may claim a wheel, the move away
 * from a wheel that real water turns, and the Work Leash on wheels. Each dry wheel stands on the stone floor with no water nearby.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WheelRowTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val wheelRel = BlockPos(7, 2, 5)

    /** A pasture at (7,1,9), south of the wheels, with its owner beside it. Work Mode is on unless [on] is false. */
    private fun scene(helper: GameTestHelper, on: Boolean = true): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        if (on) TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, wheel: WaterWheelBlockEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, wheel.blockPos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /**
     * A wild Squirtle, a Squirtle that its owner sent out of the party, a fainted pastured Squirtle and a healthy pastured one stand about
     * 3 blocks from a dry water wheel for 400 ticks. Only the healthy pastured Squirtle gets a claim (CD-ASG-01).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg01OnlyAHealthyPasturedWaterTypeClaimsTheWheel(helper: GameTestHelper) {
        val scene = scene(helper, on = false)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val wild = TestKit.wild(helper, BlockPos(4, 2, 5), "squirtle level=10")
        val partyPokemon = TestKit.pokemon("squirtle level=10")
        Cobblemon.storage.getParty(scene.owner).add(partyPokemon)
        val party = partyPokemon.sendOut(helper.level, helper.absoluteVec(BlockPos(10, 2, 5).bottomCenter), null)
            ?: throw GameTestAssertException("the party Squirtle was not sent out")
        val fainted = TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")
        fainted.pokemon.currentHealth = 0
        val healthy = TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")
        TestKit.moveTo(helper, fainted, BlockPos(7, 2, 8))
        TestKit.moveTo(helper, healthy, BlockPos(4, 2, 8))
        TestKit.workMode(scene.pasture, true)
        helper.onEachTick {
            for ((name, other) in listOf("wild" to wild, "party" to party, "fainted" to fainted)) {
                helper.assertTrue(TestKit.claimOf(helper, other) == null, "CD-ASG-01: the $name Squirtle got a claim")
            }
        }
        helper.runAtTickTime(400) {
            val claim = TestKit.claimOf(helper, healthy)
            helper.assertTrue(claim?.key == TestKit.keyOf(helper, wheel.blockPos), "the healthy pastured Squirtle holds $claim")
            helper.succeed()
        }
    }

    /**
     * An unleashed Azumarill (Water and Fairy) stands 3 blocks from a dry water wheel for 400 ticks and never claims it (CD-ASG-04).
     * Then a Squirtle joins the pasture and claims the wheel, which shows that the wheel was free to take.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 700)
    fun cdAsg04AzumarillLeavesTheDryWheelAlone(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val azumarill = TestKit.tether(helper, scene.pasture, scene.owner, "azumarill level=20")
        TestKit.moveTo(helper, azumarill, BlockPos(7, 2, 8))
        var squirtle: PokemonEntity? = null
        helper.onEachTick {
            helper.assertTrue(
                TestKit.claimOf(helper, azumarill) == null,
                "CD-ASG-04: the Azumarill claimed ${TestKit.claimOf(helper, azumarill)}",
            )
            if (helper.tick < 400) return@onEachTick
            val control = squirtle ?: TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10").also { squirtle = it }
            if (TestKit.claimOf(helper, control)?.key == TestKit.keyOf(helper, wheel.blockPos)) helper.succeed()
        }
    }

    /**
     * A Squirtle works a dry wheel, then real water turns that wheel, and the Squirtle waits there in Standby for 400 ticks. A dry wheel
     * is then placed in the work area, and within 80 ticks the Squirtle's claim is on it (CD-ASG-19).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_400)
    fun cdAsg19WheelDriverMovesToADryWheel(helper: GameTestHelper) {
        val scene = scene(helper)
        val watered = CreateKit.smallWheel(helper, wheelRel)
        val walls = listOf(BlockPos(7, 3, 6), BlockPos(6, 3, 5), BlockPos(8, 3, 5), BlockPos(6, 2, 4), BlockPos(8, 2, 4), BlockPos(7, 2, 3))
        walls.forEach { helper.setBlock(it, Blocks.STONE) }
        val squirtle = TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")
        val dryRel = BlockPos(12, 2, 9)
        var step = "work"
        var since = -1L
        var dry: WaterWheelBlockEntity? = null
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, squirtle) ?: return@onEachTick
            when (step) {
                "work" -> if (isWorkingOn(helper, squirtle, watered)) {
                    helper.setBlock(BlockPos(7, 3, 5), Blocks.WATER)
                    step = "watered"
                }

                "watered" -> if (worker.status == WorkerStatus.STANDBY) {
                    val real = (watered as RealFlowAccess).`cobblemon_dynamics$realFlowScore`()
                    helper.assertTrue(real != 0, "the Squirtle waits, but Create sees no real flow")
                    since = helper.tick
                    step = "waiting"
                }

                "waiting" -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "the Squirtle left the watered wheel: ${worker.status}")
                    helper.assertTrue(
                        TestKit.claimOf(helper, squirtle)?.key == TestKit.keyOf(helper, watered.blockPos),
                        "the claim moved early",
                    )
                    if (helper.tick - since < 400) return@onEachTick
                    dry = CreateKit.smallWheel(helper, dryRel)
                    since = helper.tick
                    step = "moving"
                }

                "moving" -> {
                    val claim = TestKit.claimOf(helper, squirtle)
                    if (claim?.key == TestKit.keyOf(helper, dry!!.blockPos)) {
                        helper.succeed()
                    } else {
                        val waited = helper.tick - since
                        helper.assertTrue(waited < 80, "CD-ASG-19: $waited ticks after the dry wheel came the Squirtle holds $claim")
                    }
                }
            }
        }
    }

    /**
     * The owner selects a pastured Vaporeon with the Work Leash and clicks dry wheel A: 1 tick later the Vaporeon is bound to it
     * (CD-LSH-03), and the action bar read "Bound Vaporeon to Water Wheel" (CD-LSH-06). The owner then binds it to wheel B: wheel A is no
     * longer reserved and the binding names wheel B (CD-LSH-11), and the Vaporeon goes on to work wheel B.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels(helper: GameTestHelper) {
        val scene = scene(helper)
        val relA = BlockPos(4, 2, 5)
        val relB = BlockPos(10, 2, 5)
        val wheelA = CreateKit.smallWheel(helper, relA)
        val wheelB = CreateKit.smallWheel(helper, relB)
        val vaporeon = TestKit.tether(helper, scene.pasture, scene.owner, "vaporeon level=30")
        TestKit.leash(scene.owner)
        TestKit.clickEntity(scene.owner, vaporeon)
        TestKit.clickBlock(helper, scene.owner, relA)
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(reply == "Bound Vaporeon to Water Wheel", "CD-LSH-06: the bind answered '$reply'")
        var rebound = false
        helper.runAfterDelay(1) {
            val binding = BindingStore.read(vaporeon.pokemon)
            helper.assertTrue(binding?.target == wheelA.blockPos, "CD-LSH-03: 1 tick after the click the binding is $binding")
            helper.assertTrue(binding?.roleId == id("wheel_driver"), "CD-LSH-03: the binding's role is ${binding?.roleId}")
            val again = TestKit.bind(helper, scene.owner, vaporeon, relB)
            helper.assertTrue(again?.target == wheelB.blockPos, "CD-LSH-11: the second bind gave $again")
            rebound = true
        }
        helper.onEachTick {
            if (!rebound) return@onEachTick
            helper.assertTrue(TestKit.reservationAt(helper, wheelA.blockPos) == null, "CD-LSH-11: wheel A stays reserved after the rebind")
            val reserved = TestKit.reservationAt(helper, wheelB.blockPos)?.pokemonId == vaporeon.pokemon.uuid
            helper.assertTrue(reserved, "CD-LSH-11: wheel B is not reserved for the Vaporeon")
            helper.assertTrue(BindingStore.read(vaporeon.pokemon)?.target == wheelB.blockPos, "CD-LSH-11: the binding left wheel B")
            if (isWorkingOn(helper, vaporeon, wheelB)) helper.succeed()
        }
    }

    /**
     * A Surskit (Bug and Water) bound to a dry wheel evolves through Cobblemon into Masquerain (Bug and Flying). No role of the wheel
     * fits Masquerain, so 20 ticks later the binding and the reservation are gone (CD-LSH-14), and the binder's chat says why.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh14EvolutionWithoutWaterClearsTheWheelBinding(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        val surskit = TestKit.tether(helper, scene.pasture, scene.owner, "surskit level=22")
        val binding = TestKit.bind(helper, scene.owner, surskit, wheelRel)
        helper.assertTrue(binding?.target == wheel.blockPos, "the Surskit did not bind to the wheel: $binding")
        val pokemon = surskit.pokemon
        val evolution = pokemon.evolutions.firstOrNull { it.id == "surskit_masquerain" }
            ?: throw GameTestAssertException("Cobblemon's Surskit has no evolution into Masquerain")
        evolution.evolutionMethod(pokemon)
        val species = pokemon.species.resourceIdentifier
        helper.assertTrue(species == ResourceLocation.parse("cobblemon:masquerain"), "the Surskit evolved into $species")
        helper.runAfterDelay(20) {
            helper.assertTrue(BindingStore.read(pokemon) == null, "CD-LSH-14: 20 ticks later the binding is ${BindingStore.read(pokemon)}")
            helper.assertTrue(TestKit.reservationAt(helper, wheel.blockPos) == null, "CD-LSH-14: the wheel stays reserved")
            val message = "Masquerain lost its work binding: its new form cannot do the job"
            helper.assertTrue(message in TestKit.messages(scene.owner), "the binder got ${TestKit.messages(scene.owner)}")
            helper.succeed()
        }
    }
}
