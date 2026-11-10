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

        val recorder = RecordingKeyframe()
    }
}
