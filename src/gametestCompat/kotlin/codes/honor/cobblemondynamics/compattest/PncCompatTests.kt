package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.compat.pneumaticcraft.CompressorGauge
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import me.desht.pneumaticcraft.api.PNCCapabilities
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine
import me.desht.pneumaticcraft.common.block.entity.compressor.AirCompressorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
import kotlin.math.max

/**
 * The Stoker, the Heater and Tailwind on real machines of PneumaticCraft: Repressurized 8.2.24 (CD-FUEL-08, CD-HTR-07, CD-AIR-08). Each
 * test keeps its machine to one role: the Stoker claims any compressor in its work area on its own, while the Heater and Tailwind take
 * only the machine that the Work Leash binds them to.
 *
 * A compressor takes air in and out only on its front face, and one with no air handler there leaks out of that face on each tick, more
 * than the 10 mL that an Air Compressor makes. So the Stoker's Air Compressor faces a UV Light Box, a tier 1 machine that connects on its
 * own front face, never leaks, and uses air only for a circuit board, and the two hold their air together.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class PncCompatTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    /** On the floor, so that a worker stands beside it at its own height. */
    private val machineRel = BlockPos(7, 2, 5)

    /** East of the machine, where the UV Light Box that closes the Air Compressor's front face stands. */
    private val boxRel = machineRel.east()

    /**
     * CD-FUEL-08: a Charmander at an Air Compressor with no fuel. After each worked tick the compressor holds its base burn rate of 1
     * burn tick, and its flame gauge, the private `maxBurnTime` that the mixin opens, holds the same, so the gauge is full. The compressor
     * burns that tick on its own next tick into 10 mL, so the air of the compressor and its UV Light Box grows by 10 mL on each tick
     * after a worked tick, and the compressor's pressure rises. After 100 worked ticks both machines go to the air from which the
     * burn that the worker gave last makes the compressor's danger pressure: from the next tick the compressor gets no burn, the
     * Charmander stops Working, no more air comes, and the pressure stays at or below the danger pressure.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = FUEL_TIMEOUT_TICKS)
    fun cdFuel08CharmanderRaisesCompressorPressure(helper: GameTestHelper) {
        helper.assertTrue(RoleRegistry.current.get(STOKER) != null, "the role $STOKER is not active")
        val box = machine(helper, boxRel, UV_LIGHT_BOX, Direction.WEST)
        val compressor = compressor(helper, AIR_COMPRESSOR, Direction.EAST)
        val gauge = (compressor as Any) as? CompressorGauge
            ?: throw GameTestAssertException("CD-FUEL-08: the gauge mixin did not apply to the Air Compressor")
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val air = compressor.getAirHandler(null) ?: throw GameTestAssertException("the Air Compressor has no air handler")
        val boxAir = air(helper, box, Direction.WEST)
        val total = { air.air + boxAir.air }
        var lastTotal = total()
        var workedLastTick = false
        var worked = 0
        var startPressure = 0f
        var dangerAt = -1L
        var dangerTotal = 0
        failAtTimeout(helper, FUEL_TIMEOUT_TICKS, charmander) { "after $worked worked ticks, burn ${compressor.burnTime}" }
        helper.onEachTick {
            val now = total()
            val made = now - lastTotal
            lastTotal = now
            if (dangerAt >= 0) {
                val since = helper.tick - dangerAt
                val burn = compressor.burnTime
                helper.assertTrue(burn == 0, "CD-FUEL-08: the compressor holds $burn burn ticks at its danger pressure")
                val status = TestKit.workerOf(helper, charmander)?.status
                helper.assertTrue(status != WorkerStatus.WORKING, "CD-FUEL-08: the Stoker Works a compressor at its danger pressure")
                helper.assertTrue(air.pressure <= air.dangerPressure, "the compressor reached ${air.pressure} bar")
                if (since == 1L) dangerTotal = now
                helper.assertTrue(now == dangerTotal, "CD-FUEL-08: the air went from $dangerTotal to $now mL at the danger pressure")
                if (since >= 60) helper.succeed()
                return@onEachTick
            }
            helper.assertTrue(made == (if (workedLastTick) AIR_PER_BURN else 0), "the machines gained $made mL on a tick")
            workedLastTick = isWorkingOn(helper, charmander, compressor.blockPos)
            if (!workedLastTick) return@onEachTick
            if (worked++ == 0) {
                helper.assertTrue(roleOf(helper, charmander) == STOKER, "the Charmander works as ${roleOf(helper, charmander)}")
                startPressure = air.pressure
            }
            val burn = compressor.burnTime
            val max = gauge.dynamicsMaxBurnTime()
            helper.assertTrue(burn == 1, "CD-FUEL-08: the worked Air Compressor holds $burn burn ticks, not its base rate of 1")
            helper.assertTrue(max == burn, "CD-FUEL-08: the flame gauge's maximum is $max with $burn burn ticks")
            if (worked < 100) return@onEachTick
            helper.assertTrue(air.pressure > startPressure, "CD-FUEL-08: the pressure went from $startPressure to ${air.pressure} bar")
            // The burn of this tick makes 10 mL on the compressor's next tick, which takes it to its danger pressure exactly.
            val target = (air.dangerPressure * air.volume).toInt() - AIR_PER_BURN
            air.addAir(target - air.air)
            boxAir.addAir((target.toLong() * boxAir.volume / air.volume).toInt() - boxAir.air)
            dangerAt = helper.tick
        }
    }

    /**
     * CD-FUEL-08: a Charmander at an Advanced Air Compressor with no fuel. After each worked tick the compressor holds its base burn
     * rate of 5 burn ticks, and its flame gauge holds the same. Its own tick burns all 5 on the next tick, and its pressure stays far
     * below its danger pressure of 20 bar, because a compressor whose front face has no air handler leaks there.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = ADVANCED_TIMEOUT_TICKS)
    fun cdFuel08AdvancedCompressorTakesFiveBurnTicks(helper: GameTestHelper) {
        helper.assertTrue(RoleRegistry.current.get(STOKER) != null, "the role $STOKER is not active")
        val compressor = compressor(helper, ADVANCED_AIR_COMPRESSOR, Direction.EAST)
        val gauge = (compressor as Any) as? CompressorGauge
            ?: throw GameTestAssertException("CD-FUEL-08: the gauge mixin did not apply to the Advanced Air Compressor")
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        var worked = 0
        failAtTimeout(helper, ADVANCED_TIMEOUT_TICKS, charmander) { "after $worked worked ticks, burn ${compressor.burnTime}" }
        helper.onEachTick {
            if (!isWorkingOn(helper, charmander, compressor.blockPos)) return@onEachTick
            val burn = compressor.burnTime
            val max = gauge.dynamicsMaxBurnTime()
            helper.assertTrue(burn == 5, "CD-FUEL-08: the worked Advanced Air Compressor holds $burn burn ticks, not its base rate of 5")
            helper.assertTrue(max == burn, "CD-FUEL-08: the flame gauge's maximum is $max with $burn burn ticks")
            if (++worked >= 40) helper.succeed()
        }
    }

    /**
     * CD-HTR-07: a level 100 Charmander bound to an Advanced Air Compressor, which takes heat where the Air Compressor does not (R87).
     * The compressor's heat exchanger has a thermal capacity of 100, the same one on every face, and with no fuel nothing else heats or
     * cools it. On each worked tick its temperature T must rise by max(0, (1,300 − T) ÷ 100) × 100 ÷ 100 heat over that capacity, with
     * T read on the tick before, and after 100 worked ticks it must stand above its start.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = HEAT_TIMEOUT_TICKS)
    fun cdHtr07HeaterWarmsAdvancedCompressor(helper: GameTestHelper) {
        helper.assertTrue(RoleRegistry.current.get(HEATER) != null, "the role $HEATER is not active")
        val compressor = compressor(helper, ADVANCED_AIR_COMPRESSOR, Direction.EAST)
        val scene = scene(helper)
        val charmander = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=100")
        val binding = TestKit.bind(helper, scene.owner, charmander, machineRel)
        helper.assertTrue(
            binding?.roleId == HEATER,
            "the leash bound the Charmander as ${binding?.roleId}, not $HEATER; the reply was '${TestKit.lastMessage(scene.owner)}'",
        )
        var last = heat(helper).temperature
        var start = Double.NaN
        var worked = 0
        failAtTimeout(helper, HEAT_TIMEOUT_TICKS, charmander) { "after $worked worked ticks, at $last K" }
        helper.onEachTick {
            val exchanger = heat(helper)
            val now = exchanger.temperature
            if (isWorkingOn(helper, charmander, compressor.blockPos)) {
                if (start.isNaN()) {
                    helper.assertTrue(roleOf(helper, charmander) == HEATER, "the Charmander works as ${roleOf(helper, charmander)}")
                    start = last
                }
                val expected = max(0.0, (SOURCE_K - last) / RESISTANCE) * LEVEL / 100.0 / exchanger.thermalCapacity
                val rose = now - last
                helper.assertTrue(
                    abs(rose - expected) <= 1e-9 + 1e-6 * abs(expected),
                    "CD-HTR-07: at $last K a worked tick raised the temperature by $rose K, not $expected K",
                )
                if (++worked >= 100) {
                    helper.assertTrue(now > start, "CD-HTR-07: the temperature went from $start K to $now K in 100 worked ticks")
                    helper.succeed()
                }
            }
            last = now
        }
    }

    /**
     * CD-AIR-08: a level 100 Pidgey bound to a UV Light Box, a tier 1 machine with a danger pressure of 5 bar and a volume of 2,000 mL,
     * which makes, uses and leaks no air here, for 2,400 ticks. Tailwind adds up to 50 mL a tick, so with no limit it would pass 5 bar
     * within 200 ticks. The pressure must stay below 5 bar on every tick, and it must come within 0.01 bar of it, which shows that the
     * Pidgey worked the machine.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_700)
    fun cdAir08TailwindStopsBelowTheDangerPressure(helper: GameTestHelper) {
        helper.assertTrue(RoleRegistry.current.get(TAILWIND) != null, "the role $TAILWIND is not active")
        val box = machine(helper, machineRel, UV_LIGHT_BOX, Direction.EAST)
        val air = air(helper, box, Direction.EAST)
        helper.assertTrue(air.dangerPressure == 5f, "the UV Light Box's danger pressure is ${air.dangerPressure} bar, not 5")
        // Cobblemon tethers the Pidgey south of the pasture. A flying Pidgey whose straight line to the box crosses the pasture hovers
        // over it, so the pasture stands aside.
        val scene = scene(helper, BlockPos(10, 1, 9))
        val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=100")
        val binding = TestKit.bind(helper, scene.owner, pidgey, machineRel)
        helper.assertTrue(
            binding?.roleId == TAILWIND,
            "the leash bound the Pidgey as ${binding?.roleId}, not $TAILWIND; the reply was '${TestKit.lastMessage(scene.owner)}'",
        )
        val boundAt = helper.tick
        var peak = 0f
        var worked = false
        helper.onEachTick {
            val pressure = air.pressure
            helper.assertTrue(pressure < 5f, "CD-AIR-08: the UV Light Box reached $pressure bar")
            peak = max(peak, pressure)
            if (isWorkingOn(helper, pidgey, box)) worked = true
            if (helper.tick - boundAt < 2_400) return@onEachTick
            helper.assertTrue(worked, "the Pidgey never worked the UV Light Box: ${CompatDiag.describe(helper, pidgey)}")
            helper.assertTrue(peak >= 4.99f, "the pressure peaked at $peak bar, so Tailwind never neared the danger pressure")
            helper.succeed()
        }
    }

    private fun scene(helper: GameTestHelper, pastureRel: BlockPos = BlockPos(7, 1, 9)): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** The PneumaticCraft machine [id] at [rel], its front face turned to [facing]. Returns its absolute position. */
    private fun machine(helper: GameTestHelper, rel: BlockPos, id: ResourceLocation, facing: Direction): BlockPos {
        val block = BuiltInRegistries.BLOCK.getOptional(id).orElseThrow { GameTestAssertException("no block $id is registered") }
        helper.setBlock(rel, facing(block.defaultBlockState(), facing))
        return helper.absolutePos(rel)
    }

    private fun compressor(helper: GameTestHelper, id: ResourceLocation, facing: Direction): AirCompressorBlockEntity =
        helper.level.getBlockEntity(machine(helper, machineRel, id, facing)) as? AirCompressorBlockEntity
            ?: throw GameTestAssertException("no compressor block entity for $id")

    /** The air handler of the machine at [pos], through its front face [facing], the only face on which it connects. */
    private fun air(helper: GameTestHelper, pos: BlockPos, facing: Direction): IAirHandlerMachine =
        helper.level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, facing)
            ?: throw GameTestAssertException("the machine at ${TestKit.relative(helper, pos)} has no air handler on its $facing face")

    /** The heat exchanger on the north face of the machine, a horizontal face, as the Heater asks one. */
    private fun heat(helper: GameTestHelper): IHeatExchangerLogic =
        helper.level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, helper.absolutePos(machineRel), Direction.NORTH)
            ?: throw GameTestAssertException("the machine has no heat exchanger on its north face")

    /** [state] with its `facing` property set to [direction]. */
    private fun facing(state: BlockState, direction: Direction): BlockState {
        val property = state.block.stateDefinition.getProperty("facing") as? DirectionProperty
            ?: throw GameTestAssertException("${state.block} has no facing property")
        return state.setValue(property, direction)
    }

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, pos: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, pos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private fun roleOf(helper: GameTestHelper, entity: PokemonEntity) = TestKit.engine(helper).roleOf(entity.pokemon.uuid)?.definition?.id

    /** Fails 10 ticks before [timeout] with what the worker shows, so that a stalled test says where it stopped. */
    private fun failAtTimeout(helper: GameTestHelper, timeout: Int, entity: PokemonEntity, progress: () -> String) {
        helper.runAtTickTime(timeout - 10L) {
            val worker = TestKit.workerOf(helper, entity)
            helper.fail(
                "the ${entity.pokemon.species.name} is ${worker?.status} at ${TestKit.relative(helper, entity.blockPosition())}, " +
                    "spot ${worker?.spot?.let { TestKit.relative(helper, it) }}, claim ${TestKit.claimOf(helper, entity)?.key}; " +
                    progress(),
            )
        }
    }

    private companion object {
        const val FUEL_TIMEOUT_TICKS = 900
        const val ADVANCED_TIMEOUT_TICKS = 600
        const val HEAT_TIMEOUT_TICKS = 800

        /** The air that an Air Compressor with no upgrade makes from 1 burn tick: its base production of 10 mL. */
        const val AIR_PER_BURN = 10

        val AIR_COMPRESSOR: ResourceLocation = ResourceLocation.fromNamespaceAndPath("pneumaticcraft", "air_compressor")
        val ADVANCED_AIR_COMPRESSOR: ResourceLocation = ResourceLocation.fromNamespaceAndPath("pneumaticcraft", "advanced_air_compressor")
        val UV_LIGHT_BOX: ResourceLocation = ResourceLocation.fromNamespaceAndPath("pneumaticcraft", "uv_light_box")
        val STOKER: ResourceLocation = id("stoker_pneumaticcraft")
        val HEATER: ResourceLocation = id("heater_pneumaticcraft")
        val TAILWIND: ResourceLocation = id("tailwind_pneumaticcraft")

        /** CD-HTR-07: the lava block that the Heater copies, 1,300 K behind a resistance of 100, at level 100. */
        const val SOURCE_K = 1_300.0
        const val RESISTANCE = 100.0
        const val LEVEL = 100
    }
}
