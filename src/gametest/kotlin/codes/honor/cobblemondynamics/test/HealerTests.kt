package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.fx.HealEffect
import codes.honor.cobblemondynamics.heal.HealTransaction
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

/** Healers through the real Brain and Cobblemon's action effects. The target rules have unit tests (HealTargetsTest). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class HealerTests {
    private class Scene(val healer: PokemonEntity, val ally: PokemonEntity)

    /** A Work Mode pasture at (7,1,7) with an unleashed Lv50 Clefairy and a Lv10 Machop. */
    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        return Scene(TestKit.tether(helper, pasture, owner, "clefairy level=50"), TestKit.tether(helper, pasture, owner, "machop level=10"))
    }

    /** The distance from the healer to the ally's body, which is what the heal range measures. */
    private fun reach(scene: Scene): Double {
        val here = scene.healer.position()
        return here.distanceTo(HealTransaction.nearestPoint(scene.ally.boundingBox, here))
    }

    /**
     * An unleashed Clefairy heals a Tired Machop that sleeps on a Hay Bed 7 blocks away (CD-HEAL-01). It takes a heal claim on the
     * Machop, walks to it through the Brain and heals it within 2 blocks of its body (CD-HEAL-10). The Clefairy pays 25 points and
     * the Machop is full (CD-HEAL-04, CD-HEAL-05), and 1 tick later the Machop is up and its bed claim is gone (CD-BED-12).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 900)
    fun cdHeal01HealerHealsASleepingAlly(helper: GameTestHelper) {
        val scene = scene(helper)
        val engine = TestKit.engine(helper)
        val healerId = scene.healer.pokemon.uuid
        val allyId = scene.ally.pokemon.uuid
        var tiredAt = -1L
        var walked = false
        var healedAt = -1L
        helper.onEachTick {
            val healer = engine.worker(healerId) ?: return@onEachTick
            val ally = engine.worker(allyId) ?: return@onEachTick
            if (tiredAt < 0) {
                helper.setBlock(BlockPos(2, 2, 2), DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
                TestKit.moveTo(helper, scene.ally, BlockPos(2, 2, 3))
                ally.stamina.fraction = 0.0
                ally.tired = true
                tiredAt = helper.tick
                return@onEachTick
            }
            val claim = engine.claims.claimOf(healerId)
            if (healedAt < 0) {
                if (claim != null) {
                    helper.assertTrue(claim.kind == ClaimKind.HEAL && claim.key == TargetKey.Entity(allyId), "the healer holds $claim")
                    helper.assertTrue(healer.status == WorkerStatus.HEALING, "the healer with a heal claim is ${healer.status}")
                    if (reach(scene) > HealTransaction.RANGE + 1.0) walked = true
                }
                if (ally.tired) return@onEachTick
                healedAt = helper.tick
                helper.assertTrue(walked, "the healer never walked to the ally")
                helper.assertTrue(helper.tick - tiredAt <= 600, "CD-HEAL-01: the heal came ${helper.tick - tiredAt} ticks after Tired")
                helper.assertTrue(abs(healer.stamina.percent - 75.0) < 0.1, "CD-HEAL-04: the healer reads ${healer.stamina.percent}%")
                helper.assertTrue(ally.stamina.percent == 100.0, "CD-HEAL-05: the ally reads ${ally.stamina.percent}%")
                helper.assertTrue(reach(scene) <= HealTransaction.RANGE + 0.5, "CD-HEAL-10: the heal came from ${reach(scene)} blocks")
                helper.assertTrue(claim == null && healer.status == WorkerStatus.IDLE, "after its heal the healer is ${healer.status}")
                return@onEachTick
            }
            val allyClaim = engine.claims.claimOf(allyId)
            helper.assertTrue(allyClaim == null, "CD-BED-12: the healed ally still holds $allyClaim")
            helper.assertTrue(ally.status == WorkerStatus.IDLE, "the healed ally is ${ally.status}")
            helper.succeed()
        }
    }

    /** Makes [entity]'s worker Tired at 0% where it stands, so with no bed it rests in place there. */
    private fun tire(helper: GameTestHelper, entity: PokemonEntity) {
        val worker = TestKit.workerOf(helper, entity) ?: throw GameTestAssertException("${entity.pokemon.species.name} is no worker")
        worker.stamina.fraction = 0.0
        worker.tired = true
    }

    private fun workersReady(helper: GameTestHelper, vararg entities: PokemonEntity): Boolean =
        entities.all { TestKit.workerOf(helper, it) != null }

    /**
     * A Lv50 Clefairy shares its pasture with a Tired Azumarill (Water and Fairy), and a Tired Machop sits in a second Work Mode
     * pasture 3 blocks away. For 400 ticks the Clefairy takes no heal claim and both stay Tired (CD-HEAL-06). Then a Machop of the
     * Clefairy's own pasture gets Tired, and the Clefairy takes a heal claim on it within one scan, which shows that it heals.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 700)
    fun cdHeal06OnlyNonFairyAlliesOfTheHealersPasture(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val home = TestKit.placePasture(helper, BlockPos(5, 1, 7), owner)
        val other = TestKit.placePasture(helper, BlockPos(8, 1, 7), owner)
        TestKit.workMode(home, true)
        TestKit.workMode(other, true)
        val healer = TestKit.tether(helper, home, owner, "clefairy level=50")
        val azumarill = TestKit.tether(helper, home, owner, "azumarill level=20")
        val neighbour = TestKit.tether(helper, other, owner, "machop level=10")
        val engine = TestKit.engine(helper)
        var tiredAt = -1L
        var ally: PokemonEntity? = null
        helper.onEachTick {
            if (tiredAt < 0) {
                if (!workersReady(helper, healer, azumarill, neighbour)) return@onEachTick
                tire(helper, azumarill)
                tire(helper, neighbour)
                tiredAt = helper.tick
                return@onEachTick
            }
            val claim = engine.claims.claimOf(healer.pokemon.uuid)
            val waited = helper.tick - tiredAt
            if (waited <= QUIET_TICKS) {
                helper.assertTrue(claim == null, "CD-HEAL-06: the healer took $claim")
                for ((what, entity) in listOf("the Azumarill" to azumarill, "the Machop of the other pasture" to neighbour)) {
                    val worker = TestKit.workerOf(helper, entity)
                    helper.assertTrue(worker?.tired == true, "CD-HEAL-06: $what is ${worker?.stamina}")
                }
                if (waited == QUIET_TICKS.toLong()) ally = TestKit.tether(helper, home, owner, "machop level=10")
                return@onEachTick
            }
            val local = ally ?: return@onEachTick
            val worker = TestKit.workerOf(helper, local) ?: return@onEachTick
            if (!worker.tired) {
                tire(helper, local)
                return@onEachTick
            }
            if (claim == null) return@onEachTick
            val onLocal = claim.kind == ClaimKind.HEAL && claim.key == TargetKey.Entity(local.pokemon.uuid)
            helper.assertTrue(onLocal, "the healer took $claim")
            helper.succeed()
        }
    }

    /**
     * The healer is held still until it takes a heal claim. Tired allies rest 3 and 6 blocks from it, and an ally at 10% that is not
     * Tired is held 1 block from it. The healer goes to the Tired ally at 3 blocks (CD-HEAL-07).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 300)
    fun cdHeal07NearestTiredAllyFirst(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val healer = TestKit.tether(helper, pasture, owner, "clefairy level=50")
        val low = TestKit.tether(helper, pasture, owner, "machop level=10")
        val near = TestKit.tether(helper, pasture, owner, "machop level=10")
        val far = TestKit.tether(helper, pasture, owner, "machop level=10")
        val healerAt = BlockPos(7, 2, 3)
        val lowAt = BlockPos(8, 2, 3)
        val engine = TestKit.engine(helper)
        var placed = false
        helper.onEachTick {
            if (!placed) {
                if (!workersReady(helper, healer, low, near, far)) return@onEachTick
                TestKit.moveTo(helper, near, BlockPos(10, 2, 3))
                TestKit.moveTo(helper, far, BlockPos(1, 2, 3))
                tire(helper, near)
                tire(helper, far)
                TestKit.workerOf(helper, low)!!.stamina.fraction = 0.10
                placed = true
            }
            val claim = engine.claims.claimOf(healer.pokemon.uuid)
            if (claim == null) {
                TestKit.moveTo(helper, healer, healerAt)
                TestKit.moveTo(helper, low, lowAt)
                return@onEachTick
            }
            val distances = listOf("the 10% ally" to low, "the Tired ally at 3" to near, "the Tired ally at 6" to far)
                .joinToString { (what, it) -> "$what ${"%.2f".format(it.position().distanceTo(healer.position()))}" }
            val target = (claim.key as? TargetKey.Entity)?.uuid
            helper.assertTrue(target == near.pokemon.uuid, "CD-HEAL-07: the healer went to $target; distances: $distances")
            helper.succeed()
        }
    }

    /**
     * Two Lv50 Clefairy and one Tired Machop. On no tick do two heal claims name the Machop, and once it is healed exactly one healer
     * paid for it: one reads 75% and the other 100% (CD-HEAL-09).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 900)
    fun cdHeal09OneHealerPerAlly(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val healers = List(2) { TestKit.tether(helper, pasture, owner, "clefairy level=50") }
        val ally = TestKit.tether(helper, pasture, owner, "machop level=10")
        val engine = TestKit.engine(helper)
        val allyKey = TargetKey.Entity(ally.pokemon.uuid)
        var spent = false
        helper.onEachTick {
            if (!spent) {
                if (!workersReady(helper, ally, *healers.toTypedArray())) return@onEachTick
                TestKit.moveTo(helper, ally, BlockPos(2, 2, 2))
                tire(helper, ally)
                spent = true
                return@onEachTick
            }
            val heals = engine.claims.all().filter { it.key == allyKey && it.kind == ClaimKind.HEAL }
            helper.assertTrue(heals.size <= 1, "CD-HEAL-09: ${heals.size} heal claims name the Machop")
            if (TestKit.workerOf(helper, ally)!!.tired) return@onEachTick
            val percents = healers.map { TestKit.workerOf(helper, it)!!.stamina.percent }.sorted()
            helper.assertTrue(
                abs(percents[0] - 75.0) < 0.1 && percents[1] == 100.0,
                "CD-HEAL-09: after the heal the healers read $percents",
            )
            helper.succeed()
        }
    }

    /**
     * A heal ends at no cost when its target stops qualifying while the healer walks to it (CD-HEAL-11). First, a Tired Machop that
     * sleeps on a Hay Bed reaches 100% there. Then a second Tired Machop leaves the pasture. Each time the heal claim ends, no heal
     * completes and the healer keeps 100%.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdHeal11HealCancelsAtNoCost(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val healer = TestKit.tether(helper, pasture, owner, "clefairy level=50")
        val sleeper = TestKit.tether(helper, pasture, owner, "machop level=10")
        val leaver = TestKit.tether(helper, pasture, owner, "machop level=10")
        helper.setBlock(BlockPos(2, 2, 2), DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val engine = TestKit.engine(helper)
        val healerId = healer.pokemon.uuid
        val far = BlockPos(12, 2, 12)
        val sleeperKey = TargetKey.Entity(sleeper.pokemon.uuid)
        var step = "tire"
        var since = -1L
        helper.runAtTickTime(1_199) {
            val states = listOf(healer, sleeper, leaver).joinToString {
                "${TestKit.workerOf(helper, it)?.status} ${TestKit.claimOf(helper, it)}"
            }
            helper.fail("stuck at '$step': $states")
        }
        helper.onEachTick {
            val worker = engine.worker(healerId) ?: return@onEachTick
            helper.assertTrue(worker.stamina.fraction == 1.0, "CD-HEAL-11: the healer paid for a heal: ${worker.stamina} at '$step'")
            val claim = engine.claims.claimOf(healerId)
            when (step) {
                "tire" -> {
                    if (!workersReady(helper, sleeper, leaver)) return@onEachTick
                    // The healer may take the Machop only once it sleeps, so the heal cannot end before the test is ready for it.
                    worker.skipUntil[sleeperKey] = Long.MAX_VALUE
                    TestKit.moveTo(helper, sleeper, BlockPos(2, 2, 3))
                    tire(helper, sleeper)
                    step = "asleep"
                }

                "asleep" -> if (TestKit.workerOf(helper, sleeper)!!.status == WorkerStatus.SLEEPING) {
                    worker.skipUntil.remove(sleeperKey)
                    step = "full"
                }

                "full" -> {
                    if (claim?.key != sleeperKey) {
                        TestKit.moveTo(helper, healer, far)
                        return@onEachTick
                    }
                    helper.assertTrue(TestKit.workerOf(helper, sleeper)!!.status == WorkerStatus.SLEEPING, "the Machop left its bed")
                    // One tick on the bed takes it to 100%.
                    TestKit.workerOf(helper, sleeper)!!.stamina.fraction = 1.0 - 1e-6
                    since = helper.tick
                    step = "cancelled"
                }

                "cancelled" -> if (claim == null) {
                    helper.assertFalse(TestKit.workerOf(helper, sleeper)!!.tired, "the heal ended while the Machop is still Tired")
                    helper.assertTrue(
                        helper.tick - since <= 2,
                        "CD-HEAL-11: the heal ended ${helper.tick - since} ticks after the ally was full",
                    )
                    TestKit.moveTo(helper, leaver, BlockPos(2, 2, 12))
                    tire(helper, leaver)
                    step = "leave"
                } else {
                    helper.assertTrue(helper.tick - since <= 2, "CD-HEAL-11: the heal on a full ally goes on: $claim")
                }

                "leave" -> {
                    if (claim?.key != TargetKey.Entity(leaver.pokemon.uuid)) {
                        TestKit.moveTo(helper, healer, far)
                        return@onEachTick
                    }
                    pasture.releasePokemon(leaver.pokemon.uuid)
                    since = helper.tick
                    step = "left"
                }

                "left" -> if (claim == null) {
                    helper.assertTrue(worker.status == WorkerStatus.IDLE, "after the cancelled heal the healer is ${worker.status}")
                    if (helper.tick - since >= 40) helper.succeed()
                } else {
                    helper.assertTrue(helper.tick - since <= 2, "CD-HEAL-11: the heal on an ally that left goes on: $claim")
                }
            }
        }
    }

    /**
     * A heal that starts at exactly 25% leaves the healer at 0%. The first Lv50 Clefairy heals a Machop at 10% with one free Hay Bed
     * in the work area, and on the tick of its heal it is in ToBed on that bed. Then a second Clefairy heals a second Machop at 10%,
     * with the only bed taken, and on the tick of its heal it rests in place (CD-HEAL-17). The test holds each healer at 25% until its
     * heal, so the recovery of its walk does not keep it above 0%.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdHeal17SpentHealerGoesToBedOrRestsInPlace(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val bedRel = BlockPos(12, 2, 12)
        helper.setBlock(bedRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val bedKey = TestKit.keyOf(helper, helper.absolutePos(bedRel))
        val engine = TestKit.engine(helper)
        val first = TestKit.tether(helper, pasture, owner, "clefairy level=50")
        val firstAlly = TestKit.tether(helper, pasture, owner, "machop level=10")
        var second: PokemonEntity? = null
        var secondAlly: PokemonEntity? = null
        var step = "first"
        helper.onEachTick {
            when (step) {
                "first" -> {
                    if (!holdForHeal(helper, first, firstAlly)) return@onEachTick
                    val healer = TestKit.workerOf(helper, first)!!
                    val claim = engine.claims.claimOf(first.pokemon.uuid)
                    helper.assertTrue(healer.tired, "the first healer is not Tired after its heal: ${healer.stamina}")
                    helper.assertTrue(
                        healer.status == WorkerStatus.TO_BED,
                        "CD-HEAL-17: the first healer is ${healer.status}, not in ToBed",
                    )
                    helper.assertTrue(claim?.kind == ClaimKind.BED && claim.key == bedKey, "CD-HEAL-17: the first healer holds $claim")
                    second = TestKit.tether(helper, pasture, owner, "clefairy level=50")
                    secondAlly = TestKit.tether(helper, pasture, owner, "machop level=10")
                    step = "second"
                }

                "second" -> {
                    if (!holdForHeal(helper, second!!, secondAlly!!)) return@onEachTick
                    val healer = TestKit.workerOf(helper, second!!)!!
                    val claim = engine.claims.claimOf(second!!.pokemon.uuid)
                    helper.assertTrue(engine.claims.holderOf(bedKey)?.worker == first.pokemon.uuid, "the first healer lost its bed")
                    helper.assertTrue(healer.tired, "the second healer is not Tired after its heal: ${healer.stamina}")
                    val resting = healer.status == WorkerStatus.RESTING_IN_PLACE
                    helper.assertTrue(resting, "CD-HEAL-17: with no free bed the second healer is ${healer.status}")
                    helper.assertTrue(claim == null, "CD-HEAL-17: the second healer holds $claim")
                    helper.succeed()
                }
            }
        }
    }

    /**
     * Holds [healer] at exactly 25% and [ally] at 10% until the heal, then returns true on the tick that the ally is full. The engine
     * heals before its stamina step, so a healer that the test set to 25% after the last tick pays its last 25 points.
     */
    private fun holdForHeal(helper: GameTestHelper, healer: PokemonEntity, ally: PokemonEntity): Boolean {
        val healerWorker = TestKit.workerOf(helper, healer) ?: return false
        val allyWorker = TestKit.workerOf(helper, ally) ?: return false
        if (allyWorker.stamina.fraction == 1.0 && healerWorker.stamina.fraction < 0.25) return true
        healerWorker.stamina.fraction = 0.25
        if (allyWorker.stamina.fraction > 0.10) allyWorker.stamina.fraction = 0.10
        return false
    }

    /**
     * With a `cobblemon:floralhealing` timeline in Cobblemon's action effects, a heal runs it with the healer as the user and the ally
     * as the target (CD-HEAL-16). The batch puts a recording timeline in place, because Cobblemon 1.8.4 has none.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600, batch = FLORAL_BATCH)
    fun cdHeal16FloralHealingRunsWithTheHealerAsUser(helper: GameTestHelper) {
        val scene = scene(helper)
        val engine = TestKit.engine(helper)
        val allyId = scene.ally.pokemon.uuid
        var spent = false
        helper.onEachTick {
            val ally = engine.worker(allyId) ?: return@onEachTick
            if (!spent) {
                ally.stamina.fraction = 0.0
                ally.tired = true
                spent = true
                return@onEachTick
            }
            if (ally.tired) return@onEachTick
            val runs = recorder.runs.toList()
            helper.assertTrue(runs.size == 1, "the timeline ran ${runs.size} times")
            val (users, targets) = runs.single()
            helper.assertTrue(users == listOf(scene.healer), "CD-HEAL-16: the timeline's users are $users")
            helper.assertTrue(targets == listOf(scene.ally), "CD-HEAL-16: the timeline's targets are $targets")
            helper.succeed()
        }
    }

    /** Records the users and targets of each context that runs it. */
    class RecordingKeyframe : ActionEffectKeyframe {
        val runs = mutableListOf<Pair<List<Entity>, List<Entity>>>()

        override fun play(context: ActionEffectContext): CompletableFuture<Unit> {
            val users = context.findOneProvider<UsersProvider>()?.entities.orEmpty()
            val targets = context.findOneProvider<TargetsProvider>()?.entities.orEmpty()
            runs += users to targets
            return CompletableFuture.completedFuture(Unit)
        }
    }

    @BeforeBatch(batch = FLORAL_BATCH)
    fun floralHealing(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        recorder.runs.clear()
        ActionEffects.actionEffects[HealEffect.FLORAL_HEALING] = ActionEffectTimeline(timeline = listOf(recorder))
    }

    @AfterBatch(batch = FLORAL_BATCH)
    fun noFloralHealing(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        ActionEffects.actionEffects.remove(HealEffect.FLORAL_HEALING)
    }

    companion object {
        const val FLORAL_BATCH = "floral_healing"
        private const val QUIET_TICKS = 400

        val recorder = RecordingKeyframe()
    }
}
