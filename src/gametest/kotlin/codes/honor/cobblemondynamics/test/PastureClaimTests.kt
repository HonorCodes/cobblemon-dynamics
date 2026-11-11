package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.stamina.StaminaMath
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The pasture check of the land claim gate (CD-LND-01 to CD-LND-05, CD-LND-11), here through the test mod's claim provider,
 * [TestLandClaims], which guards the absolute positions that a test adds. Each pasture belongs to a [TestKit.owner], whom
 * `PlayerList.getPlayer` does not return, so every check asks about an offline placer.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class PastureClaimTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    /** A Work Mode pasture at (7,1,7), and its owner beside it, in reach of the leash clicks. */
    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** The provider refuses the absolute position [pos] to every placer from now until the test ends. */
    private fun refuse(helper: GameTestHelper, pos: BlockPos, set: MutableSet<BlockPos> = TestLandClaims.refused): BlockPos {
        val absolute = pos.immutable()
        set += absolute
        TestKit.afterTest(helper) { set -= absolute }
        return absolute
    }

    private fun isWorkingAt(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /** Leaves the worker of [entity] one tick of Work Stamina, so that it gets Tired on its next Working tick, as RestTests does. */
    private fun tireOut(helper: GameTestHelper, entity: PokemonEntity) {
        val worker = TestKit.workerOf(helper, entity) ?: throw GameTestAssertException("the ${entity.pokemon.species.name} is no worker")
        worker.stamina.fraction = 1.0 / StaminaMath.capacityTicks(entity.pokemon.level, WorkEngineSettings.current)
    }

    /**
     * Free cells on solid ground [near] and [far] blocks from the work spot [spot], in opposite directions, inside the 15 × 15 floor.
     * Returns them relative to the test.
     */
    private fun bedCells(helper: GameTestHelper, spot: BlockPos, near: Int, far: Int): Pair<BlockPos, BlockPos?> {
        val rel = TestKit.relative(helper, spot)
        for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val a = rel.offset(near * dx, 0, near * dz)
            val b = rel.offset(-far * dx, 0, -far * dz)
            val cells = if (far > 0) listOf(a, b) else listOf(a)
            val free = cells.all {
                it.x in 1..13 && it.z in 1..13 && helper.getBlockState(it).isAir && !helper.getBlockState(it.below()).isAir
            }
            if (free) return a to b.takeIf { far > 0 }
        }
        throw GameTestAssertException("no room for beds $near and $far blocks from the work spot $rel")
    }

    private fun placeBed(helper: GameTestHelper, rel: BlockPos): BlockPos {
        helper.setBlock(rel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        return helper.absolutePos(rel)
    }

    /**
     * Furnaces A and B hold 64 raw iron each, and the provider refuses B to the placer. For 400 ticks no worker claims B, and a worker
     * claims A. Then the provider allows B, and a worker claims B within 400 ticks (CD-LND-01).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = BATCH)
    fun cdLnd01RefusedFurnaceGetsNoWorkerUntilAllowed(helper: GameTestHelper) {
        val scene = scene(helper)
        val a = TestKit.furnace(helper, BlockPos(4, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val b = refuse(helper, TestKit.furnace(helper, BlockPos(10, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos)
        val workers = List(2) { TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10") }
        var allowedAt = -1L
        helper.onEachTick {
            val keys = workers.mapNotNull { TestKit.claimOf(helper, it)?.key }
            if (allowedAt < 0) {
                helper.assertFalse(TestKit.keyOf(helper, b) in keys, "CD-LND-01: a worker claimed furnace B, which the placer may not use")
                if (helper.tick < 400) return@onEachTick
                helper.assertTrue(TestKit.keyOf(helper, a) in keys, "CD-LND-01: furnace A has no worker after 400 ticks: $keys")
                TestLandClaims.refused -= b
                allowedAt = helper.tick
            } else if (TestKit.keyOf(helper, b) in keys) {
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - allowedAt < 400, "CD-LND-01: no worker claimed furnace B within 400 ticks of the change")
            }
        }
    }

    /**
     * A Charmander Works furnace A, and then the provider refuses A. 21 ticks later the worker holds no claim, and for 200 more ticks it
     * does not claim A again (CD-LND-02).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = BATCH)
    fun cdLnd02RefusalReleasesAWorkClaimWithin20Ticks(helper: GameTestHelper) {
        val scene = scene(helper)
        val a = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var refusedAt = -1L
        helper.onEachTick {
            if (refusedAt < 0) {
                if (!isWorkingAt(helper, fire, a)) return@onEachTick
                refuse(helper, a)
                refusedAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - refusedAt
            val claim = TestKit.claimOf(helper, fire)
            if (age >= 21) helper.assertTrue(claim == null, "CD-LND-02: the worker holds $claim $age ticks after the refusal")
            if (age >= 221) helper.succeed()
        }
    }

    /**
     * A Tired Charmander sleeps on bed A, and then the provider refuses bed A. 21 ticks later no bed claim remains, and the worker
     * rests in place (CD-LND-02).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_600, batch = BATCH)
    fun cdLnd02RefusalReleasesABedClaimWithin20Ticks(helper: GameTestHelper) {
        val scene = scene(helper)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var bed: BlockPos? = null
        var refusedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            val placed = bed
            if (placed == null) {
                if (!isWorkingAt(helper, fire, furnace)) return@onEachTick
                val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
                bed = placeBed(helper, bedCells(helper, spot, near = 3, far = 0).first)
                tireOut(helper, fire)
                return@onEachTick
            }
            val claim = TestKit.claimOf(helper, fire)
            if (refusedAt < 0) {
                if (worker.status != WorkerStatus.SLEEPING) return@onEachTick
                helper.assertTrue(claim?.kind == ClaimKind.BED && claim.key == TestKit.keyOf(helper, placed), "the sleeper holds $claim")
                refuse(helper, placed)
                refusedAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - refusedAt
            if (age < 21) return@onEachTick
            helper.assertFalse(claim?.kind == ClaimKind.BED, "CD-LND-02: the bed claim $claim remains $age ticks after the refusal")
            helper.assertTrue(worker.status == WorkerStatus.RESTING_IN_PLACE, "CD-LND-02: the worker is ${worker.status}")
            helper.succeed()
        }
    }

    /**
     * A Charmander bound to furnace A Works it, and then the provider refuses A. 21 ticks later the binding is gone, the worker holds no
     * claim, and the binder's last message names the reason (CD-LND-03).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = BATCH)
    fun cdLnd03RefusedBoundTargetClearsTheBinding(helper: GameTestHelper) {
        val scene = scene(helper)
        val furnaceRel = BlockPos(7, 1, 3)
        val a = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64)).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val binding = TestKit.bind(helper, scene.owner, fire, furnaceRel)
        helper.assertTrue(binding?.target == a, "the Charmander did not bind to furnace A: $binding")
        var refusedAt = -1L
        helper.onEachTick {
            if (refusedAt < 0) {
                if (!isWorkingAt(helper, fire, a)) return@onEachTick
                refuse(helper, a)
                refusedAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - refusedAt
            if (age < 21) return@onEachTick
            val left = BindingStore.read(fire.pokemon)
            helper.assertTrue(left == null, "CD-LND-03: the binding $left remains $age ticks after the refusal")
            val claim = TestKit.claimOf(helper, fire)
            helper.assertTrue(claim == null, "CD-LND-03: the worker holds $claim")
            val last = TestKit.lastMessage(scene.owner)
            helper.assertTrue(
                last == "Charmander lost its work binding: the owner of its pasture may not use its target",
                "the binder got $last",
            )
            helper.succeed()
        }
    }

    /**
     * An Abra is bound to move items from chest S to chest D, and then the provider refuses D. 21 ticks later the binding is gone, and
     * the binder's message names the destination (CD-LND-03).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 400, batch = BATCH)
    fun cdLnd03RefusedDestinationClearsATelekinesisBinding(helper: GameTestHelper) {
        val sourceRel = BlockPos(4, 2, 4)
        val destinationRel = BlockPos(10, 2, 4)
        helper.setBlock(sourceRel, Blocks.CHEST)
        helper.getBlockEntity<ChestBlockEntity>(sourceRel).setItem(0, ItemStack(Items.COBBLESTONE, 64))
        helper.setBlock(destinationRel, Blocks.CHEST)
        val scene = scene(helper)
        val abra = TestKit.tether(helper, scene.pasture, scene.owner, "abra level=20")
        val binding = TestKit.bindTransfer(helper, scene.owner, abra, sourceRel, destinationRel)
        val d = helper.absolutePos(destinationRel)
        helper.assertTrue(binding?.destination == d, "the Abra was not bound from S to D: $binding")
        refuse(helper, d)
        val refusedAt = helper.tick
        helper.onEachTick {
            val age = helper.tick - refusedAt
            if (age < 21) return@onEachTick
            val left = BindingStore.read(abra.pokemon)
            helper.assertTrue(left == null, "CD-LND-03: the binding $left remains $age ticks after the refusal")
            val expected = "Abra lost its work binding: the owner of its pasture may not use its destination"
            helper.assertTrue(expected in TestKit.messages(scene.owner), "the binder got ${TestKit.messages(scene.owner)}")
            helper.succeed()
        }
    }

    /** Bed A 3 blocks away is refused, and bed B 6 blocks away is allowed: the Tired Charmander claims B and never A (CD-LND-04). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = BATCH)
    fun cdLnd04TiredWorkerTakesTheAllowedBed(helper: GameTestHelper) {
        val scene = scene(helper)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var beds: Pair<BlockPos, BlockPos>? = null
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            val claim = TestKit.claimOf(helper, fire)
            val placed = beds
            if (placed == null) {
                if (!isWorkingAt(helper, fire, furnace)) return@onEachTick
                val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
                val (nearRel, farRel) = bedCells(helper, spot, near = 3, far = 6)
                val a = refuse(helper, placeBed(helper, nearRel))
                beds = a to placeBed(helper, farRel ?: throw GameTestAssertException("no cell for bed B"))
                tireOut(helper, fire)
                return@onEachTick
            }
            val (a, b) = placed
            helper.assertFalse(
                claim?.key == TestKit.keyOf(helper, a),
                "CD-LND-04: the Tired worker claimed bed A, which the placer may not use",
            )
            if (!worker.tired) return@onEachTick
            helper.assertTrue(
                claim?.kind == ClaimKind.BED && claim.key == TestKit.keyOf(helper, b),
                "CD-LND-04: the Tired worker holds $claim",
            )
            helper.succeed()
        }
    }

    /** Bed A is the only bed, and the provider refuses it: the Tired Charmander claims no bed and rests in place (CD-LND-04). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = BATCH)
    fun cdLnd04OnlyBedRefusedRestsInPlace(helper: GameTestHelper) {
        val scene = scene(helper)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var tiredFrom = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, fire) ?: return@onEachTick
            val claim = TestKit.claimOf(helper, fire)
            helper.assertFalse(claim?.kind == ClaimKind.BED, "CD-LND-04: the worker claimed the refused bed: $claim")
            if (tiredFrom < 0) {
                if (!isWorkingAt(helper, fire, furnace)) return@onEachTick
                val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
                refuse(helper, placeBed(helper, bedCells(helper, spot, near = 3, far = 0).first))
                tireOut(helper, fire)
                tiredFrom = helper.tick
                return@onEachTick
            }
            if (worker.status == WorkerStatus.RESTING_IN_PLACE) {
                helper.succeed()
            } else {
                helper.assertTrue(
                    helper.tick - tiredFrom < 21,
                    "CD-LND-04: the worker is ${worker.status} ${helper.tick - tiredFrom} ticks on",
                )
            }
        }
    }

    /**
     * The provider refuses furnace B and records whom it is asked about. After 400 ticks it was asked only about the placer's UUID for
     * both furnaces, and no worker claimed B (CD-LND-05). The test runs alone, so that no other pasture's scan reaches its furnaces.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600, batch = "pasture_claims_lnd05")
    fun cdLnd05TheCheckGetsOnlyThePlacersUuid(helper: GameTestHelper) {
        val scene = scene(helper)
        val a = TestKit.furnace(helper, BlockPos(4, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val b = refuse(helper, TestKit.furnace(helper, BlockPos(10, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos)
        TestKit.afterTest(helper) { TestLandClaims.askedAbout.keys.removeAll(setOf(a, b)) }
        val workers = List(2) { TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10") }
        helper.onEachTick {
            for (worker in workers) {
                val claim = TestKit.claimOf(helper, worker)
                helper.assertFalse(claim?.key == TestKit.keyOf(helper, b), "CD-LND-05: a worker claimed furnace B")
            }
            if (helper.tick < 400) return@onEachTick
            for ((name, pos) in listOf("A" to a, "B" to b)) {
                val asked = TestLandClaims.askedAbout[pos].orEmpty()
                helper.assertTrue(asked == setOf(scene.owner.uuid), "CD-LND-05: the check of furnace $name was asked about $asked")
            }
            helper.succeed()
        }
    }

    /** The provider throws for furnace A, which is in the work area of a Charmander's pasture: no worker claims A (CD-LND-11). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600, batch = BATCH)
    fun cdLnd11ThrowingCheckGivesNoClaim(helper: GameTestHelper) {
        val scene = scene(helper)
        val a = refuse(helper, TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos, TestLandClaims.throwing)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        helper.onEachTick {
            val claim = TestKit.claimOf(helper, fire)
            helper.assertFalse(claim?.key == TestKit.keyOf(helper, a), "CD-LND-11: the Charmander claimed furnace A, whose check throws")
            if (helper.tick >= 400) helper.succeed()
        }
    }

    private companion object {
        const val BATCH = "pasture_claims"
    }
}
