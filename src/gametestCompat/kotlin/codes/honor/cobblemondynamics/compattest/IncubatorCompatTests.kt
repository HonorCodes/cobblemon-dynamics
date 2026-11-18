package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.nbp.cobblemon_incubator.blockentity.EggIncubatorBlockEntity
import com.nbp.cobblemon_incubator.config.IncubatorConfig
import com.nbp.cobblemon_incubator.util.CobbreedingCompat
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Stoker on the real Egg Incubator of Cobblemon Incubator 1.1.1, with the eggs of Cobbreeding 2.4.1 (CD-FUEL-11). On each of its
 * ticks the incubator takes `incubationSpeed()` off the timer of the egg in its input slot: its base speed, or the upgraded speed while
 * a Speed Upgrade sits in an upgrade slot, however many it holds. An Analyse Upgrade holds the egg, which then counts nothing down. Each
 * egg here has a timer of 1,000,000, so none hatches during a test.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class IncubatorCompatTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val incubatorRel = BlockPos(7, 2, 5)

    /** An unworked incubator with a Speed Upgrade of its own, which gives the speed of one Speed Upgrade. No Stoker claims it. */
    private val referenceRel = BlockPos(11, 2, 5)

    /**
     * CD-FUEL-11: a Charmander at an Egg Incubator with an egg and no Speed Upgrade, where the config turns Speed Upgrades on. While it
     * Works, the incubator reads the speed of an incubator with one Speed Upgrade, and on each tick after a worked tick the egg's timer
     * drops by that speed, where it drops by the base speed on any other tick. After 20 worked ticks a Speed Upgrade goes in: the worked
     * incubator with its upgrade reads the same speed on that tick and after it, and the Charmander stops Working on the next tick,
     * because an incubator with a Speed Upgrade has no demand.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = WARM_TIMEOUT_TICKS)
    fun cdFuel11WarmedIncubatorRunsAtOneSpeedUpgrade(helper: GameTestHelper) {
        assertActive(helper)
        helper.assertTrue(IncubatorConfig.speedUpgradeEnabled, "the incubator's config turns Speed Upgrades off")
        val base = IncubatorConfig.baseSpeed
        val incubator = incubator(helper, incubatorRel)
        val reference = incubator(helper, referenceRel)
        reference.setItem(EggIncubatorBlockEntity.SLOT_UPGRADE_START, ItemStack(item(SPEED_UPGRADE)))
        val one = reference.incubationSpeed()
        helper.assertTrue(one > base, "an incubator with a Speed Upgrade reads $one, and its base speed is $base")
        helper.assertTrue(incubator.incubationSpeed() == base, "the unworked incubator reads ${incubator.incubationSpeed()}, not $base")
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        // The first call of onEachTick runs in the tick of this setup, before the incubator's first tick, so it only takes the timer.
        var last: Int? = null
        var workedLastTick = false
        var worked = 0
        var upgradeAt = -1L
        failAtTimeout(helper, WARM_TIMEOUT_TICKS, charmander) {
            "after $worked worked ticks; the incubator reads ${incubator.incubationSpeed()}"
        }
        helper.onEachTick {
            val timer = timer(incubator)
            val previous = last
            last = timer
            if (previous == null) return@onEachTick
            val step = previous - timer
            val expected = if (workedLastTick || upgradeAt >= 0) one else base
            helper.assertTrue(step == expected, "CD-FUEL-11: the egg's timer dropped by $step on a tick, not $expected")
            if (upgradeAt < 0) {
                workedLastTick = isWorkingOn(helper, charmander, incubator)
                if (!workedLastTick) return@onEachTick
                val speed = incubator.incubationSpeed()
                helper.assertTrue(speed == one, "CD-FUEL-11: the worked incubator reads $speed, not $one, the speed of one Speed Upgrade")
                if (++worked < 20) return@onEachTick
                incubator.setItem(EggIncubatorBlockEntity.SLOT_UPGRADE_START, ItemStack(item(SPEED_UPGRADE)))
                val upgradedSpeed = incubator.incubationSpeed()
                helper.assertTrue(
                    upgradedSpeed == one,
                    "CD-FUEL-11: the worked incubator with a Speed Upgrade reads $upgradedSpeed, not $one",
                )
                upgradeAt = helper.tick
                return@onEachTick
            }
            val speed = incubator.incubationSpeed()
            helper.assertTrue(speed == one, "CD-FUEL-11: the incubator with a Speed Upgrade reads $speed, not $one")
            val status = TestKit.workerOf(helper, charmander)?.status
            helper.assertTrue(status != WorkerStatus.WORKING, "CD-FUEL-11: the Stoker Works an incubator that has its own Speed Upgrade")
            if (helper.tick - upgradeAt >= 40) helper.succeed()
        }
    }

    /**
     * CD-FUEL-11: an Analyse Upgrade holds the egg, so the incubator counts nothing down and the Stoker has no demand there. The engine
     * lets a Stoker claim an incubator with no demand and wait in Standby, as at a furnace, so the Charmander never Works it in 300
     * ticks, while the egg's timer stays. Then the upgrade comes out, and the Charmander Works the incubator, which shows that it was
     * in reach.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = ANALYSE_TIMEOUT_TICKS)
    fun cdFuel11AnalyseUpgradeLeavesNoDemand(helper: GameTestHelper) {
        assertActive(helper)
        helper.assertTrue(IncubatorConfig.analyseUpgradeEnabled, "the incubator's config turns Analyse Upgrades off")
        val incubator = incubator(helper, incubatorRel)
        incubator.setItem(EggIncubatorBlockEntity.SLOT_UPGRADE_START, ItemStack(item(ANALYSE_UPGRADE)))
        val start = timer(incubator)
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var removed = false
        failAtTimeout(helper, ANALYSE_TIMEOUT_TICKS, charmander) { "the Analyse Upgrade came out: $removed" }
        helper.onEachTick {
            val working = isWorkingOn(helper, charmander, incubator)
            if (removed) {
                if (working) helper.succeed()
                return@onEachTick
            }
            helper.assertFalse(working, "CD-FUEL-11: the Charmander Works an incubator whose Analyse Upgrade holds the egg")
            helper.assertTrue(timer(incubator) == start, "the held egg's timer went from $start to ${timer(incubator)}")
            if (helper.tick < 300) return@onEachTick
            incubator.setItem(EggIncubatorBlockEntity.SLOT_UPGRADE_START, ItemStack.EMPTY)
            removed = true
        }
    }

    /** Fails 10 ticks before [timeout] with what the worker shows, so that a stalled test says where it stopped. */
    private fun failAtTimeout(helper: GameTestHelper, timeout: Int, entity: PokemonEntity, progress: () -> String) {
        helper.runAtTickTime(timeout - 10L) {
            val worker = TestKit.workerOf(helper, entity)
            helper.fail(
                "the Charmander is ${worker?.status} at ${TestKit.relative(helper, entity.blockPosition())}, claim " +
                    "${TestKit.claimOf(helper, entity)?.key}; ${progress()}",
            )
        }
    }

    private fun assertActive(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.EGG_INCUBATOR), "the Egg Incubator mixin did not apply")
        helper.assertTrue(RoleRegistry.current.get(ROLE) != null, "the role $ROLE is not active")
    }

    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** An Egg Incubator at [rel] with a Charmander egg in its input slot, made by the incubator's own egg factory. */
    private fun incubator(helper: GameTestHelper, rel: BlockPos): EggIncubatorBlockEntity {
        val id = ResourceLocation.fromNamespaceAndPath("cobblemon_incubator", "egg_incubator")
        helper.setBlock(rel, BuiltInRegistries.BLOCK.getOptional(id).orElseThrow { GameTestAssertException("no block $id is registered") })
        val incubator = helper.getBlockEntity<EggIncubatorBlockEntity>(rel)
        val egg = CobbreedingCompat.createFusedEgg(PokemonProperties.parse("charmander"), EGG_TIMER)
            ?: throw GameTestAssertException("Cobbreeding made no egg")
        helper.assertTrue(CobbreedingCompat.isEgg(egg), "the incubator does not take $egg as an egg")
        incubator.setItem(EggIncubatorBlockEntity.SLOT_INPUT, egg)
        return incubator
    }

    /** The timer of the egg in the input slot, which the incubator counts down. */
    private fun timer(incubator: EggIncubatorBlockEntity): Int =
        CobbreedingCompat.getTimer(incubator.getItem(EggIncubatorBlockEntity.SLOT_INPUT))
            ?: throw GameTestAssertException(
                "the incubator's input holds no egg with a timer: ${incubator.getItem(EggIncubatorBlockEntity.SLOT_INPUT)}",
            )

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, incubator: EggIncubatorBlockEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, incubator.blockPos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private fun item(id: ResourceLocation): Item =
        BuiltInRegistries.ITEM.getOptional(id).orElseThrow { GameTestAssertException("no item $id is registered") }

    private companion object {
        const val EGG_TIMER = 1_000_000
        const val WARM_TIMEOUT_TICKS = 800
        const val ANALYSE_TIMEOUT_TICKS = 900

        val ROLE: ResourceLocation = id("stoker_cobblemon_incubator")
        val SPEED_UPGRADE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_incubator", "speed_upgrade")
        val ANALYSE_UPGRADE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_incubator", "analyse_upgrade")
    }
}
