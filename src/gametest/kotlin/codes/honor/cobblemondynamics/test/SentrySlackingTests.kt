package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.stamina.StaminaMath
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobSpawnType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/** Cross-slice Sentry + Slacking Off lifecycle and machine-capped drain scaling (CD-SNT-04, CD-PRG-10, CD-PRG-16). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class SentrySlackingTests {
    private var savedSettings: EngineSettings? = null

    private fun isNaturalZombieCancelled(helper: GameTestHelper, pos: BlockPos): Boolean {
        val zombie = EntityType.ZOMBIE.create(helper.level) ?: error("failed to create zombie")
        val event = FinalizeSpawnEvent(
            zombie,
            helper.level,
            pos.x + 0.5,
            pos.y.toDouble(),
            pos.z + 0.5,
            helper.level.getCurrentDifficultyAt(pos),
            MobSpawnType.NATURAL,
            null,
            null,
        )
        NeoForge.EVENT_BUS.post(event)
        return event.isSpawnCancelled
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 700, batch = SENTRY_SLACK_BATCH)
    fun cdSnt04Prg16SentrySlackingLiftsWatchRetainsClaimAndResumes(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val pokemon = TestKit.pokemon("umbreon level=10 nature=relaxed").also {
            it.shiny = true
            it.setFriendship(0, true)
        }
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for umbreon")
        val umbreon = TestKit.pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no entity for umbreon")
        val inside = pasture.blockPos.offset(3, 1, 0)

        var workingStartStamina = -1.0
        var workingMeasuredTicks = 0
        var verifiedScaledDrain = false
        var slacked = false
        var watchLiftedDuringSlack = false
        var slackStartStamina = -1.0

        helper.onEachTick {
            val worker = TestKit.workerOf(helper, umbreon) ?: return@onEachTick
            val roleId = TestKit.engine(helper).roleOf(umbreon.pokemon.uuid)?.definition?.id

            if (!slacked && worker.status == WorkerStatus.WORKING && roleId == id("sentry")) {
                helper.assertTrue(isNaturalZombieCancelled(helper, inside), "CD-SNT-02: Working Sentry did not cancel zombie spawn")
                if (workingStartStamina < 0.0) {
                    workingStartStamina = worker.stamina.fraction
                } else {
                    workingMeasuredTicks++
                    if (workingMeasuredTicks == 20) {
                        val settings = WorkEngineSettings.current
                        val expected = 20.0 * (1.0 / settings.shinyMultiplier) / StaminaMath.capacityTicks(10, settings)
                        val actual = workingStartStamina - worker.stamina.fraction
                        helper.assertTrue(
                            abs(actual - expected) <= 1e-9,
                            "CD-PRG-10: Shiny Sentry drained $actual over 20 ticks, expected $expected",
                        )
                        verifiedScaledDrain = true
                    }
                }
            } else if (worker.status == WorkerStatus.SLACKING) {
                if (!slacked) {
                    slacked = true
                    slackStartStamina = worker.stamina.fraction
                }
                helper.assertTrue(
                    TestKit.claimOf(helper, umbreon)?.kind == ClaimKind.WORK,
                    "CD-PRG-16: Sentry lost WORK claim during SLACKING",
                )
                helper.assertTrue(
                    abs(worker.stamina.fraction - slackStartStamina) < 1e-12,
                    "CD-PRG-16: Sentry stamina changed during SLACKING: ${worker.stamina.fraction} vs $slackStartStamina",
                )
                if (!isNaturalZombieCancelled(helper, inside)) watchLiftedDuringSlack = true
            } else if (slacked && worker.status == WorkerStatus.WORKING && roleId == id("sentry")) {
                helper.assertTrue(verifiedScaledDrain, "Sentry slacked before 20-tick drain check")
                helper.assertTrue(watchLiftedDuringSlack, "CD-SNT-04: SentryWatch never lifted during SLACKING")
                helper.assertTrue(
                    isNaturalZombieCancelled(helper, inside),
                    "CD-PRG-16: SentryWatch did not resume after SLACKING ended",
                )
                helper.succeed()
            }
        }
    }

    @BeforeBatch(batch = SENTRY_SLACK_BATCH)
    fun enableSentrySlack(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        savedSettings = current
        WorkEngineSettings.current = current.copy(
            baseSlackChance = 1.0,
            slackCheckTicks = 100,
            slackDurationTicks = 40,
        )
    }

    @AfterBatch(batch = SENTRY_SLACK_BATCH)
    fun restoreSentrySlack(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = savedSettings ?: EngineSettings.DEFAULT.copy(baseSlackChance = 0.0)
        savedSettings = null
    }

    private companion object {
        const val SENTRY_SLACK_BATCH = "sentry_slack"
    }
}
