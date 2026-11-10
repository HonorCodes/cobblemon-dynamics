package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.capability.FePushHandler
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The Generator through NeoForge's real energy capability, into the test battery and AE2's blocks. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class GeneratorTests {
    /** round(10 × 500^(49 ÷ 99)): the Lv50 rate of the default `generator.json` (CD-GEN-02). */
    private val lv50Rate = 217

    private val batteryRel = BlockPos(7, 1, 3)

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, battery: TestBatteryBlockEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, battery.blockPos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /** CD-GEN-02 and CD-GEN-05 on the real capability: one offer per tick, each exactly the rate. */
    private fun assertOffersAtRate(helper: GameTestHelper, battery: TestBatteryBlockEntity) {
        val wrong = battery.offers.firstOrNull { it.offered != lv50Rate }
        helper.assertTrue(wrong == null, "an offer was not the Lv50 rate of $lv50Rate FE: $wrong")
        val twice = battery.offers.groupBy { it.tick }.filterValues { it.size > 1 }.keys
        helper.assertTrue(twice.isEmpty(), "the battery got more than one offer in ticks $twice")
    }

    /** The handler checks each of the six faces, as the scan and the Work Leash call it. */
    @GameTest(template = "floor_9x5x9")
    fun cdGen01CapabilityTargets(helper: GameTestHelper) {
        TestKit.testBattery(helper, BlockPos(2, 1, 2))
        TestKit.testBattery(helper, BlockPos(6, 1, 2), refuse = Direction.entries.toSet())
        helper.setBlock(BlockPos(4, 1, 6), TestKit.ae2Block("ae2:energy_acceptor"))
        val level = helper.level
        fun isTarget(rel: BlockPos): Boolean {
            val pos = helper.absolutePos(rel)
            return FePushHandler.matches(level, pos, level.getBlockState(pos), level.getBlockEntity(pos)) &&
                FePushHandler.stillValid(level, pos)
        }
        helper.assertTrue(isTarget(BlockPos(2, 1, 2)), "the test battery is not a Generator target")
        helper.assertTrue(isTarget(BlockPos(4, 1, 6)), "the AE2 Energy Acceptor is not a Generator target")
        helper.assertFalse(isTarget(BlockPos(6, 1, 2)), "a battery whose capability cannot receive on any face is a Generator target")
        helper.assertFalse(isTarget(BlockPos(4, 1, 4)), "the stone floor is a Generator target")
        val generator = RoleRegistry.current.get(id("generator"))
        helper.assertTrue(generator?.handler === FePushHandler, "the Generator role is not active with the FE handler: $generator")
        helper.succeed()
    }

    /**
     * A Lv50 Pikachu fills a 2,000 FE battery: nine offers of 217 FE, then one of which it takes 47. With the battery full, the
     * worker waits in Standby, keeps its Work Stamina and pushes nothing. Once the battery empties, its next offer is 217 FE again.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdGen06FullBatteryMeansStandby(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val battery = TestKit.testBattery(helper, batteryRel, capacity = 2_000)
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=50")
        var step = "fill"
        var since = -1L
        var stamina = 0.0
        var offers = 0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, pikachu) ?: return@onEachTick
            assertOffersAtRate(helper, battery)
            when (step) {
                "fill" -> if (battery.stored == 2_000) {
                    helper.assertTrue(
                        battery.offers.last().accepted == 2_000 % lv50Rate,
                        "the last offer before full: ${battery.offers.last()}",
                    )
                    step = "full"
                    since = helper.tick
                }

                "full" -> if (worker.status == WorkerStatus.STANDBY) {
                    step = "standby"
                    since = helper.tick
                    stamina = worker.stamina.fraction
                    offers = battery.offers.size
                } else {
                    helper.assertTrue(helper.tick - since < 20, "the Pikachu is ${worker.status} 20 ticks after its battery filled")
                }

                "standby" -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "the Pikachu left Standby at a full battery: ${worker.status}")
                    helper.assertTrue(worker.stamina.fraction >= stamina, "Work Stamina drained in Standby: ${worker.stamina}")
                    helper.assertTrue(battery.offers.size == offers, "the full battery got ${battery.offers.size - offers} offers")
                    stamina = worker.stamina.fraction
                    if (helper.tick - since >= 40) {
                        battery.drain()
                        step = "drained"
                        since = helper.tick
                    }
                }

                "drained" -> if (battery.offers.size > offers) {
                    val first = battery.offers[offers]
                    helper.assertTrue(first.accepted == lv50Rate, "the first offer after the drain was $first")
                    helper.assertTrue(isWorkingOn(helper, pikachu, battery), "the Pikachu pushes without Working: ${worker.status}")
                    helper.succeed()
                } else {
                    helper.assertTrue(helper.tick - since <= 20, "no offer 20 ticks after the battery emptied")
                }
            }
        }
    }

    /**
     * The row's literal case: a target bound to Pikachu refuses a second binding and names Pikachu. The bound Pikachu works the
     * battery through the real capability at its curve's rate.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdLsh05PikachuOnBattery(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val battery = TestKit.testBattery(helper, batteryRel)
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=50")
        val voltorb = TestKit.tether(helper, pasture, owner, "voltorb level=20")
        val bound = TestKit.bind(helper, owner, pikachu, batteryRel)
        helper.assertTrue(bound?.target == battery.blockPos, "no binding to the battery: $bound")
        TestKit.leash(owner)
        TestKit.clickEntity(owner, voltorb)
        TestKit.clickBlock(helper, owner, batteryRel)
        val reply = Feedback.recorded[owner.uuid].orEmpty().lastOrNull()?.string
        helper.assertTrue(reply == "Already assigned to Pikachu", "the second binding answered '$reply'")
        val stored = Cobblemon.storage.getPC(owner)[voltorb.pokemon.uuid]
        helper.assertTrue(stored != null && BindingStore.read(stored) == null, "the Voltorb got a binding to Pikachu's battery")
        helper.succeedWhen {
            assertOffersAtRate(helper, battery)
            helper.assertTrue(TestKit.claimOf(helper, voltorb) == null, "the Voltorb holds a claim")
            helper.assertTrue(TestKit.workerOf(helper, pikachu)?.leashed == true, "the bound Pikachu is not leashed")
            helper.assertTrue(isWorkingOn(helper, pikachu, battery), "the bound Pikachu does not work its battery")
            helper.assertTrue(battery.offers.size >= 20, "the battery got ${battery.offers.size} offers")
            helper.assertTrue(battery.stored == lv50Rate * battery.offers.size, "the battery holds ${battery.stored} FE")
        }
    }
}
