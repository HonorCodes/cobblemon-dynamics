package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.stamina.StaminaMath
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class SentryTests {
    private fun postSpawn(helper: GameTestHelper, mob: Mob, pos: BlockPos, spawnType: MobSpawnType): Boolean {
        val event = FinalizeSpawnEvent(
            mob,
            helper.level,
            pos.x + 0.5,
            pos.y.toDouble(),
            pos.z + 0.5,
            helper.level.getCurrentDifficultyAt(pos),
            spawnType,
            null,
            null,
        )
        NeoForge.EVENT_BUS.post(event)
        return event.isSpawnCancelled
    }

    private fun isNaturalZombieCancelled(helper: GameTestHelper, pos: BlockPos): Boolean {
        val zombie = EntityType.ZOMBIE.create(helper.level) ?: error("failed to create zombie")
        return postSpawn(helper, zombie, pos, MobSpawnType.NATURAL)
    }

    private fun isWorkingSentry(helper: GameTestHelper, entity: PokemonEntity): Boolean =
        TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING &&
            TestKit.claimOf(helper, entity)?.kind == ClaimKind.WORK &&
            TestKit.engine(helper).roleOf(entity.pokemon.uuid)?.definition?.id == id("sentry")

    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "sentry_active")
    fun cdSnt01to03SentryCancelsOnlyNaturalMonstersInWorkArea(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pastureRel = BlockPos(11, 1, 11)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.workMode(pasture, true)
        val first = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        val second = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        val binding = TestKit.bind(helper, owner, first, pastureRel)
        helper.assertTrue(binding == null, "CD-SNT-01: the Work Leash bound a Sentry: $binding")
        val inside = pasture.blockPos.offset(4, 1, 0)
        val outside = pasture.blockPos.offset(9, 1, 0)
        var verifiedTicks = 0
        helper.onEachTick {
            val working = listOf(first, second).filter { isWorkingSentry(helper, it) }
            if (working.isEmpty()) return@onEachTick
            helper.assertTrue(working.size == 1, "CD-SNT-01: ${working.size} Sentries worked one pasture at once")
            val other = if (working.single() === first) second else first
            helper.assertTrue(
                TestKit.claimOf(helper, other) == null && TestKit.workerOf(helper, other)?.status == WorkerStatus.IDLE,
                "CD-SNT-01: the second Umbreon did not stay Idle: ${TestKit.workerOf(helper, other)?.status}",
            )
            helper.assertTrue(isNaturalZombieCancelled(helper, inside), "CD-SNT-02: natural zombie inside workArea was not cancelled")
            helper.assertFalse(isNaturalZombieCancelled(helper, outside), "CD-SNT-02: natural zombie outside workArea was cancelled")

            val spawnerZombie = EntityType.ZOMBIE.create(helper.level) ?: error("no zombie")
            helper.assertFalse(
                postSpawn(helper, spawnerZombie, inside, MobSpawnType.SPAWNER),
                "CD-SNT-03: spawner zombie was cancelled",
            )
            val trialSkeleton = EntityType.SKELETON.create(helper.level) ?: error("no skeleton")
            helper.assertFalse(
                postSpawn(helper, trialSkeleton, inside, MobSpawnType.TRIAL_SPAWNER),
                "CD-SNT-03: trial spawner skeleton was cancelled",
            )
            val raidPillager = EntityType.PILLAGER.create(helper.level) ?: error("no pillager")
            helper.assertFalse(
                postSpawn(helper, raidPillager, inside, MobSpawnType.EVENT),
                "CD-SNT-03: raid event pillager was cancelled",
            )
            val naturalCow = EntityType.COW.create(helper.level) ?: error("no cow")
            helper.assertFalse(
                postSpawn(helper, naturalCow, inside, MobSpawnType.NATURAL),
                "CD-SNT-03: natural cow was cancelled",
            )
            val naturalPokemon = PokemonEntity(helper.level, TestKit.pokemon("rattata level=5"))
            helper.assertFalse(
                postSpawn(helper, naturalPokemon, inside, MobSpawnType.NATURAL),
                "CD-SNT-03: natural Cobblemon was cancelled",
            )
            if (++verifiedTicks >= 45) helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400, batch = "sentry_work_mode_off")
    fun cdSnt04WorkModeOffRestoresNaturalSpawnsWithinOneTick(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val umbreon = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        val inside = pasture.blockPos.offset(3, 1, 0)
        var offAt = -1L
        helper.onEachTick {
            if (offAt < 0) {
                if (!isWorkingSentry(helper, umbreon)) return@onEachTick
                helper.assertTrue(isNaturalZombieCancelled(helper, inside), "Sentry did not cancel before Work Mode turned off")
                TestKit.workMode(pasture, false)
                offAt = helper.tick
            } else if (helper.tick == offAt + 1) {
                helper.assertFalse(
                    isNaturalZombieCancelled(helper, inside),
                    "CD-SNT-04: natural spawn still cancelled 1 tick after Work Mode off",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400, batch = "sentry_recall")
    fun cdSnt04RecallRestoresNaturalSpawnsWithinOneTick(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val umbreon = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        val inside = pasture.blockPos.offset(3, 1, 0)
        var recalledAt = -1L
        helper.onEachTick {
            if (recalledAt < 0) {
                if (!isWorkingSentry(helper, umbreon)) return@onEachTick
                helper.assertTrue(isNaturalZombieCancelled(helper, inside), "Sentry did not cancel before recall")
                pasture.releasePokemon(umbreon.pokemon.uuid)
                recalledAt = helper.tick
            } else if (helper.tick == recalledAt + 1) {
                helper.assertFalse(
                    isNaturalZombieCancelled(helper, inside),
                    "CD-SNT-04: natural spawn still cancelled 1 tick after recall",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400, batch = "sentry_tired")
    fun cdSnt04TiredRestoresNaturalSpawnsWithinOneTick(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val umbreon = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        val inside = pasture.blockPos.offset(3, 1, 0)
        var nearlyEmptyAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, umbreon) ?: return@onEachTick
            if (nearlyEmptyAt < 0) {
                if (!isWorkingSentry(helper, umbreon)) return@onEachTick
                helper.assertTrue(isNaturalZombieCancelled(helper, inside), "Sentry did not cancel before draining")
                worker.stamina.fraction = 0.5 / StaminaMath.capacityTicks(umbreon.pokemon.level, WorkEngineSettings.current)
                nearlyEmptyAt = helper.tick
            } else if (helper.tick == nearlyEmptyAt + 1) {
                helper.assertTrue(worker.tired && worker.status.isResting, "Umbreon did not enter Tired rest: ${worker.status}")
                helper.assertFalse(
                    isNaturalZombieCancelled(helper, inside),
                    "CD-SNT-04: natural spawn still cancelled 1 tick after Tired",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 500, batch = DAY_BATCH)
    fun cdSnt05DaytimeSentryDrainsFullStamina(helper: GameTestHelper) {
        helper.assertFalse(helper.level.isNight, "CD-SNT-05: day batch ran during night")
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val umbreon = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        var startTick = -1L
        var startFraction = 0.0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, umbreon) ?: return@onEachTick
            if (startTick < 0) {
                if (!isWorkingSentry(helper, umbreon)) return@onEachTick
                startTick = helper.tick
                startFraction = worker.stamina.fraction
                return@onEachTick
            }
            helper.assertTrue(worker.status == WorkerStatus.WORKING, "Umbreon left Working during daytime measurement")
            if (helper.tick == startTick + MEASURE_TICKS) {
                val expected = MEASURE_TICKS / StaminaMath.capacityTicks(10, WorkEngineSettings.current)
                val actual = startFraction - worker.stamina.fraction
                helper.assertTrue(
                    abs(actual - expected) <= TOLERANCE,
                    "CD-SNT-05: daytime Umbreon drained $actual instead of $expected",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 500, batch = NIGHT_BATCH)
    fun cdSnt05NighttimeDarkWorkersDrainHalfStamina(helper: GameTestHelper) {
        helper.assertTrue(helper.level.isNight, "CD-SNT-05: night batch did not run during night")
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val houndoomFurnace = BlockPos(3, 1, 3)
        val charmanderFurnace = BlockPos(11, 1, 3)
        TestKit.furnace(helper, houndoomFurnace, ItemStack(Items.RAW_IRON, 64))
        TestKit.furnace(helper, charmanderFurnace, ItemStack(Items.RAW_IRON, 64))
        val umbreon = TestKit.tether(helper, pasture, owner, "umbreon level=10")
        val houndoom = TestKit.tether(helper, pasture, owner, "houndoom level=10")
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        helper.assertTrue(TestKit.bind(helper, owner, houndoom, houndoomFurnace) != null, "Houndoom did not bind to furnace")
        helper.assertTrue(TestKit.bind(helper, owner, charmander, charmanderFurnace) != null, "Charmander did not bind to furnace")
        TestKit.moveTo(helper, houndoom, BlockPos(3, 2, 4))
        TestKit.moveTo(helper, charmander, BlockPos(11, 2, 4))
        var startTick = -1L
        var umbreonStart = 0.0
        var houndoomStart = 0.0
        var charmanderStart = 0.0
        helper.onEachTick {
            val uWorker = TestKit.workerOf(helper, umbreon) ?: return@onEachTick
            val hWorker = TestKit.workerOf(helper, houndoom) ?: return@onEachTick
            val cWorker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            val allWorking = uWorker.status == WorkerStatus.WORKING &&
                hWorker.status == WorkerStatus.WORKING &&
                cWorker.status == WorkerStatus.WORKING
            if (startTick < 0) {
                if (!allWorking) return@onEachTick
                startTick = helper.tick
                umbreonStart = uWorker.stamina.fraction
                houndoomStart = hWorker.stamina.fraction
                charmanderStart = cWorker.stamina.fraction
                return@onEachTick
            }
            helper.assertTrue(allWorking, "A worker left Working during night stamina measurement")
            if (helper.tick == startTick + MEASURE_TICKS) {
                val fullDrain = MEASURE_TICKS / StaminaMath.capacityTicks(10, WorkEngineSettings.current)
                val halfDrain = fullDrain * StaminaMath.NOCTURNAL_DRAIN_FACTOR
                val uDrain = umbreonStart - uWorker.stamina.fraction
                val hDrain = houndoomStart - hWorker.stamina.fraction
                val cDrain = charmanderStart - cWorker.stamina.fraction
                helper.assertTrue(abs(uDrain - halfDrain) <= TOLERANCE, "CD-SNT-05: night Umbreon drained $uDrain, expected $halfDrain")
                helper.assertTrue(abs(hDrain - halfDrain) <= TOLERANCE, "CD-SNT-05: night Houndoom drained $hDrain, expected $halfDrain")
                helper.assertTrue(abs(cDrain - fullDrain) <= TOLERANCE, "CD-SNT-05: night Charmander drained $cDrain, expected $fullDrain")
                helper.succeed()
            }
        }
    }

    @BeforeBatch(batch = DAY_BATCH)
    fun setDaytime(level: ServerLevel) {
        level.dayTime = DAY_TEST_TIME
        level.updateSkyBrightness()
    }

    @AfterBatch(batch = DAY_BATCH)
    fun restoreAfterDaytime(level: ServerLevel) {
        level.dayTime = DEFAULT_DAY_TIME
        level.updateSkyBrightness()
    }

    @BeforeBatch(batch = NIGHT_BATCH)
    fun setNighttime(level: ServerLevel) {
        level.dayTime = NIGHT_TEST_TIME
        level.updateSkyBrightness()
    }

    @AfterBatch(batch = NIGHT_BATCH)
    fun restoreAfterNighttime(level: ServerLevel) {
        level.dayTime = DEFAULT_DAY_TIME
        level.updateSkyBrightness()
    }

    companion object {
        const val DAY_BATCH = "sentry_day"
        const val NIGHT_BATCH = "sentry_night"
        private const val DAY_TEST_TIME = 1_000L
        private const val NIGHT_TEST_TIME = 14_000L
        private const val DEFAULT_DAY_TIME = 6_000L
        private const val MEASURE_TICKS = 40L
        private const val TOLERANCE = 1e-9
    }
}
