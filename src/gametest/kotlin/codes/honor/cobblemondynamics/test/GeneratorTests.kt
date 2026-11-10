package codes.honor.cobblemondynamics.test

import appeng.api.networking.IGrid
import appeng.api.networking.IInWorldGridNodeHost
import appeng.api.networking.energy.IAEPowerStorage
import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.capability.FePushHandler
import codes.honor.cobblemondynamics.handler.capability.NearestFaces
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/** The Generator through NeoForge's real energy capability, into the test battery and AE2's blocks. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class GeneratorTests {
    /** round(10 × 500^(49 ÷ 99)): the Lv50 rate of the default `generator.json` (CD-GEN-02). */
    private val lv50Rate = 217

    /** The ticks of work over which CD-GEN-07 measures the grid's gain. */
    private val sampleTicks = 40

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

    /** An AE2 block at [rel] of [id], with its `facing` property set when [facing] is not null. */
    private fun ae2(helper: GameTestHelper, rel: BlockPos, id: String, facing: Direction? = null): BlockPos {
        var state = TestKit.ae2Block(id).defaultBlockState()
        if (facing != null) {
            val property = state.block.stateDefinition.getProperty("facing") as? DirectionProperty
                ?: throw GameTestAssertException("$id has no facing property")
            state = state.setValue(property, facing)
        }
        helper.setBlock(rel, state)
        return helper.absolutePos(rel)
    }

    /**
     * An AE2 Charger faces south, toward the pasture, and takes FE on every face but its front. A Lv50 Pikachu is held south of it until
     * it claims, so it works from the front, whose face is nearest. The Generator pushes through the nearest face that accepts FE, and
     * the charger's buffer, read through a power face, rises (CD-GEN-04).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdGen04NearestAcceptingFace(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        val charger = ae2(helper, BlockPos(7, 2, 5), "ae2:charger", Direction.SOUTH)
        val level = helper.level
        val front = level.getCapability(Capabilities.EnergyStorage.BLOCK, charger, Direction.SOUTH)
        helper.assertTrue(front == null || !front.canReceive(), "the charger's front takes FE, so the scene proves nothing")
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=50")
        val hold = BlockPos(7, 2, 7)
        var before = -1
        helper.onEachTick {
            val buffer = level.getCapability(Capabilities.EnergyStorage.BLOCK, charger, Direction.UP)?.energyStored
                ?: throw GameTestAssertException("the charger has no FE capability on its top")
            val claim = TestKit.claimOf(helper, pikachu)
            if (claim == null) {
                TestKit.moveTo(helper, pikachu, hold)
                return@onEachTick
            }
            val worker = TestKit.workerOf(helper, pikachu) ?: return@onEachTick
            if (worker.status != WorkerStatus.WORKING) return@onEachTick
            val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
            val nearest = NearestFaces.of(charger, pikachu.position()).first()
            helper.assertTrue(nearest == Direction.SOUTH, "the Pikachu works from $spot, where the nearest face is $nearest, not the front")
            if (before < 0) {
                before = buffer
                return@onEachTick
            }
            if (buffer > before) {
                helper.succeed()
                return@onEachTick
            }
            val worked = helper.tick - worker.statusSince
            helper.assertTrue(worked < 20, "CD-GEN-04: the charger's buffer stays at $buffer FE after $worked ticks of work")
        }
    }

    /**
     * A Lv50 Pikachu works an AE2 Energy Acceptor beside an energy cell, which form one grid. Over 40 ticks of work the grid's stored
     * energy rises by 108.5 ± 1 AE per tick: AE2 turns the Generator's 217 FE per tick into AE at its own ratio of 2 FE to 1 AE
     * (CD-GEN-07).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdGen07Ae2RatioAtLv50(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        val acceptor = ae2(helper, BlockPos(7, 2, 5), "ae2:energy_acceptor")
        val cell = ae2(helper, BlockPos(7, 2, 4), "ae2:energy_cell")
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=50")
        var start = -1L
        val stored = ArrayList<Double>()
        helper.onEachTick {
            if (!onOneGrid(helper, acceptor, cell)) return@onEachTick
            val energy = storedAe(helper, cell)
            val worker = TestKit.workerOf(helper, pikachu) ?: return@onEachTick
            val claim = TestKit.claimOf(helper, pikachu)
            if (claim?.key != TestKit.keyOf(helper, acceptor) || worker.status != WorkerStatus.WORKING) return@onEachTick
            if (start < 0) start = helper.tick
            stored += energy
            if (stored.size <= sampleTicks) return@onEachTick
            val perTick = (stored.last() - stored.first()) / sampleTicks
            val steps = stored.zipWithNext { a, b -> b - a }
            helper.assertTrue(abs(perTick - lv50Rate / 2.0) <= 1.0, "CD-GEN-07: the grid gains $perTick AE per tick; steps $steps")
            helper.succeed()
        }
    }

    private fun gridOf(helper: GameTestHelper, pos: BlockPos): IGrid? {
        val host = helper.level.getBlockEntity(pos) as? IInWorldGridNodeHost ?: return null
        val node = host.getGridNode(null) ?: Direction.entries.firstNotNullOfOrNull { host.getGridNode(it) } ?: return null
        return node.grid
    }

    private fun onOneGrid(helper: GameTestHelper, a: BlockPos, b: BlockPos): Boolean {
        val grid = gridOf(helper, a) ?: return false
        return grid === gridOf(helper, b)
    }

    /**
     * The stored energy of the grid, which is its one energy cell's. AE2's `IEnergyService.getStoredPower` reads a total that AE2
     * recomputes only every 90 ticks or on a storage event, so the test reads the cell, the grid's only power storage, directly.
     */
    private fun storedAe(helper: GameTestHelper, cell: BlockPos): Double =
        (helper.level.getBlockEntity(cell) as? IAEPowerStorage)?.aeCurrentPower ?: throw GameTestAssertException("no AE2 energy cell")

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
