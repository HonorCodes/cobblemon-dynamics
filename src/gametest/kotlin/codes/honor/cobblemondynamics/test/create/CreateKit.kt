package codes.honor.cobblemondynamics.test.create

import com.simibubi.create.AllBlocks
import com.simibubi.create.api.boiler.BoilerHeater
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs

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
}
