package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.handler.create.FanCatalyst
import codes.honor.cobblemondynamics.handler.create.FanState
import codes.honor.cobblemondynamics.handler.create.FireMode
import com.simibubi.create.AllBlocks
import com.simibubi.create.api.boiler.BoilerHeater
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import com.simibubi.create.content.logistics.depot.DepotBlockEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** Builds Create machines for GameTests. Only the full GameTest run has Create, so this stays out of `kotlin-shared`. */
object CreateKit {
    /**
     * A small water wheel at [rel] on the X axis. Create looks for flow in the cells above, below, north and south of it, so a
     * wheel on the stone floor with air around it is dry.
     */
    fun smallWheel(helper: GameTestHelper, rel: BlockPos): WaterWheelBlockEntity {
        helper.setBlock(rel, AllBlocks.WATER_WHEEL.defaultState.setValue(DirectionalKineticBlock.FACING, Direction.EAST))
        return helper.getBlockEntity(rel)
    }

    /** A large water wheel centred on [rel], on the X axis. Create places its ring of structural blocks on the next tick. */
    fun largeWheel(helper: GameTestHelper, rel: BlockPos): WaterWheelBlockEntity {
        helper.setBlock(rel, AllBlocks.LARGE_WATER_WHEEL.defaultState.setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X))
        return helper.getBlockEntity(rel)
    }

    /** The 8 ring cells of a large wheel centred on [rel], on the X axis. */
    fun ringOf(rel: BlockPos): List<BlockPos> = (-1..1).flatMap { dy -> (-1..1).map { dz -> rel.offset(0, dy, dz) } } - rel

    fun isRingBuilt(helper: GameTestHelper, rel: BlockPos): Boolean =
        ringOf(rel).all { helper.getBlockState(it).`is`(AllBlocks.WATER_WHEEL_STRUCTURAL.get()) }

    /** A hand crank at [rel] whose `FACING` is [facing]. Create drops a crank with nothing solid behind it, so this puts stone there. */
    fun handCrank(helper: GameTestHelper, rel: BlockPos, facing: Direction): HandCrankBlockEntity {
        val support = rel.relative(facing.opposite)
        if (helper.getBlockState(support).isAir) helper.setBlock(support, Blocks.STONE)
        helper.setBlock(rel, AllBlocks.HAND_CRANK.defaultState.setValue(DirectionalKineticBlock.FACING, facing))
        return helper.getBlockEntity(rel)
    }

    /** A filled Blaze Burner with no fuel at [rel]. Create's own tick holds it at `SMOULDERING`. */
    fun filledBurner(helper: GameTestHelper, rel: BlockPos): BlazeBurnerBlockEntity {
        val filled = AllBlocks.BLAZE_BURNER.defaultState.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        helper.setBlock(rel, filled)
        return helper.getBlockEntity(rel)
    }

    fun basin(helper: GameTestHelper, rel: BlockPos) = helper.setBlock(rel, AllBlocks.BASIN.defaultState)

    /** A one-block fluid tank, which becomes its own controller on its first tick. With a heater below, it is a boiler. */
    fun fluidTank(helper: GameTestHelper, rel: BlockPos) = helper.setBlock(rel, AllBlocks.FLUID_TANK.defaultState)

    /** The heat level in the block state at [rel], which basins and boilers read. */
    fun heatAt(helper: GameTestHelper, rel: BlockPos): BlazeBurnerBlock.HeatLevel =
        BlazeBurnerBlock.getHeatLevelOf(helper.getBlockState(rel))

    /** The heat that Create's boiler heater registry gives the block at [rel]: 1 for `KINDLED`, 2 for `SEETHING`. */
    fun heaterHeat(helper: GameTestHelper, rel: BlockPos): Float {
        val pos = helper.absolutePos(rel)
        return BoilerHeater.findHeat(helper.level, pos, helper.level.getBlockState(pos))
    }

    /**
     * The active heat that the boiler of the tank at [rel] counts from the blocks under it. `BoilerData.updateTemperature` is the
     * boiler's own heat count; a boiler with no engine never runs it, because `updateBoilerTemperature` skips an inactive boiler.
     */
    fun boilerHeat(helper: GameTestHelper, rel: BlockPos): Int? {
        val tank = helper.getBlockEntity<FluidTankBlockEntity>(rel).controllerBE ?: return null
        tank.boiler.updateTemperature(tank)
        return tank.boiler.activeHeat
    }

    /** The capacity that [be] adds at its current speed, as Create's goggles compute it. */
    fun capacity(be: KineticBlockEntity): Float = be.calculateAddedStressCapacity() * abs(be.getSpeed())

    /** The capacity of the kinetic network that [be] is in, or null when it is in none. */
    fun networkCapacity(be: KineticBlockEntity): Float? = if (be.hasNetwork()) be.getOrCreateNetwork().calculateCapacity() else null

    /** A creative motor at [rel] whose shaft faces [facing], turning at [rpm]. */
    fun motor(helper: GameTestHelper, rel: BlockPos, facing: Direction, rpm: Int): CreativeMotorBlockEntity {
        helper.setBlock(rel, AllBlocks.CREATIVE_MOTOR.defaultState.setValue(DirectionalKineticBlock.FACING, facing))
        return helper.getBlockEntity<CreativeMotorBlockEntity>(rel).also { setRpm(it, rpm) }
    }

    fun setRpm(motor: CreativeMotorBlockEntity, rpm: Int) {
        motor.generatedSpeed.setValue(rpm)
        motor.updateGeneratedRotation()
    }

    /** An encased fan at [rel] facing [facing], with a creative motor behind it at [rpm], so that it blows out of its face. */
    fun fan(helper: GameTestHelper, rel: BlockPos, facing: Direction, rpm: Int): EncasedFanBlockEntity {
        helper.setBlock(rel, AllBlocks.ENCASED_FAN.defaultState.setValue(DirectionalKineticBlock.FACING, facing))
        motor(helper, rel.relative(facing.opposite), facing, rpm)
        val fan = helper.getBlockEntity<EncasedFanBlockEntity>(rel)
        helper.runAfterDelay(1) {
            if (fan.airFlowDirection != facing) throw GameTestAssertException("the fan at $rel blows ${fan.airFlowDirection}, not $facing")
        }
        return fan
    }

    /** A depot at [rel] that holds [stack]. A depot one block below an air current is in the current. */
    fun depot(helper: GameTestHelper, rel: BlockPos, stack: ItemStack): DepotBlockEntity {
        helper.setBlock(rel, AllBlocks.DEPOT.defaultState)
        return helper.getBlockEntity<DepotBlockEntity>(rel).also { it.setHeldItem(stack) }
    }

    /**
     * An unpowered belt from [from] to [to] along one axis, with [stack] at its middle. Returns its controller. A belt finds its
     * controller in its first tick, through `BeltBlock.initBelt`, which this calls at once.
     */
    fun belt(helper: GameTestHelper, from: BlockPos, to: BlockPos, stack: ItemStack): BeltBlockEntity {
        BeltConnectorItem.createBelts(helper.level, helper.absolutePos(from), helper.absolutePos(to))
        BeltBlock.initBelt(helper.level, helper.absolutePos(from))
        val controller = helper.getBlockEntity<BeltBlockEntity>(from).controllerBE ?: throw GameTestAssertException("no belt controller")
        controller.getInventory().addItem(TransportedItemStack(stack).apply { beltPosition = from.distManhattan(to) / 2f + 0.5f })
        return controller
    }

    /**
     * Holds [kind] (and [mode], for Fire) on [fan] for the rest of the test, as a Working worker does. Starts on the fan's first tick,
     * when Create attaches the fan state.
     */
    fun catalyse(helper: GameTestHelper, fan: EncasedFanBlockEntity, kind: FanCatalyst, mode: FireMode = FireMode.BLASTING) {
        var held: FanState? = null
        helper.onEachTick {
            val state = held ?: FanState.of(fan)?.also {
                if (it.fireMode != mode) it.flipMode()
                held = it
            } ?: return@onEachTick
            state.grant(kind, helper.level.gameTime)
        }
    }

    /**
     * A windmill bearing at [rel] that faces up, with a shaft below it and a flat sheet of [sails] sails on its face. A sail holds on
     * to its neighbours across its own facing, so the sheet assembles as one windmill.
     */
    fun windmill(helper: GameTestHelper, rel: BlockPos, sails: Int): WindmillBearingBlockEntity {
        helper.setBlock(rel.below(), AllBlocks.SHAFT.defaultState.setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y))
        helper.setBlock(rel, AllBlocks.WINDMILL_BEARING.defaultState.setValue(DirectionalKineticBlock.FACING, Direction.UP))
        val width = ceil(sqrt(sails.toDouble())).toInt()
        // Row by row from one corner; the sheet always covers the block on the bearing's face, at its center.
        for (i in 0 until sails) helper.setBlock(rel.offset(i % width - width / 2, 1, i / width - width / 2), sail())
        return helper.getBlockEntity(rel)
    }

    /** A sail that lies flat, as a windmill that faces up holds it. */
    fun sail() = AllBlocks.SAIL.defaultState.setValue(DirectionalBlock.FACING, Direction.UP)
}
