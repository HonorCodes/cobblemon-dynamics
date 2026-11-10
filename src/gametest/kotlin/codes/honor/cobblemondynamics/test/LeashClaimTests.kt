package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.EngineEvents
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** How leash bindings meet the claim registry and the engine: reservations, takeovers, waits, unloads and missed arrivals. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class LeashClaimTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    /** A Work Mode pasture at [rel] with its owner beside it. */
    private fun pasture(helper: GameTestHelper, rel: BlockPos = BlockPos(7, 1, 7)): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, rel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    private fun isWorkingAt(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /**
     * Charmander A holds an auto work claim on a furnace, and the owner binds Charmander B to it. A's claim ends in the tick of the bind,
     * A never holds the furnace again, and B holds a work claim on it 20 ticks later (CD-CLM-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdClm05BindTakesTheTargetFromAnAutoWorker(helper: GameTestHelper) {
        val scene = pasture(helper)
        val furnaceRel = BlockPos(7, 1, 3)
        val furnace = TestKit.keyOf(helper, TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 8)).blockPos)
        val auto = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var bound: PokemonEntity? = null
        var boundAt = -1L
        helper.onEachTick {
            val leashed = bound
            if (leashed == null) {
                if (TestKit.claimOf(helper, auto)?.key != furnace) return@onEachTick
                val other = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
                helper.assertTrue(TestKit.bind(helper, scene.owner, other, furnaceRel) != null, "Charmander B did not bind")
                helper.assertTrue(TestKit.claimOf(helper, auto) == null, "CD-CLM-05: Charmander A keeps its claim in the tick of the bind")
                bound = other
                boundAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - boundAt
            helper.assertTrue(TestKit.claimOf(helper, auto)?.key != furnace, "CD-CLM-05: Charmander A holds the furnace $age ticks later")
            if (age < 20) return@onEachTick
            val claim = TestKit.claimOf(helper, leashed)
            helper.assertTrue(claim?.kind == ClaimKind.WORK && claim.key == furnace, "CD-CLM-05: Charmander B holds $claim 20 ticks later")
            helper.succeed()
        }
    }

    /**
     * A leashed Charmander works its furnace until the test tires it, and then sleeps on a Hay Bed, while an Idle Charmander of its
     * pasture stands 3 blocks from the furnace for 400 ticks. Then its pasture's Work Mode turns off, and an Idle Charmander of a second
     * pasture stands 3 blocks from the furnace for 400 ticks. Neither claims the furnace, which stays reserved for the leashed Charmander
     * (CD-CLM-06).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_800)
    fun cdClm06ReservedTargetWaitsForItsWorker(helper: GameTestHelper) {
        val home = pasture(helper, BlockPos(6, 1, 11))
        val owner = home.owner
        val second = TestKit.placePasture(helper, BlockPos(16, 1, 11), owner)
        val furnaceRel = BlockPos(11, 1, 11)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64)).blockPos
        val key = TestKit.keyOf(helper, furnace)
        helper.setBlock(BlockPos(6, 2, 16), DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val sleeper = TestKit.tether(helper, home.pasture, owner, "charmander level=10")
        val binding = TestKit.bind(helper, owner, sleeper, furnaceRel) ?: throw GameTestAssertException("the leash did not bind")
        val idle = TestKit.tether(helper, home.pasture, owner, "charmander level=10")
        val stranger = TestKit.tether(helper, second, owner, "charmander level=10")
        val engine = TestKit.engine(helper)
        var step = "work"
        var since = -1L
        helper.onEachTick {
            val now = BindingStore.read(sleeper.pokemon)
            helper.assertTrue(now == binding, "the binding is $now at '$step'")
            val reserved = TestKit.reservationAt(helper, furnace)?.pokemonId == sleeper.pokemon.uuid
            helper.assertTrue(reserved, "CD-CLM-06: the furnace is not reserved for the leashed Charmander at '$step'")
            val holder = engine.claims.holderOf(key)
            val free = holder == null || holder.worker == sleeper.pokemon.uuid
            helper.assertTrue(free, "CD-CLM-06: $holder took the reserved furnace at '$step'")
            when (step) {
                "work" -> {
                    val worker = engine.worker(sleeper.pokemon.uuid) ?: return@onEachTick
                    if (worker.status != WorkerStatus.WORKING) return@onEachTick
                    worker.stamina.fraction = 0.0
                    worker.tired = true
                    step = "tired"
                }

                "tired" -> if (engine.worker(sleeper.pokemon.uuid)?.status == WorkerStatus.SLEEPING) {
                    step = "asleep"
                    since = helper.tick
                }

                "asleep" -> {
                    TestKit.moveTo(helper, idle, BlockPos(8, 2, 11))
                    val sleeping = engine.worker(sleeper.pokemon.uuid)?.status
                    helper.assertTrue(sleeping == WorkerStatus.SLEEPING, "the leashed Charmander is $sleeping, not asleep")
                    val status = engine.worker(idle.pokemon.uuid)?.status
                    helper.assertTrue(status == WorkerStatus.IDLE, "the Charmander beside the furnace is $status, not Idle")
                    if (helper.tick - since < 400) return@onEachTick
                    TestKit.workMode(home.pasture, false)
                    TestKit.workMode(second, true)
                    step = "off"
                    since = helper.tick
                }

                "off" -> {
                    TestKit.moveTo(helper, stranger, BlockPos(14, 2, 11))
                    helper.assertTrue(engine.worker(sleeper.pokemon.uuid) == null, "the leashed Charmander still works with Work Mode off")
                    if (helper.tick - since < 2) return@onEachTick
                    val status = engine.worker(stranger.pokemon.uuid)?.status
                    helper.assertTrue(status == WorkerStatus.IDLE, "the second pasture's Charmander is $status, not an Idle worker")
                    if (helper.tick - since >= 400) helper.succeed()
                }
            }
        }
    }

    /**
     * A leashed Stoker waits in Standby at its empty furnace while an auto Stoker of the same pasture works a third furnace, so the
     * pasture's scans look for furnaces. Then the furnace 3 blocks from the empty one gets input. For 400 ticks the leashed Stoker stays
     * on its work spot and keeps its claim and its binding, and the furnace with input stays unclaimed (CD-ASG-20), although an auto
     * worker in Standby moves to such a furnace after `standbyMoveTicks` (200 ticks).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdAsg20LeashedWorkerStaysOnItsBoundTarget(helper: GameTestHelper) {
        val scene = pasture(helper)
        val emptyRel = BlockPos(5, 1, 3)
        val empty = TestKit.keyOf(helper, TestKit.furnace(helper, emptyRel, null).blockPos)
        val nearby = TestKit.furnace(helper, BlockPos(8, 1, 3), null)
        val loaded = TestKit.keyOf(helper, nearby.blockPos)
        val worked = TestKit.keyOf(helper, TestKit.furnace(helper, BlockPos(7, 1, 11), ItemStack(Items.RAW_IRON, 64)).blockPos)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val binding = TestKit.bind(helper, scene.owner, fire, emptyRel) ?: throw GameTestAssertException("the leash did not bind")
        val auto = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val engine = TestKit.engine(helper)
        var since = -1L
        var spot: Vec3? = null
        helper.onEachTick {
            helper.assertTrue(BindingStore.read(fire.pokemon) == binding, "CD-ASG-20: the binding is ${BindingStore.read(fire.pokemon)}")
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            val claim = TestKit.claimOf(helper, fire)
            val autoWorks = TestKit.claimOf(helper, auto)?.key == worked && TestKit.workerOf(helper, auto)?.status == WorkerStatus.WORKING
            val held = spot
            if (held == null) {
                if (worker.status != WorkerStatus.STANDBY || !autoWorks) return@onEachTick
                helper.assertTrue(claim?.key == empty, "the leashed Stoker waits at ${claim?.key}, not its furnace")
                nearby.setItem(0, ItemStack(Items.RAW_IRON, 8))
                since = helper.tick
                spot = Vec3.atBottomCenterOf(worker.spot ?: throw GameTestAssertException("a worker in Standby with no spot"))
                return@onEachTick
            }
            helper.assertTrue(engine.claims.holderOf(loaded) == null, "CD-ASG-20: the furnace with input got a claim")
            helper.assertTrue(autoWorks, "the auto Stoker stopped working its furnace, so the scans may have stopped")
            val waited = helper.tick - since
            val stays = worker.status == WorkerStatus.STANDBY && claim?.key == empty
            helper.assertTrue(stays, "CD-ASG-20: the leashed Stoker is ${worker.status} at ${claim?.key} after $waited ticks of Standby")
            val off = fire.position().distanceTo(held)
            helper.assertTrue(off <= 0.5, "CD-ASG-20: the leashed Stoker moved $off blocks from its spot after $waited ticks of Standby")
            if (waited >= 400) helper.succeed()
        }
    }

    /**
     * The bound furnace's chunk unloads, as `ChunkEvent.Unload` runs it, and stays unloaded for 100 ticks: the unload runs on each of
     * those ticks, so no claim lives in the chunk. The binding and the reservation stay on every tick, and the Charmander Works the
     * furnace again within 600 ticks of the reload (CD-LSH-09). The unload also ends other tests' claims in that chunk, so this test runs
     * alone.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_400, batch = "unload_lsh09")
    fun cdLsh09UnloadKeepsTheBinding(helper: GameTestHelper) {
        val scene = pasture(helper)
        val furnaceRel = BlockPos(7, 1, 3)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64)).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val binding = TestKit.bind(helper, scene.owner, fire, furnaceRel) ?: throw GameTestAssertException("the leash did not bind")
        var unloadedAt = -1L
        helper.onEachTick {
            helper.assertTrue(BindingStore.read(fire.pokemon) == binding, "CD-LSH-09: the binding is ${BindingStore.read(fire.pokemon)}")
            val reserved = TestKit.reservationAt(helper, furnace)?.pokemonId == fire.pokemon.uuid
            helper.assertTrue(reserved, "CD-LSH-09: the furnace lost its reservation")
            if (unloadedAt < 0) {
                if (isWorkingAt(helper, fire, furnace)) unloadedAt = helper.tick
                if (unloadedAt < 0) return@onEachTick
            }
            val age = helper.tick - unloadedAt
            if (age < 100) {
                EngineEvents.onChunkUnload(helper.level, ChunkPos(furnace))
                helper.assertTrue(TestKit.claimOf(helper, fire) == null, "a claim lives in the unloaded chunk")
                return@onEachTick
            }
            if (isWorkingAt(helper, fire, furnace)) {
                helper.succeed()
            } else {
                helper.assertTrue(age - 100 < 600, "CD-LSH-09: the Charmander is not Working 600 ticks after the reload")
            }
        }
    }

    /**
     * A bound Charmander is held 9 blocks from its furnace until it claims, so its work spot lies blocks away, and then glass walls it in.
     * Its arrival time runs out, and its owner's action bar reads "Charmander cannot reach Furnace" within 601 ticks of the claim
     * (CD-LSH-21).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdLsh21CannotReachMessage(helper: GameTestHelper) {
        val scene = pasture(helper)
        val furnaceRel = BlockPos(7, 1, 3)
        TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val hold = BlockPos(7, 2, 12)
        TestKit.moveTo(helper, fire, hold)
        helper.assertTrue(TestKit.bind(helper, scene.owner, fire, furnaceRel) != null, "the leash did not bind")
        val expected = "Charmander cannot reach Furnace"
        var claim: Claim? = null
        helper.onEachTick {
            val held = claim
            if (held == null) {
                val first = TestKit.claimOf(helper, fire)
                if (first == null) {
                    TestKit.moveTo(helper, fire, hold)
                    return@onEachTick
                }
                val spot = TestKit.workerOf(helper, fire)?.spot ?: throw GameTestAssertException("a claim with no work spot")
                helper.assertFalse(WorkEngine.isArrived(fire, spot), "the Charmander stands on its spot at claim time")
                TestKit.moveTo(helper, fire, hold)
                for (side in Direction.Plane.HORIZONTAL) {
                    helper.setBlock(hold.relative(side), Blocks.GLASS)
                    helper.setBlock(hold.relative(side).above(), Blocks.GLASS)
                }
                claim = first
                return@onEachTick
            }
            val age = helper.level.gameTime - held.sinceTick
            if (expected in TestKit.messages(scene.owner)) {
                helper.assertTrue(age <= 601, "CD-LSH-21: the message came $age ticks after the claim")
                helper.succeed()
            } else {
                helper.assertTrue(age < 601, "CD-LSH-21: no '$expected' $age ticks after the claim: ${TestKit.messages(scene.owner)}")
            }
        }
    }
}
