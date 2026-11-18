package codes.honor.cobblemondynamics.compattest

import blusunrize.immersiveengineering.api.IEProperties
import blusunrize.immersiveengineering.common.blocks.wooden.WatermillBlockEntity
import blusunrize.immersiveengineering.common.blocks.wooden.WindmillBlockEntity
import blusunrize.immersiveengineering.common.config.IEServerConfig
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeAttachments
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeWatermillHandler
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeWatermillRule
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeWindmillHandler
import codes.honor.cobblemondynamics.compat.immersiveengineering.WatermillDrive
import codes.honor.cobblemondynamics.compat.immersiveengineering.WatermillParams
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.slf4j.Logger
import kotlin.math.abs

/**
 * The Wheel Driver and Tailwind on the real mills of Immersive Engineering 12.4.2 (ADR-0020). Each mill turns a real Kinetic Dynamo,
 * which pushes its FE into a real LV capacitor below it, through the top face that IE makes the capacitor's input by default, and the
 * tests read what the capacitor stores. A dynamo gives `dynamo_output` ÷ 3 FE per tick for each unit of rotation it gets, floored, so
 * 1 FE per unit at IE's default of 3. A watermill gives the dynamo the sum of `getIFScaledTorque()` over its row of wheels.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class IeCompatTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    /** The axle of each watermill, in the middle of the template. The wheel faces north, and its 5 × 5 ring spans y 3 to 7. */
    private val axleRel = BlockPos(11, 5, 11)

    /** The Kinetic Dynamo north of the axle, its shaft toward the wheel, over its capacitor. */
    private val wheelDynamoRel = axleRel.north()

    /**
     * The windmill faces north. Its sails turn in a disc 4 blocks out from the hub, which clears the floor from y = 6, and its 7 blocks
     * of airflow to the north stay inside the template.
     */
    private val windmillRel = BlockPos(11, 6, 15)

    /** The Kinetic Dynamo behind the windmill, its shaft toward the windmill, over its capacitor. */
    private val windDynamoRel = windmillRel.south()

    /**
     * CD-KIN-02: a level 10 Squirtle Works a dry watermill on a Kinetic Dynamo. Through the mixin at the return of
     * `getIFScaledTorque()`, the wheel reports `virtual_torque` times its stored direction, and the dynamo pushes FE into the capacitor
     * on every tick: more than 0, and no more than an optimized overshot wheel gives. That bound is
     * `IeWatermillRule.OPTIMIZED_OVERSHOT_TORQUE`, which is in the units of `getIFScaledTorque()`, through the dynamo's own formula.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_200)
    fun cdKin02DryWatermillDrivesTheDynamo(helper: GameTestHelper) {
        val role = assertWheelDriver(helper)
        val virtual = WatermillParams.of(role.definition.params).virtualTorque
        val scene = scene(helper, wheelPastureRel)
        val wheel = dryWheel(helper, scene.owner)
        val squirtle = TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")
        var step = "claim"
        var since = -1L
        var start = 0
        helper.onEachTick {
            when (step) {
                "claim" -> if (isWorkingOn(helper, squirtle, wheel.blockPos)) {
                    val worked = TestKit.engine(helper).roleOf(squirtle.pokemon.uuid)?.definition?.id
                    helper.assertTrue(worked == WHEEL_DRIVER, "the Squirtle works the watermill as $worked")
                    step = "settle"
                    since = helper.tick
                }

                // IE computes the wheel again on the tick after the drive starts.
                "settle" -> if (helper.tick - since >= SETTLE_TICKS) {
                    start = storedFe(helper, wheelDynamoRel)
                    step = "sample"
                    since = helper.tick
                }

                "sample" -> if (helper.tick - since == SAMPLE_TICKS.toLong()) {
                    val perTick = (storedFe(helper, wheelDynamoRel) - start) / SAMPLE_TICKS
                    val direction = driveOf(wheel)?.direction ?: 0
                    val torque = wheel.ifScaledTorque
                    helper.assertTrue(
                        direction == WatermillDrive.FORWARD && torque == virtual * direction,
                        "CD-KIN-02: the dry wheel reports a torque of $torque, not $virtual × $direction",
                    )
                    helper.assertTrue(perTick > 0, "CD-KIN-02: the dynamo gives the capacitor $perTick FE/t")
                    val cap = dynamoFe(IeWatermillRule.OPTIMIZED_OVERSHOT_TORQUE)
                    helper.assertTrue(
                        perTick <= cap,
                        "CD-KIN-02: the dynamo gives $perTick FE/t, more than the $cap FE/t of an optimized overshot wheel",
                    )
                    helper.succeed()
                }
            }
        }
    }

    /**
     * CD-KIN-03: a level 10 Squirtle Works a dry watermill on a Kinetic Dynamo for 40 ticks. Then a water source above the wheel's top
     * west corner pours real flowing water through that corner and down the wheel's west side, so IE finds a torque of real water: the
     * Squirtle waits in Standby within 40 ticks of the source, and stays there for 20 more ticks.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_200)
    fun cdKin03RealFlowMeansStandby(helper: GameTestHelper) {
        assertWheelDriver(helper)
        val scene = scene(helper, wheelPastureRel)
        val wheel = dryWheel(helper, scene.owner)
        val squirtle = TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")
        var workingSince = -1L
        var wateredAt = -1L
        var standbySince = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, squirtle) ?: return@onEachTick
            when {
                wateredAt < 0 -> {
                    if (workingSince < 0 && isWorkingOn(helper, squirtle, wheel.blockPos)) workingSince = helper.tick
                    if (workingSince < 0) return@onEachTick
                    helper.assertTrue(worker.status == WorkerStatus.WORKING, "the Squirtle is ${worker.status} at the dry wheel")
                    if (helper.tick - workingSince >= DRY_TICKS) {
                        // The source sits in no cell that IE reads. The water it pours through the top west corner and down the west side does.
                        helper.setBlock(at(-2, 3), Blocks.WATER)
                        wateredAt = helper.tick
                    }
                }

                standbySince < 0 -> if (worker.status == WorkerStatus.STANDBY) {
                    val real = driveOf(wheel)?.realTorque
                    helper.assertTrue(real != null && real != 0.0, "the Squirtle waits, but IE finds no torque of real water: $real")
                    standbySince = helper.tick
                } else {
                    helper.assertTrue(
                        helper.tick - wateredAt <= STANDBY_TICKS,
                        "CD-KIN-03: the Squirtle is ${worker.status} $STANDBY_TICKS ticks after the water",
                    )
                }

                else -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "the Squirtle left Standby: ${worker.status}")
                    if (helper.tick - standbySince >= HOLD_TICKS) helper.succeed()
                }
            }
        }
    }

    /**
     * CD-KIN-04: the owner binds a level 10 Squirtle to a dry watermill on a Kinetic Dynamo. While the wheel turns forward, the binder's
     * sneak-click with the Work Leash flips it, as CD-WHL-05 flips a Create wheel: within 10 ticks the wheel's saved direction reads -1
     * and its speed is negative. Then the wheel's block entity is replaced by one loaded from its save, attachments included, as a
     * chunk load does: the loaded wheel still reads -1, and once the Squirtle drives it again it reports the negative torque and turns
     * backward.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_200)
    fun cdKin04FlipReversesTheWheelAndSurvivesAReload(helper: GameTestHelper) {
        val role = assertWheelDriver(helper)
        val virtual = WatermillParams.of(role.definition.params).virtualTorque
        val scene = scene(helper, wheelPastureRel)
        val axle = dryWheel(helper, scene.owner).blockPos
        val squirtle = TestKit.tether(helper, scene.pasture, scene.owner, "squirtle level=10")
        val binding = TestKit.bind(helper, scene.owner, squirtle, axleRel)
        helper.assertTrue(binding?.target == axle, "no binding to the watermill: $binding")
        var step = "work"
        var since = -1L
        helper.onEachTick {
            val wheel = wheelAt(helper)
            when (step) {
                "work" -> if (isWorkingOn(helper, squirtle, axle) && wheel.speed > 0.0) {
                    val result = TestKit.clickBlock(helper, scene.owner, axleRel, sneak = true)
                    helper.assertTrue(result.consumesAction(), "the binder's sneak-click returned $result")
                    since = helper.tick
                    step = "flipped"
                }

                "flipped" -> if (wheel.speed < 0.0) {
                    helper.assertTrue(driveOf(wheel)?.direction == -WatermillDrive.FORWARD, "the flip stored ${driveOf(wheel)?.direction}")
                    reload(helper)
                    val kept = driveOf(wheelAt(helper))?.direction
                    helper.assertTrue(kept == -WatermillDrive.FORWARD, "CD-KIN-04: the wheel loaded from its save reads direction $kept")
                    since = helper.tick
                    step = "reloaded"
                } else {
                    helper.assertTrue(
                        helper.tick - since < FLIP_TICKS,
                        "CD-KIN-04: the wheel turns at ${wheel.speed} $FLIP_TICKS ticks after the flip",
                    )
                }

                "reloaded" -> {
                    val drive = driveOf(wheel)
                    helper.assertTrue(
                        drive?.direction == -WatermillDrive.FORWARD,
                        "CD-KIN-04: the reloaded wheel's direction is ${drive?.direction}",
                    )
                    // The loaded speed is the saved one, so the test waits for IE to compute the wheel again with the new drive.
                    val driven = drive?.driven == true && wheel.ifScaledTorque == -virtual && wheel.speed < 0.0
                    if (helper.tick - since >= SETTLE_TICKS && isWorkingOn(helper, squirtle, axle) && driven) {
                        helper.succeed()
                    } else {
                        helper.assertTrue(
                            helper.tick - since <= RELOAD_TICKS,
                            "after the reload the wheel reports ${wheel.ifScaledTorque} and turns at ${wheel.speed}",
                        )
                    }
                }
            }
        }
    }

    /**
     * CD-AIR-05: a windmill with 8 sails on a Kinetic Dynamo, in clear weather, with all 399 cells of its airflow free. IE's own turn
     * speed carries the weather factor w = 0.75 × 0.66. While a level 100 Pidgey that the owner bound Works it, the windmill turns at
     * its own speed times (w + (1 − w) × 100 ÷ 100) ÷ w, through the mixin at the return of `getActualTurnSpeed()`, and the dynamo
     * gives the capacitor at least 71 FE on every tick.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_200)
    fun cdAir05Lv100PidgeyTurnsTheWindmillAtTheStormFactor(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.IE_WINDMILL), "the IE windmill mixin did not apply")
        val role = RoleRegistry.current.get(TAILWIND)
        helper.assertTrue(role?.handler === IeWindmillHandler, "the role $TAILWIND is not active with its handler: $role")
        val level = helper.level
        helper.assertTrue(
            !level.isRaining && !level.isThundering,
            "the weather is not clear: rain ${level.isRaining}, thunder ${level.isThundering}",
        )
        // IE's weather factor of `getActualTurnSpeed()`: 0.75 unless it rains, times 0.66 unless it thunders.
        val w = (if (level.isRaining) 1.0 else 0.75) * (if (level.isThundering) 1.0 else 0.66)
        dynamo(helper, windDynamoRel, Direction.NORTH)
        // The facing goes into the placed state, because the windmill finds its dynamo from it when it loads.
        helper.setBlock(windmillRel, ieBlock("windmill").defaultBlockState().setValue(IEProperties.FACING_HORIZONTAL, Direction.NORTH))
        val windmill = helper.level.getBlockEntity(helper.absolutePos(windmillRel)) as? WindmillBlockEntity
            ?: throw GameTestAssertException("no IE windmill at $windmillRel")
        windmill.sails = SAILS
        windmill.setChanged()
        val scene = scene(helper, windPastureRel)
        val pidgey = TestKit.tether(helper, scene.pasture, scene.owner, "pidgey level=100")
        CompatDiag.failBeforeTimeout(helper, 1_200, pidgey)
        var step = "turn"
        var since = -1L
        var own = 0.0
        var start = 0
        helper.onEachTick {
            when (step) {
                // IE computes a windmill's airflow every 128 ticks.
                "turn" -> if (windmill.turnSpeed > 0f) {
                    helper.assertTrue(
                        windmill.turnSpeed == FREE_AIRFLOW,
                        "the windmill's airflow is ${windmill.turnSpeed} of $FREE_AIRFLOW cells",
                    )
                    own = windmill.actualTurnSpeed
                    val binding = TestKit.bind(helper, scene.owner, pidgey, windmillRel)
                    helper.assertTrue(binding?.target == windmill.blockPos, "no binding to the windmill: $binding")
                    step = "claim"
                } else {
                    helper.assertTrue(helper.tick <= AIRFLOW_TICKS, "IE has not turned the windmill after ${helper.tick} ticks")
                }

                "claim" -> if (isWorkingOn(helper, pidgey, windmill.blockPos)) {
                    since = helper.tick
                    step = "factor"
                }

                "factor" -> if (helper.tick - since >= SETTLE_TICKS) {
                    val bonus = windmill.getExistingDataOrNull(IeAttachments.WINDMILL_BONUS.get())?.level
                    helper.assertTrue(bonus == PIDGEY_LEVEL, "the windmill holds a bonus of level $bonus")
                    val factor = w + (1.0 - w) * PIDGEY_LEVEL / 100.0
                    val expected = own * factor / w
                    val actual = windmill.actualTurnSpeed
                    helper.assertTrue(
                        abs(actual - expected) <= expected * 1e-9,
                        "CD-AIR-05: the windmill turns at $actual, not $own × $factor ÷ $w = $expected",
                    )
                    start = storedFe(helper, windDynamoRel)
                    since = helper.tick
                    step = "sample"
                }

                "sample" -> if (helper.tick - since == SAMPLE_TICKS.toLong()) {
                    val perTick = (storedFe(helper, windDynamoRel) - start) / SAMPLE_TICKS
                    helper.assertTrue(perTick >= WINDMILL_FE, "CD-AIR-05: the dynamo gets $perTick FE/t, less than $WINDMILL_FE")
                    helper.succeed()
                }
            }
        }
    }

    /**
     * Decision 22 and CD-BAL-14: measures IE's best case for one wheel, which caps `virtual_torque`. An optimized overshot watermill on
     * a Kinetic Dynamo, with real water that runs around the wheel one way. A source on a top slab in the wheel's top east corner,
     * walled on its other sides, feeds the 3 cells over the top, which flow west at levels 7, 6 and 5 of a source's 8. The water falls
     * through the top west corner and down the west side, and a stone at the foot of that side turns it east, back under the wheel,
     * into a tailrace one block wide. No source sits in a cell that IE reads. A top slab has no full face toward the wheel, so it does
     * not block it, and the stone sits in no cell that IE checks. Once IE's torque has stayed the same for 40 ticks, the test logs IE's
     * raw torque, `getIFScaledTorque()` and the dynamo's FE per tick in one INFO line, and asserts only that the scaled torque is above 0.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun cdBal14MeasuresTheOptimizedOvershotWatermill(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        dynamo(helper, wheelDynamoRel, Direction.SOUTH)
        val wheel = watermill(helper, placer)
        helper.setBlock(at(2, 2), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP))
        val stones = listOf(at(3, 3), at(2, 3, -1), at(2, 3, 1), at(-3, -3), at(-4, -2), at(-3, -2, -1), at(-3, -2, 1), at(7, -3)) +
            (-2..6).flatMap { x -> listOf(at(x, -3, -1), at(x, -3, 1)) }
        stones.forEach { helper.setBlock(it, Blocks.STONE) }
        helper.setBlock(at(2, 3), Blocks.WATER)
        var last = Double.NaN
        var stableSince = 0L
        var start = -1
        var startTick = 0L
        helper.onEachTick {
            val scaled = wheel.ifScaledTorque
            if (scaled != last) {
                last = scaled
                stableSince = helper.tick
                start = -1
                return@onEachTick
            }
            if (helper.tick < WATER_TICKS || helper.tick - stableSince < STABLE_TICKS) return@onEachTick
            if (start < 0) {
                // IE recomputes a wheel only on a neighbour change, so the dynamo's rotation may lag the last change of the water.
                if (startTick < stableSince) {
                    wheel.setShouldUpdate()
                    startTick = helper.tick
                } else if (helper.tick - startTick >= SETTLE_TICKS) {
                    start = storedFe(helper, wheelDynamoRel)
                    startTick = helper.tick
                }
                return@onEachTick
            }
            if (helper.tick - startTick < SAMPLE_TICKS) return@onEachTick
            val raw = wheel.getTorque(true, true).z
            val perTick = (storedFe(helper, wheelDynamoRel) - start) / SAMPLE_TICKS
            val overshot = abs(scaled - raw * IF_SCALE) < 1e-9
            LOGGER.info(
                "IE watermill torque, optimized overshot: raw {}, scaled {} ({} FE/t at the dynamo, overshot model {}, blocked {})",
                raw,
                scaled,
                perTick,
                overshot,
                wheel.isBlocked,
            )
            helper.assertTrue(scaled > 0.0, "the optimized overshot watermill reports a torque of $scaled")
            helper.succeed()
        }
    }

    /** The cell [dx] east, [dy] up and [dz] south of the axle. */
    private fun at(dx: Int, dy: Int, dz: Int = 0): BlockPos = axleRel.offset(dx, dy, dz)

    private fun assertWheelDriver(helper: GameTestHelper): ActiveRole {
        helper.assertTrue(MixinState.applied(MixinState.IE_WATERMILL), "the IE watermill mixin did not apply")
        val role = RoleRegistry.current.get(WHEEL_DRIVER)
        helper.assertTrue(role?.handler === IeWatermillHandler, "the role $WHEEL_DRIVER is not active with its handler: $role")
        return role ?: throw GameTestAssertException("the role $WHEEL_DRIVER is not active")
    }

    /** A Work Mode pasture at [rel] with its owner beside it. */
    private fun scene(helper: GameTestHelper, rel: BlockPos): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, rel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** A watermill with no water near it, on its dynamo. */
    private fun dryWheel(helper: GameTestHelper, placer: ServerPlayer): WatermillBlockEntity {
        dynamo(helper, wheelDynamoRel, Direction.SOUTH)
        val wheel = watermill(helper, placer)
        helper.assertFalse(wheel.isBlocked, "the dry watermill is blocked")
        return wheel
    }

    /**
     * IE's watermill with its axle at [axleRel], facing north: the master and its ring of 20 dummies, which IE's own `placeDummies`
     * places, as IE's block item does once it has set the facing.
     */
    private fun watermill(helper: GameTestHelper, placer: ServerPlayer): WatermillBlockEntity {
        val block = ieBlock("watermill")
        val state = block.defaultBlockState().setValue(IEProperties.FACING_HORIZONTAL, Direction.NORTH)
        helper.setBlock(axleRel, state)
        val wheel = wheelAt(helper)
        val hit = BlockHitResult(Vec3.atCenterOf(wheel.blockPos), Direction.SOUTH, wheel.blockPos, false)
        wheel.placeDummies(BlockPlaceContext(placer, InteractionHand.MAIN_HAND, ItemStack(block), hit), state)
        return wheel
    }

    private fun wheelAt(helper: GameTestHelper): WatermillBlockEntity =
        helper.level.getBlockEntity(helper.absolutePos(axleRel)) as? WatermillBlockEntity
            ?: throw GameTestAssertException("no IE watermill at $axleRel")

    /** Replaces the wheel's block entity with one loaded from its save, attachments included, as a chunk load does. */
    private fun reload(helper: GameTestHelper) {
        val old = wheelAt(helper)
        val registries = helper.level.registryAccess()
        val loaded = BlockEntity.loadStatic(old.blockPos, old.blockState, old.saveWithFullMetadata(registries), registries)
            ?: throw GameTestAssertException("the watermill did not load from its save")
        helper.level.setBlockEntity(loaded)
        helper.assertTrue(wheelAt(helper) !== old, "the reload kept the old block entity")
    }

    private fun driveOf(wheel: WatermillBlockEntity): WatermillDrive? = wheel.getExistingDataOrNull(IeAttachments.WATERMILL_DRIVE.get())

    /** A Kinetic Dynamo at [rel] whose shaft faces [facing], over an LV capacitor that takes the dynamo's FE through its top. */
    private fun dynamo(helper: GameTestHelper, rel: BlockPos, facing: Direction) {
        helper.setBlock(rel.below(), ieBlock("capacitor_lv"))
        helper.setBlock(rel, ieBlock("dynamo").defaultBlockState().setValue(IEProperties.FACING_HORIZONTAL, facing))
    }

    /** The FE in the capacitor below the dynamo at [dynamoRel]. */
    private fun storedFe(helper: GameTestHelper, dynamoRel: BlockPos): Int =
        helper.level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(dynamoRel.below()), null)?.energyStored
            ?: throw GameTestAssertException("the capacitor below $dynamoRel has no energy storage")

    /** The FE per tick that a dynamo gives for [rotation], by `DynamoBlockEntity`'s own formula and config. */
    private fun dynamoFe(rotation: Double): Int = (IEServerConfig.MACHINES.dynamo_output.get() / 3f * rotation).toInt()

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private fun ieBlock(path: String): Block {
        val id = ResourceLocation.fromNamespaceAndPath(IE, path)
        return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow { GameTestAssertException("no block $id is registered") }
    }

    private companion object {
        val LOGGER: Logger = LogUtils.getLogger()
        val WHEEL_DRIVER: ResourceLocation = id("wheel_driver_immersiveengineering")
        val TAILWIND: ResourceLocation = id("tailwind_immersiveengineering")
        const val IE = "immersiveengineering"

        /** South of the wheel, which the Squirtle reaches on the floor below the wheel's south face. */
        val wheelPastureRel = BlockPos(11, 1, 17)

        /**
         * Behind the windmill, out of its disc and its airflow. Cobblemon tethers a Pokémon south of its pasture, and one too near the
         * template's edge climbs onto its barrier ceiling, so the pasture stays clear of the edge and off the Pidgey's line to the windmill.
         */
        val windPastureRel = BlockPos(14, 1, 18)

        /** `getIFScaledTorque()` is IE's torque about the axle times this. */
        const val IF_SCALE = 1.25

        /** IE's most sails on one windmill. */
        const val SAILS = 8

        /** Every cell of a windmill's airflow is free: 57 cells of its disc, 7 blocks deep. */
        const val FREE_AIRFLOW = 399f

        const val PIDGEY_LEVEL = 100

        /** CD-AIR-05: 399 × 5e-5 × 1.5 for 8 sails × 2,400 = 71.8 FE/t at factor 1. */
        const val WINDMILL_FE = 71
        const val SETTLE_TICKS = 3L
        const val SAMPLE_TICKS = 20
        const val DRY_TICKS = 40L
        const val STANDBY_TICKS = 40L
        const val HOLD_TICKS = 20L
        const val FLIP_TICKS = 10L
        const val RELOAD_TICKS = 100L

        /** IE computes a windmill's airflow once in 128 ticks. */
        const val AIRFLOW_TICKS = 200L

        /** The water's longest path, from the source round to the tailrace's end, is 19 steps of 5 ticks. */
        const val WATER_TICKS = 200L
        const val STABLE_TICKS = 40L
    }
}
