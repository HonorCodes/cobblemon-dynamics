package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedCandidate
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.stamina.StaminaMath
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
     * travels to its bound target at once (CD-STA-14).
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
            val rested = helper.tick - tiredAt
            helper.assertTrue(abs(rested - capacity) <= 1.0, "CD-BED-08: full $rested ticks after Tired, not ${capacity.toLong()}")
            helper.assertTrue(worker.status == WorkerStatus.TRAVELING, "CD-STA-14: the rested worker is ${worker.status}")
            helper.assertTrue(claim?.key == furnaceKey, "CD-STA-14: the rested worker claims ${claim?.key}")
            helper.assertTrue(fire.enablePoseTypeRecalculation, "CD-AI-08: pose recalculation is off after the wake")
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
     * night, and the test keeps the Charmander drowsy so that Cobblemon's own go_to_sleep task puts it to sleep soon.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_400, batch = NIGHT_BATCH)
    fun cdAi07NightSleepEndsWhenWorkStarts(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val engine = TestKit.engine(helper)
        val id = fire.pokemon.uuid
        var claimedAt = -1L
        var asleep = false
        helper.runAtTickTime(2_399) {
            helper.fail("asleep $asleep, status ${fire.pokemon.status?.status?.name}, worker ${engine.worker(id)?.status}")
        }
        helper.onEachTick {
            val brain = fire.brain
            val claim = engine.claims.claimOf(id)
            // Cobblemon's drowsy sensor sets this at night by chance. While it is set, only the bridge can end the sleep.
            if (claim == null) brain.setMemory(CobblemonMemories.POKEMON_DROWSY, true)
            if (!asleep) {
                if (hasBattleSleep(fire) && brain.isMemoryValue(CobblemonMemories.POKEMON_SLEEPING, true)) {
                    asleep = true
                    TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
                }
                return@onEachTick
            }
            if (claim == null) {
                helper.assertTrue(hasBattleSleep(fire), "Cobblemon woke the Charmander before it got a claim")
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

        /** The settings before the fast batch. Batches run one after another, so one slot is enough. */
        private var saved: EngineSettings? = null
    }
}
