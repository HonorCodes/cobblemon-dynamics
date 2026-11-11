package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedCandidate
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.stamina.Stamina
import codes.honor.cobblemondynamics.stamina.StaminaMath
import codes.honor.cobblemondynamics.stamina.StaminaStore
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Tired workers through the real Brain: the walk to a bed, the sleep pose on it, recovery, and the way back to work. These are the
 * parts that depend on Cobblemon's navigation, pose and sleep code. The rules themselves have unit tests (RestMachineTest).
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class RestTests {
    private fun isSleepPose(entity: PokemonEntity): Boolean =
        entity.entityData.get(PokemonEntity.POSE_TYPE) == PoseType.SLEEP && !entity.enablePoseTypeRecalculation

    private fun hasBattleSleep(entity: PokemonEntity): Boolean = entity.pokemon.status?.status == Statuses.SLEEP

    /** The first pair of free cells on solid ground 3 and 5 blocks from [spot] in opposite directions, inside the 15 × 15 floor. */
    private fun bedCells(helper: GameTestHelper, spot: BlockPos): Pair<BlockPos, BlockPos> {
        val rel = TestKit.relative(helper, spot)
        for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val near = rel.offset(3 * dx, 0, 3 * dz)
            val far = rel.offset(-5 * dx, 0, -5 * dz)
            val free = listOf(near, far).all {
                it.x in 1..13 && it.z in 1..13 && helper.getBlockState(it).isAir && !helper.getBlockState(it.below()).isAir
            }
            if (free) return near to far
        }
        throw GameTestAssertException("no room for beds 3 and 5 blocks from the work spot $rel")
    }

    /**
     * A Lv1 Charmander works a furnace, and the test leaves it one tick of Work Stamina. When it is Tired it frees the furnace in the
     * same tick and claims the Hay Bed 3 blocks away, not the Plush Bed 5 blocks away (CD-STA-13, CD-BED-05). It walks there through
     * the Brain, lies within 0.25 blocks of the sleep point in the sleep pose (CD-BED-10), and never has the battle SLEEP status
     * (CD-AI-07). The Hay Bed halves its recovery time (CD-BED-03, CD-BED-04). At 100% the bed claim is gone in the same tick
     * (CD-BED-12), the pose recalculates again, also after a save and load (CD-AI-08), and the next scan sends it back to the furnace.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 3_200)
    fun cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8)).blockPos
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=1")
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        val capacity = StaminaMath.capacityTicks(1, WorkEngineSettings.current)
        var step = "work"
        var near: BlockPos? = null
        var far: BlockPos? = null
        var tiredAt = -1L
        var asleepAt = -1L
        helper.runAtTickTime(3_199) { helper.fail("stuck at '$step': ${engine.worker(id)?.status}, claim ${engine.claims.claimOf(id)}") }
        helper.onEachTick {
            helper.assertFalse(hasBattleSleep(fire), "CD-AI-07: the worker has the battle sleep status at '$step'")
            val worker = engine.worker(id) ?: return@onEachTick
            val claim = engine.claims.claimOf(id)
            when (step) {
                "work" -> if (worker.status == WorkerStatus.WORKING) {
                    val (nearRel, farRel) = bedCells(helper, worker.spot ?: throw GameTestAssertException("a Working worker with no spot"))
                    helper.setBlock(nearRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
                    helper.setBlock(farRel, DynamicsBlocks.BEDS.getValue(BedTier.PLUSH).get())
                    near = helper.absolutePos(nearRel)
                    far = helper.absolutePos(farRel)
                    worker.stamina.fraction = 1.0 / capacity
                    step = "tiring"
                }

                "tiring" -> if (worker.tired) {
                    tiredAt = helper.tick
                    helper.assertTrue(claim?.kind == ClaimKind.BED, "CD-STA-13: the Tired worker holds $claim, not a bed claim")
                    helper.assertFalse(claim?.key == TestKit.keyOf(helper, far!!), "CD-BED-05: the worker claimed the bed 5 blocks away")
                    helper.assertTrue(claim?.key == TestKit.keyOf(helper, near!!), "CD-BED-05: the worker claimed ${claim?.key}")
                    helper.assertTrue(worker.status == WorkerStatus.TO_BED, "the Tired worker is ${worker.status}, not TO_BED")
                    step = "to_bed"
                }

                "to_bed" -> when (worker.status) {
                    WorkerStatus.TO_BED -> {}

                    WorkerStatus.SLEEPING -> {
                        asleepAt = helper.tick
                        step = "asleep"
                    }

                    else -> helper.fail("the worker did not reach the Hay Bed: ${worker.status} ${helper.tick - tiredAt} ticks after Tired")
                }

                "asleep" -> {
                    val point = BedCandidate.sleepPoint(near!!)
                    if (worker.status == WorkerStatus.SLEEPING) {
                        val off = fire.position().distanceTo(point)
                        helper.assertTrue(off <= 0.25, "CD-BED-10: the sleeper is $off blocks from the bed's center")
                        if (helper.tick > asleepAt) helper.assertTrue(isSleepPose(fire), "the sleeper does not show the sleep pose")
                        return@onEachTick
                    }
                    helper.assertTrue(worker.status == WorkerStatus.IDLE && !worker.tired, "the sleeper left the bed as ${worker.status}")
                    helper.assertTrue(claim == null, "CD-BED-12: the claim $claim remains on the tick that the worker reached 100%")
                    helper.assertTrue(helper.tick - asleepAt >= 1_200, "the worker slept only ${helper.tick - asleepAt} ticks")
                    // Base rate on the way to the bed, twice the base rate on the Hay Bed, from the tick after it got Tired.
                    val walked = asleepAt - 1 - tiredAt
                    val expected = asleepAt - 1 + ceil((capacity - walked) / StaminaMath.bedFactor(1, WorkEngineSettings.current)).toLong()
                    helper.assertTrue(abs(helper.tick - expected) <= 1, "CD-BED-03: full at tick ${helper.tick}, expected $expected")
                    helper.assertTrue(fire.enablePoseTypeRecalculation, "CD-AI-08: pose recalculation is off after the wake")
                    val saved = CompoundTag()
                    helper.assertTrue(fire.save(saved), "the worker entity did not save")
                    val loaded = EntityType.create(saved, helper.level).orElse(null) as? PokemonEntity
                    helper.assertTrue(loaded?.enablePoseTypeRecalculation == true, "CD-AI-08: pose recalculation is off after a reload")
                    step = "awake"
                }

                "awake" -> if (worker.status == WorkerStatus.WORKING) {
                    helper.assertTrue(claim?.key == TestKit.keyOf(helper, furnace), "the rested worker works ${claim?.key}")
                    helper.succeed()
                }
            }
        }
    }

    /**
     * A leashed Lv10 Charmander with no bed in the work area works its furnace until Tired (200 ticks in this batch). It rests in
     * place: it holds where it stood, in the sleep pose, and keeps its binding while its target stays reserved (CD-BED-08, CD-LSH-12,
     * CD-CLM-06, CD-CLM-09). It reaches 100% after its capacity at the base rate, 200 ticks here and 36,000 at the default, and
     * claims its bound target again within 20 ticks (CD-STA-14). The rest's hold leaves its navigation unable to plan on the tick that
     * it wakes, so its next poll claims (CD-ASG-09). It rested on its work spot, so it may be Working in the tick of that claim.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200, batch = FAST_BATCH)
    fun cdBed08LeashedWorkerRestsInPlaceAndGoesBack(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8)).blockPos
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val binding = TestKit.bind(helper, owner, fire, BlockPos(7, 1, 3)) ?: throw GameTestAssertException("the leash did not bind")
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        val furnaceKey = TestKit.keyOf(helper, furnace)
        val capacity = StaminaMath.capacityTicks(10, WorkEngineSettings.current)
        var tiredAt = -1L
        var wokeAt = -1L
        var restSpot: Vec3? = null
        helper.onEachTick {
            helper.assertFalse(hasBattleSleep(fire), "CD-AI-07: the worker has the battle sleep status")
            helper.assertTrue(BindingStore.read(fire.pokemon) == binding, "CD-LSH-12: the binding is ${BindingStore.read(fire.pokemon)}")
            val worker = engine.worker(id) ?: return@onEachTick
            val claim = engine.claims.claimOf(id)
            if (tiredAt < 0) {
                if (!worker.tired) return@onEachTick
                tiredAt = helper.tick
                restSpot = fire.position()
                helper.assertTrue(worker.status == WorkerStatus.RESTING_IN_PLACE, "CD-BED-08: with no bed it is ${worker.status}")
                helper.assertTrue(claim == null, "CD-STA-13: the Tired worker still holds $claim")
                helper.assertFalse(engine.claims.isFree(furnaceKey, UUID.randomUUID()), "CD-CLM-06: the bound furnace is free for others")
                return@onEachTick
            }
            if (worker.tired) {
                helper.assertTrue(worker.status == WorkerStatus.RESTING_IN_PLACE, "the Tired worker is ${worker.status}")
                helper.assertTrue(claim == null, "CD-LSH-12: the Tired worker took $claim")
                val off = fire.position().distanceTo(restSpot!!)
                helper.assertTrue(off <= 0.25, "CD-BED-08: the resting worker moved $off blocks")
                if (helper.tick > tiredAt) helper.assertTrue(isSleepPose(fire), "CD-BED-08: the resting worker shows no sleep pose")
                return@onEachTick
            }
            if (wokeAt < 0) {
                wokeAt = helper.tick
                val rested = wokeAt - tiredAt
                helper.assertTrue(abs(rested - capacity) <= 1.0, "CD-BED-08: full $rested ticks after Tired, not ${capacity.toLong()}")
                helper.assertTrue(fire.enablePoseTypeRecalculation, "CD-AI-08: pose recalculation is off after the wake")
            }
            if (claim?.key != furnaceKey) {
                helper.assertTrue(helper.tick - wokeAt < 20, "CD-STA-14: the rested worker claims ${claim?.key} 20 ticks after the wake")
                return@onEachTick
            }
            helper.assertTrue(worker.status != WorkerStatus.IDLE, "CD-STA-14: the rested worker claims its furnace and is IDLE")
            helper.succeed()
        }
    }

    /**
     * A Wailord, nearly 8 blocks wide, at 0% claims the one Hay Bed and lies on it (CD-BED-09). Navigation stops a wide Pokémon half
     * its width from its goal, so it reaches the bed from that far and is laid on the sleep point.
     */
    @GameTest(template = "floor_41x8x41", timeoutTicks = 400)
    fun cdBed09WailordSleepsOnAHayBed(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasturePos = BlockPos(20, 1, 20)
        val pasture = TestKit.placePasture(helper, pasturePos, owner)
        TestKit.workMode(pasture, true)
        val whale = TestKit.tether(helper, pasture, owner, "wailord level=50")
        val whaleRel = TestKit.relative(helper, whale.blockPosition())
        // On the ground that the Wailord stands on, at the work area's edge on its side of the pasture.
        val bedRel = BlockPos(whaleRel.x, whaleRel.y, pasturePos.z + 8)
        helper.setBlock(bedRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val bed = helper.absolutePos(bedRel)
        val point = BedCandidate.sleepPoint(bed)
        val engine = TestKit.engine(helper)
        val id = whale.pokemon.uuid
        var spent = false
        var asleepAt = -1L
        helper.onEachTick {
            val worker = engine.worker(id) ?: return@onEachTick
            if (!spent) {
                helper.assertTrue(whale.bbWidth > 7f, "the Wailord is ${whale.bbWidth} blocks wide")
                worker.stamina.fraction = 0.0
                worker.tired = true
                spent = true
                return@onEachTick
            }
            if (worker.status != WorkerStatus.SLEEPING) {
                helper.assertTrue(worker.status == WorkerStatus.TO_BED, "the Tired Wailord is ${worker.status}")
                return@onEachTick
            }
            val claim = engine.claims.claimOf(id)
            helper.assertTrue(claim?.kind == ClaimKind.BED && claim.key == TestKit.keyOf(helper, bed), "the Wailord holds $claim")
            val off = whale.position().distanceTo(point)
            helper.assertTrue(off <= 0.25, "CD-BED-10: the Wailord is $off blocks from the bed's center")
            if (asleepAt < 0) asleepAt = helper.tick
            if (helper.tick - asleepAt >= 100) {
                helper.assertTrue(isSleepPose(whale), "the sleeping Wailord shows no sleep pose")
                helper.succeed()
            }
        }
    }

    /**
     * Carry-over from M1. Cobblemon's night sleep gives a pastured Pokémon the battle SLEEP status and its sleeping memory, and only its
     * own wake-up task, in the sleeping activity, clears them. A work order replaces that activity, so the Brain bridge wakes the
     * Pokémon when it starts work: no battle status, no sleeping memory and no sleep pose while it works (CD-AI-07). The batch runs at
     * night, and the test keeps the Charmander drowsy so that Cobblemon's own go_to_sleep task puts it to sleep soon. The furnace comes
     * only while the Charmander sleeps, so its claim wakes a sleeper.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_400, batch = NIGHT_BATCH)
    fun cdAi07NightSleepEndsWhenWorkStarts(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        val furnacePos = BlockPos(7, 1, 3)
        var furnace: AbstractFurnaceBlockEntity? = null
        var claimedAt = -1L
        var asleep = false
        var earlyWakes = 0
        helper.runAtTickTime(2_399) {
            helper.fail(
                "asleep $asleep, status ${fire.pokemon.status?.status?.name}, worker ${engine.worker(id)?.status}, early wakes $earlyWakes",
            )
        }
        helper.onEachTick {
            val brain = fire.brain
            val claim = engine.claims.claimOf(id)
            // Cobblemon's drowsy sensor sets this at night by chance. While it is set, only the bridge can end the sleep.
            if (claim == null) brain.setMemory(CobblemonMemories.POKEMON_DROWSY, true)
            if (!asleep) {
                if (hasBattleSleep(fire) && brain.isMemoryValue(CobblemonMemories.POKEMON_SLEEPING, true)) {
                    asleep = true
                    furnace = TestKit.furnace(helper, furnacePos, ItemStack(Items.RAW_IRON, 8))
                }
                return@onEachTick
            }
            if (claim == null) {
                if (!hasBattleSleep(fire)) {
                    // The drowsy sensor also clears the memory by chance (its rouse chance, 1 in 240 for each run, once a second).
                    // Cobblemon's wake-up task then wakes the Charmander in the same Brain tick, before this handler sets the memory
                    // again. Take the furnace away and wait for the next sleep, so that the claim still comes while the Charmander sleeps.
                    earlyWakes++
                    asleep = false
                    furnace?.clearContent()
                    furnace = null
                    helper.setBlock(furnacePos, Blocks.AIR)
                }
                return@onEachTick
            }
            if (claimedAt < 0) claimedAt = helper.tick
            helper.assertTrue(claim.kind == ClaimKind.WORK, "the woken worker holds $claim")
            helper.assertFalse(hasBattleSleep(fire), "the worker kept Cobblemon's battle sleep status after it took a claim")
            helper.assertFalse(brain.isMemoryValue(CobblemonMemories.POKEMON_SLEEPING, true), "the worker kept Cobblemon's sleeping memory")
            if (helper.tick - claimedAt >= 2) {
                helper.assertFalse(fire.entityData.get(PokemonEntity.POSE_TYPE) == PoseType.SLEEP, "the worker works in the sleep pose")
            }
            if (engine.worker(id)?.status == WorkerStatus.WORKING) helper.succeed()
        }
    }

    /**
     * Two Lv10 Charmanders and one furnace. The one that works it is left one tick of Work Stamina: on the tick that it is Tired its
     * work claim is gone (CD-STA-13), and the other Charmander, which waited Idle, claims the furnace at the pasture's next scan, at
     * most `scanIntervalTicks` later.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdSta13TiredAutoWorkerFreesItsMachineForTheNextScan(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8)).blockPos
        val pair = List(2) { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        val engine = TestKit.engine(helper)
        val key = TestKit.keyOf(helper, furnace)
        val capacity = StaminaMath.capacityTicks(10, WorkEngineSettings.current)
        var first: PokemonEntity? = null
        var tiredAt = -1L
        helper.onEachTick {
            val holder = engine.claims.holderOf(key)
            val tired = first
            if (tired == null) {
                val working = pair.firstOrNull { TestKit.workerOf(helper, it)?.status == WorkerStatus.WORKING } ?: return@onEachTick
                TestKit.workerOf(helper, working)!!.stamina.fraction = 1.0 / capacity
                first = working
                return@onEachTick
            }
            val worker = TestKit.workerOf(helper, tired) ?: throw GameTestAssertException("the first Charmander left the engine")
            if (tiredAt < 0) {
                if (!worker.tired) return@onEachTick
                tiredAt = helper.tick
                helper.assertTrue(
                    TestKit.claimOf(helper, tired) == null,
                    "CD-STA-13: the Tired worker still holds ${TestKit.claimOf(helper, tired)}",
                )
                helper.assertTrue(holder == null, "CD-STA-13: the furnace is still claimed by $holder on the tick its worker got Tired")
                return@onEachTick
            }
            val second = pair.single { it !== tired }
            if (holder == null) {
                val waited = helper.tick - tiredAt
                helper.assertTrue(
                    waited <= WorkEngineSettings.current.scanIntervalTicks,
                    "CD-STA-13: no claim on the free furnace after $waited ticks",
                )
                return@onEachTick
            }
            helper.assertTrue(
                holder.worker == second.pokemon.uuid,
                "CD-STA-13: the free furnace went to ${holder.worker}, not the waiting worker",
            )
            helper.succeed()
        }
    }

    /**
     * A Lv10 Charmander is Tired at 300 ticks short of 100% and rests in place, and the owner binds it to a furnace with input. It
     * neither claims nor works the furnace before it reaches 100%, and it keeps the binding (CD-LSH-12). At 100% it travels to the
     * furnace and works it.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdLsh12BindingMadeWhileTiredWaitsUntilFull(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val furnaceRel = BlockPos(7, 1, 3)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 8)).blockPos
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        val capacity = StaminaMath.capacityTicks(10, WorkEngineSettings.current)
        var bound: Binding? = null
        var spent = false
        var rested = false
        helper.onEachTick {
            val worker = engine.worker(id) ?: return@onEachTick
            if (!spent) {
                worker.stamina.fraction = 1.0 - REST_LEFT_TICKS / capacity
                worker.tired = true
                spent = true
                return@onEachTick
            }
            if (bound == null) {
                if (worker.status != WorkerStatus.RESTING_IN_PLACE) return@onEachTick
                bound =
                    TestKit.bind(helper, owner, fire, furnaceRel)
                        ?: throw GameTestAssertException("the leash did not bind the Tired worker")
                helper.assertTrue(bound?.target == furnace, "the Tired worker is bound to ${bound?.target}")
                return@onEachTick
            }
            helper.assertTrue(BindingStore.read(fire.pokemon) == bound, "CD-LSH-12: the binding is ${BindingStore.read(fire.pokemon)}")
            val claim = engine.claims.claimOf(id)
            if (worker.tired) {
                helper.assertTrue(claim == null, "CD-LSH-12: the Tired worker took $claim at ${worker.stamina}")
                helper.assertTrue(worker.status == WorkerStatus.RESTING_IN_PLACE, "CD-LSH-12: the Tired worker is ${worker.status}")
                return@onEachTick
            }
            if (!rested) {
                helper.assertTrue(worker.stamina.fraction == 1.0, "CD-LSH-12: the worker stopped resting at ${worker.stamina}")
                rested = true
            }
            if (worker.status == WorkerStatus.WORKING) {
                helper.assertTrue(claim?.key == TestKit.keyOf(helper, furnace), "the rested worker works ${claim?.key}")
                helper.succeed()
            }
        }
    }

    /**
     * A pasture at the center of the 23 × 23 floor and one Lv10 Charmander. With the only bed at horizontal offset 9, outside the
     * work area, the Tired worker rests in place and claims no bed. It is woken, the bed moves to offset 8, inside the work area, and
     * the next time it is Tired it claims that bed (CD-BED-07).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600)
    fun cdBed07OnlyABedInsideTheWorkAreaIsClaimed(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        val outsideRel = BlockPos(2, 2, 11)
        val insideRel = BlockPos(3, 2, 11)
        val hay = DynamicsBlocks.BEDS.getValue(BedTier.HAY).get()
        helper.setBlock(outsideRel, hay)
        val outsideKey = TestKit.keyOf(helper, helper.absolutePos(outsideRel))
        val insideKey = TestKit.keyOf(helper, helper.absolutePos(insideRel))
        var step = "outside"
        var since = -1L
        helper.onEachTick {
            val worker = engine.worker(id) ?: return@onEachTick
            val claim = engine.claims.claimOf(id)
            helper.assertFalse(claim?.key == outsideKey, "CD-BED-07: the worker claimed the bed at offset 9")
            when (step) {
                "outside" -> {
                    worker.stamina.fraction = 0.0
                    worker.tired = true
                    since = helper.tick
                    step = "resting"
                }

                "resting" -> {
                    helper.assertTrue(
                        worker.status == WorkerStatus.RESTING_IN_PLACE,
                        "CD-BED-07: with the bed at offset 9 it is ${worker.status}",
                    )
                    helper.assertTrue(claim == null, "CD-BED-07: with the bed at offset 9 it holds $claim")
                    if (helper.tick - since < 100) return@onEachTick
                    worker.stamina.fraction = 1.0
                    worker.tired = false
                    step = "woken"
                }

                "woken" -> if (worker.status == WorkerStatus.IDLE) {
                    helper.setBlock(outsideRel, Blocks.AIR)
                    helper.setBlock(insideRel, hay)
                    worker.stamina.fraction = 0.0
                    worker.tired = true
                    step = "inside"
                }

                "inside" -> {
                    helper.assertTrue(claim?.kind == ClaimKind.BED && claim.key == insideKey, "CD-BED-07: the bed at offset 8 gave $claim")
                    val toBed = worker.status == WorkerStatus.TO_BED || worker.status == WorkerStatus.SLEEPING
                    helper.assertTrue(toBed, "CD-BED-07: with the bed at offset 8 it is ${worker.status}")
                    helper.succeed()
                }
            }
        }
    }

    /**
     * A Tired Lv10 Charmander sleeps on a Hay Bed and recovers at twice the base rate. The bed is broken under it, and on the next tick
     * it rests in place, holds no bed claim and recovers at the base rate (CD-BED-11).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBed11BrokenBedMeansRestInPlace(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val bedRel = BlockPos(7, 2, 3)
        helper.setBlock(bedRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        val settings = WorkEngineSettings.current
        val base = 1.0 / StaminaMath.capacityTicks(10, settings)
        val onHay = base * StaminaMath.bedFactor(BedTier.HAY.tier, settings)
        var spent = false
        var sleptAt = -1L
        var brokenAt = -1L
        var last = -1.0
        helper.onEachTick {
            val worker = engine.worker(id) ?: return@onEachTick
            val claim = engine.claims.claimOf(id)
            val fraction = worker.stamina.fraction
            val step = fraction - last
            last = fraction
            if (!spent) {
                TestKit.moveTo(helper, fire, BlockPos(7, 2, 5))
                worker.stamina.fraction = 0.0
                worker.tired = true
                spent = true
                return@onEachTick
            }
            if (brokenAt < 0) {
                if (worker.status != WorkerStatus.SLEEPING) return@onEachTick
                if (sleptAt < 0) sleptAt = helper.tick
                // Measured over a tick that starts and ends asleep, so the walk's base rate is not in it.
                if (helper.tick - sleptAt < 2) return@onEachTick
                helper.assertTrue(abs(step - onHay) < 1e-12, "the sleeper gains $step per tick on the Hay Bed, not $onHay")
                helper.level.destroyBlock(helper.absolutePos(bedRel), false)
                brokenAt = helper.tick
                return@onEachTick
            }
            helper.assertTrue(worker.status == WorkerStatus.RESTING_IN_PLACE, "CD-BED-11: 1 tick after the break it is ${worker.status}")
            helper.assertTrue(claim == null, "CD-BED-11: 1 tick after the break it holds $claim")
            helper.assertTrue(abs(step - base) < 1e-12, "CD-BED-11: 1 tick after the break it gains $step per tick, not the base $base")
            helper.succeed()
        }
    }

    /**
     * Beside a free Hay Bed for 1,200 ticks: a Work Mode worker at 50%, a wild Charmander, and a Charmander of a pasture with Work
     * Mode off whose saved Work Stamina says Tired. None of them claims the bed or lies down on it (CD-BED-13). Then the worker is
     * Tired, and it claims the bed, which shows that the bed was free to take.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_400)
    fun cdBed13OnlyTiredWorkersOfWorkModePasturesGoToBed(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val offPasture = TestKit.placePasture(helper, BlockPos(10, 1, 7), owner)
        val bedRel = BlockPos(7, 2, 7)
        helper.setBlock(bedRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val bedKey = TestKit.keyOf(helper, helper.absolutePos(bedRel))
        val worker = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val wild = TestKit.wild(helper, BlockPos(7, 2, 8), "charmander level=10")
        val off = TestKit.tether(helper, offPasture, owner, "charmander level=10")
        StaminaStore.write(off.pokemon.persistentData, Stamina(0.0, tired = true), helper.level.gameTime)
        val engine = TestKit.engine(helper)
        val id = worker.pokemon.uuid
        var halfAt = -1L
        helper.onEachTick {
            for ((what, entity) in listOf(
                "the 50% worker" to worker,
                "the wild Charmander" to wild,
                "the Work Mode off Charmander" to off,
            )) {
                if (halfAt >= 0 && helper.tick - halfAt >= QUIET_TICKS && entity === worker) continue
                helper.assertFalse(isSleepPose(entity), "CD-BED-13: $what lies down")
                helper.assertTrue(engine.claims.claimOf(entity.pokemon.uuid) == null, "CD-BED-13: $what holds a claim")
            }
            helper.assertTrue(
                engine.claims.holderOf(bedKey)?.worker.let {
                    it == null || it == id
                },
                "CD-BED-13: another Pokémon holds the bed",
            )
            helper.assertTrue(engine.worker(off.pokemon.uuid) == null, "the Charmander of the Work Mode off pasture is a worker")
            helper.assertTrue(engine.worker(wild.pokemon.uuid) == null, "the wild Charmander is a worker")
            val live = engine.worker(id) ?: return@onEachTick
            if (halfAt < 0) {
                live.stamina.fraction = 0.5
                halfAt = helper.tick
                return@onEachTick
            }
            val waited = helper.tick - halfAt
            if (waited < QUIET_TICKS) {
                helper.assertFalse(live.status.isResting, "CD-BED-13: the worker at ${live.stamina} is ${live.status}")
                return@onEachTick
            }
            if (waited == QUIET_TICKS.toLong()) {
                live.stamina.fraction = 0.0
                live.tired = true
                return@onEachTick
            }
            val claim = engine.claims.claimOf(id)
            helper.assertTrue(claim?.kind == ClaimKind.BED && claim.key == bedKey, "the Tired worker did not claim the free bed: $claim")
            helper.succeed()
        }
    }

    /**
     * A Tired Lv10 Charmander sleeps on a Hay Bed, and the server restarts: every claim is gone. When the worker joins again, it lies
     * on a free bed in the sleep pose within 40 ticks (CD-BED-14). The restart drops the whole engine, so the test has its own batch.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = "restart_bed14")
    fun cdBed14SleeperClaimsABedAfterRestart(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val bedRel = BlockPos(7, 2, 3)
        helper.setBlock(bedRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val bed = helper.absolutePos(bedRel)
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        var spent = false
        var sleptAt = -1L
        var restartedAt = -1L
        var loadedAt = -1L
        helper.onEachTick {
            if (restartedAt < 0) {
                val worker = engine.worker(id) ?: return@onEachTick
                if (!spent) {
                    worker.stamina.fraction = 0.0
                    worker.tired = true
                    spent = true
                    return@onEachTick
                }
                if (worker.status != WorkerStatus.SLEEPING) return@onEachTick
                if (sleptAt < 0) sleptAt = helper.tick
                if (helper.tick - sleptAt < 60) return@onEachTick
                TestKit.simulateRestart(helper)
                helper.assertTrue(engine.claims.all().isEmpty(), "the registry holds a claim after the restart")
                restartedAt = helper.tick
                return@onEachTick
            }
            val worker = engine.worker(id) ?: return@onEachTick
            if (loadedAt < 0) {
                loadedAt = helper.tick
                helper.assertTrue(worker.tired, "the worker joined after the restart at ${worker.stamina}, not Tired")
            }
            val claim = engine.claims.claimOf(id)
            val onBed = worker.status == WorkerStatus.SLEEPING &&
                claim?.kind == ClaimKind.BED &&
                claim.key == TestKit.keyOf(helper, bed) &&
                fire.position().distanceTo(BedCandidate.sleepPoint(bed)) <= 0.25
            if (onBed && isSleepPose(fire)) {
                helper.succeed()
                return@onEachTick
            }
            val waited = helper.tick - loadedAt
            helper.assertTrue(waited < 40, "CD-BED-14: $waited ticks after it joined the worker is ${worker.status} with $claim")
        }
    }

    /** Settings for tests that wait for real drain and recovery: a Lv10 worker drains and recovers in 200 ticks. */
    @BeforeBatch(batch = FAST_BATCH)
    fun fastStamina(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        saved = current
        WorkEngineSettings.current = current.copy(ticksPerLevel = 20, healerMinutesAtLevel1 = 1, healerMinutesAtLevel100 = 1)
    }

    @AfterBatch(batch = FAST_BATCH)
    fun normalStamina(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = saved ?: EngineSettings.DEFAULT
        saved = null
    }

    @BeforeBatch(batch = NIGHT_BATCH)
    fun night(level: ServerLevel) {
        level.dayTime = NIGHT
    }

    /** The test mod's start time, so night sleep never touches the other tests. */
    @AfterBatch(batch = NIGHT_BATCH)
    fun day(level: ServerLevel) {
        level.dayTime = DAY
    }

    companion object {
        const val FAST_BATCH = "stamina_fast"
        const val NIGHT_BATCH = "night_sleep"
        private const val NIGHT = 18_000L
        private const val DAY = 6_000L
        private const val REST_LEFT_TICKS = 300.0
        private const val QUIET_TICKS = 1_200

        /** The settings before the fast batch. Batches run one after another, so one slot is enough. */
        private var saved: EngineSettings? = null
    }
}
