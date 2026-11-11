package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.EngineEvents
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.pasture.WorkModeService
import com.cobblemon.mod.common.Cobblemon
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
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class AssignmentTests {
    private class Scene(
        val owner: ServerPlayer,
        val pasture: PokemonPastureBlockEntity,
        val furnaces: List<AbstractFurnaceBlockEntity>,
        val stokers: List<PokemonEntity>,
    )

    /** A Work Mode pasture at (7,1,7), and one Charmander for each furnace with 8 raw iron, north of the pasture. */
    private fun stokers(helper: GameTestHelper, count: Int): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnaces = listOf(BlockPos(3, 1, 3), BlockPos(11, 1, 3), BlockPos(7, 1, 3)).take(count).map {
            TestKit.furnace(helper, it, ItemStack(Items.RAW_IRON, 8))
        }
        return Scene(owner, pasture, furnaces, List(count) { TestKit.tether(helper, pasture, owner, "charmander level=10") })
    }

    private fun allWorking(helper: GameTestHelper, entities: List<PokemonEntity>) =
        entities.all { TestKit.workerOf(helper, it)?.status == WorkerStatus.WORKING }

    private fun claimedPositions(helper: GameTestHelper): Set<BlockPos> =
        TestKit.engine(helper).claims.all().mapNotNull { (it.key as? TargetKey.Block)?.let { key -> BlockPos.of(key.pos) } }.toSet()

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg01OnlyOwnedHealthyPastured(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val wild = TestKit.wild(helper, BlockPos(4, 1, 3), "charmander level=10")
        val partyPokemon = TestKit.pokemon("charmander level=10")
        Cobblemon.storage.getParty(owner).add(partyPokemon)
        val party = partyPokemon.sendOut(helper.level, helper.absoluteVec(BlockPos(10, 1, 3).bottomCenter), null)
            ?: throw GameTestAssertException("the party Charmander was not sent out")
        val fainted = TestKit.tether(helper, pasture, owner, "charmander level=10")
        fainted.pokemon.currentHealth = 0
        val healthy = TestKit.tether(helper, pasture, owner, "charmander level=10")
        // On the floor: a Pokémon moved into the floor (y = 1) falls under it, where no path reaches a work spot (CD-ASG-09).
        TestKit.moveTo(helper, fainted, BlockPos(7, 2, 6))
        TestKit.moveTo(helper, healthy, BlockPos(7, 2, 0))
        TestKit.workMode(pasture, true)
        helper.onEachTick {
            for ((name, other) in listOf("wild" to wild, "party" to party, "fainted" to fainted)) {
                helper.assertTrue(TestKit.claimOf(helper, other) == null, "the $name Charmander got a claim")
            }
        }
        helper.runAtTickTime(400) {
            helper.assertTrue(TestKit.claimOf(helper, healthy) != null, "the healthy pastured Charmander holds no claim")
            helper.succeed()
        }
    }

    /** The Charmander is held at one spot until it claims, so its distances to the furnaces stay 3 and 6 blocks. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdAsg03NearestFurnaceWins(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        val near = TestKit.furnace(helper, BlockPos(4, 1, 10), ItemStack(Items.RAW_IRON, 8))
        val far = TestKit.furnace(helper, BlockPos(13, 1, 10), ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val hold = BlockPos(7, 2, 10)
        TestKit.moveTo(helper, fire, hold)
        TestKit.workMode(pasture, true)
        helper.onEachTick {
            val claim = TestKit.claimOf(helper, fire)
            if (claim == null) {
                TestKit.moveTo(helper, fire, hold)
                return@onEachTick
            }
            helper.assertTrue(claim.key != TestKit.keyOf(helper, far.blockPos), "the worker claimed the 6-block furnace")
            helper.assertTrue(claim.key == TestKit.keyOf(helper, near.blockPos), "the worker claimed no furnace: ${claim.key}")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 400)
    fun cdAsg08HorizontalWorkAreaEdge(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val inside = TestKit.furnace(helper, BlockPos(19, 1, 11), ItemStack(Items.RAW_IRON, 8))
        val outside = TestKit.furnace(helper, BlockPos(2, 1, 11), ItemStack(Items.RAW_IRON, 8))
        repeat(2) { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        expectEdge(helper, inside, outside, "horizontal offset")
    }

    /**
     * Each furnace stands on a stone pillar, with a one-block ledge beside it. A claim needs a path to its spot (CD-ASG-09), and
     * Cobblemon's pathfinder does not step from the floor or a stair onto that ledge, so a stone staircase rises from the pasture's
     * side to the inside furnace. Its top steps are spots that a path reaches.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 400)
    fun cdAsg08VerticalWorkAreaEdge(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val inside = furnaceOnPillar(helper, BlockPos(11, 7, 5))
        // The column n blocks south of the ledge at (12, 6, 5) is n blocks lower, down to the floor.
        for (step in 1..4) for (y in 2..6 - step) helper.setBlock(BlockPos(12, y, 5 + step), Blocks.STONE)
        val outside = furnaceOnPillar(helper, BlockPos(11, 8, 17))
        repeat(2) { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        expectEdge(helper, inside, outside, "vertical offset")
    }

    private fun furnaceOnPillar(helper: GameTestHelper, rel: BlockPos): AbstractFurnaceBlockEntity {
        for (y in 1 until rel.y) helper.setBlock(BlockPos(rel.x, y, rel.z), Blocks.STONE)
        helper.setBlock(rel.east().below(), Blocks.STONE)
        return TestKit.furnace(helper, rel, ItemStack(Items.RAW_IRON, 8))
    }

    /** Over 300 ticks, [inside] gets claimed and [outside] never does. */
    private fun expectEdge(helper: GameTestHelper, inside: AbstractFurnaceBlockEntity, outside: AbstractFurnaceBlockEntity, what: String) {
        var insideClaimed = false
        helper.onEachTick {
            val claimed = claimedPositions(helper)
            helper.assertFalse(outside.blockPos in claimed, "a worker claimed the furnace beyond the work area ($what)")
            if (inside.blockPos in claimed) insideClaimed = true
        }
        helper.runAtTickTime(300) {
            helper.assertTrue(insideClaimed, "no worker claimed the furnace at the work area's edge ($what)")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg11WorkingWithinOneTickOfArrival(helper: GameTestHelper) {
        val fire = stokers(helper, 1).stokers.single()
        var arrivedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            val spot = worker.spot ?: return@onEachTick
            if (arrivedAt < 0 && WorkEngine.isArrived(fire, spot)) arrivedAt = helper.tick
            if (arrivedAt < 0) return@onEachTick
            if (worker.status == WorkerStatus.WORKING) {
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - arrivedAt < 1, "not Working 1 tick after reaching the spot: ${worker.status}")
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdAsg12StandbyKeepsClaimAndResumes(helper: GameTestHelper) {
        val scene = stokers(helper, 1)
        val fire = scene.stokers.single()
        val furnace = scene.furnaces.single()
        var claim: Claim? = null
        var emptiedAt = -1L
        var refilledAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            when {
                claim == null -> if (worker.status == WorkerStatus.WORKING) {
                    claim = TestKit.claimOf(helper, fire)
                    furnace.setItem(0, ItemStack.EMPTY)
                    emptiedAt = helper.tick
                }

                refilledAt < 0 -> {
                    helper.assertTrue(TestKit.claimOf(helper, fire) == claim, "the Stoker released its claim with no input")
                    if (helper.tick - emptiedAt == 5L) {
                        helper.assertTrue(worker.status == WorkerStatus.STANDBY, "not in Standby after the input ran out: ${worker.status}")
                    }
                    if (helper.tick - emptiedAt >= 60) {
                        furnace.setItem(0, ItemStack(Items.RAW_IRON, 8))
                        refilledAt = helper.tick
                    }
                }

                else -> {
                    helper.assertTrue(TestKit.claimOf(helper, fire) == claim, "the Stoker released its claim")
                    if (worker.status == WorkerStatus.WORKING) {
                        helper.succeed()
                    } else {
                        helper.assertTrue(helper.tick - refilledAt < 20, "not Working 20 ticks after the new input")
                    }
                }
            }
        }
    }

    /**
     * The Charmander is held 9 blocks from the furnace, beyond the Stoker's range, until it claims, so its work spot lies some
     * blocks away. Glass then walls the Charmander in, two blocks high, so it can never reach the spot.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdAsg13UnreachableTargetTimesOut(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val hold = BlockPos(7, 2, 12)
        TestKit.moveTo(helper, fire, hold)
        TestKit.workMode(pasture, true)
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
                helper.assertFalse(WorkEngine.isArrived(fire, spot), "the worker stands on its spot at claim time")
                TestKit.moveTo(helper, fire, hold)
                for (side in Direction.Plane.HORIZONTAL) {
                    helper.setBlock(hold.relative(side), Blocks.GLASS)
                    helper.setBlock(hold.relative(side).above(), Blocks.GLASS)
                }
                claim = first
                return@onEachTick
            }
            when (helper.level.gameTime - held.sinceTick) {
                599L -> helper.assertTrue(TestKit.claimOf(helper, fire) == held, "the claim is gone 599 ticks after it was made")

                601L -> {
                    helper.assertTrue(TestKit.claimOf(helper, fire) == null, "the claim is still held 601 ticks after it was made")
                    helper.succeed()
                }
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg15LeavingThePastureReleasesTheClaim(helper: GameTestHelper) {
        val scene = stokers(helper, 1)
        val fire = scene.stokers.single()
        val key = TestKit.keyOf(helper, scene.furnaces.single().blockPos)
        var leftAt = -1L
        helper.onEachTick {
            if (leftAt < 0) {
                if (TestKit.workerOf(helper, fire)?.status == WorkerStatus.WORKING) {
                    scene.pasture.releasePokemon(fire.pokemon.uuid)
                    leftAt = helper.tick
                }
            } else if (helper.tick - leftAt == 20L) {
                val claims = TestKit.engine(helper).claims
                val left = claims.claimOf(fire.pokemon.uuid) ?: claims.holderOf(key)
                helper.assertTrue(left == null, "the claim remains 20 ticks after the worker left: $left")
                helper.succeed()
            }
        }
    }

    /**
     * Two Working workers hold work claims, a Tired Machop sleeps on a Hay Bed with a bed claim, and a Clefairy on its way to heal the
     * Machop holds a heal claim (`EveryClaimScene`). The owner turns Work Mode off, and 1 tick later none of the four claims remains;
     * the leash binding stays (CD-WMD-05, CD-CLM-06).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdWmd05WorkModeOffReleasesEveryClaim(helper: GameTestHelper) {
        val scene = EveryClaimScene(helper)
        var offAt = -1L
        helper.runAtTickTime(999) { helper.fail("the scene never held every claim: ${scene.describe()}") }
        helper.onEachTick {
            val claims = TestKit.engine(helper).claims
            if (offAt < 0) {
                if (!scene.ready()) return@onEachTick
                WorkModeService.apply(helper.level, scene.pasture.blockPos, false)
                offAt = helper.tick
            } else if (helper.tick - offAt == 1L) {
                for ((entity, id) in scene.workers.zip(scene.ids)) {
                    val claim = claims.claimOf(id)
                    helper.assertTrue(
                        claim == null,
                        "CD-WMD-05: the ${entity.pokemon.species.name} holds $claim 1 tick after Work Mode off",
                    )
                }
                val left = claims.all().filter { it.pasture == scene.pastureKey }
                helper.assertTrue(left.isEmpty(), "CD-WMD-05: the pasture's claims $left remain 1 tick after Work Mode off")
                val binding = BindingStore.read(scene.vulpix.pokemon)
                helper.assertTrue(binding == scene.binding, "the Vulpix's binding is $binding after Work Mode off")
                helper.succeed()
            }
        }
    }

    /**
     * The `EveryClaimScene` holds two work claims, a bed claim and a heal claim, and the server restarts: before any scan the registry
     * is empty, and no claim from before the restart comes back (CD-CLM-07). The restart drops the whole engine, so this test runs
     * alone in its own batch.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = "restart_clm07")
    fun cdClm07ClaimsAreNotSaved(helper: GameTestHelper) {
        val scene = EveryClaimScene(helper)
        var restartedAt = -1L
        helper.runAtTickTime(999) { helper.fail("the scene never held every claim: ${scene.describe()}") }
        helper.onEachTick {
            val claims = TestKit.engine(helper).claims
            if (restartedAt < 0) {
                if (!scene.ready()) return@onEachTick
                val kinds = claims.all().map { it.kind }.sorted()
                helper.assertTrue(
                    kinds == listOf(ClaimKind.WORK, ClaimKind.WORK, ClaimKind.BED, ClaimKind.HEAL),
                    "the registry holds $kinds",
                )
                TestKit.simulateRestart(helper)
                helper.assertTrue(claims.all().isEmpty(), "CD-CLM-07: the registry holds ${claims.all()} after the restart")
                restartedAt = helper.level.gameTime
                return@onEachTick
            }
            // Only a new scan or poll may claim again; a claim from before the restart would mean that it was restored.
            helper.assertTrue(claims.all().all { it.sinceTick > restartedAt }, "CD-CLM-07: a claim from before the restart came back")
            if (helper.level.gameTime - restartedAt >= 20) helper.succeed()
        }
    }

    /** A chunk can hold parts of two test structures, so this test, which unloads one, runs alone in its own batch. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = "unload_clm08")
    fun cdClm08BrokenReplacedUnloaded(helper: GameTestHelper) {
        val scene = stokers(helper, 3)
        var before: List<Claim> = emptyList()
        var changedAt = -1L
        helper.onEachTick {
            val claims = TestKit.engine(helper).claims
            if (changedAt < 0) {
                if (!allWorking(helper, scene.stokers)) return@onEachTick
                before = scene.furnaces.map {
                    claims.holderOf(TestKit.keyOf(helper, it.blockPos)) ?: throw GameTestAssertException("${it.blockPos} has no claim")
                }
                val (broken, replaced, unloaded) = scene.furnaces
                helper.level.destroyBlock(broken.blockPos, false)
                helper.level.setBlockAndUpdate(replaced.blockPos, Blocks.STONE.defaultBlockState())
                EngineEvents.onChunkUnload(helper.level, ChunkPos(unloaded.blockPos))
                changedAt = helper.tick
            } else if (helper.tick - changedAt == 1L) {
                val left = claims.all().filter { it in before }
                helper.assertTrue(left.isEmpty(), "claims remain 1 tick later: $left")
                helper.succeed()
            }
        }
    }
}
