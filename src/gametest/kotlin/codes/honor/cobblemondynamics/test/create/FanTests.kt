package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.handler.create.FanCatalyst
import codes.honor.cobblemondynamics.handler.create.FanWorkers
import codes.honor.cobblemondynamics.handler.create.FireMode
import codes.honor.cobblemondynamics.test.TestKit
import com.simibubi.create.infrastructure.config.AllConfigs
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * Create encased fans that hold a Pokémon catalyst on their fan state, with no Pokémon: what Create's own processing does with each
 * catalyst, and the air current's exemption of a marked worker. Each fan sits at [fanRel] and faces east.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class FanTests {
    private val fanRel = BlockPos(3, 2, 7)

    /**
     * Raw iron on an unpowered belt under the current of a fan with a Fire catalyst in blasting mode becomes an iron ingot, no sooner
     * than Create's processing time (CD-FAN-05, CD-BAL-04).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFan05Bal04BlastingOnABeltKeepsCreatesTime(helper: GameTestHelper) {
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        val belt = CreateKit.belt(helper, BlockPos(5, 1, 7), BlockPos(7, 1, 7), ItemStack(Items.RAW_IRON))
        val start = helper.level.gameTime
        CreateKit.catalyse(helper, fan, FanCatalyst.FIRE)
        val minimum = AllConfigs.server().kinetics.fanProcessingTime.get()
        helper.succeedWhen {
            val stack = belt.getInventory().transportedItems.firstOrNull()?.stack ?: throw GameTestAssertException("the belt holds no item")
            helper.assertTrue(stack.`is`(Items.IRON_INGOT), "CD-FAN-05: the belt holds $stack")
            val elapsed = helper.level.gameTime - start
            helper.assertTrue(elapsed >= minimum - 1, "CD-BAL-04: blasting took $elapsed ticks, under Create's $minimum")
        }
    }

    /** In smoking mode, raw beef becomes steak, and raw iron stays raw iron (CD-FAN-06). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFan06SmokingCooksBeefAndLeavesIron(helper: GameTestHelper) {
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        val beef = CreateKit.depot(helper, BlockPos(5, 1, 7), ItemStack(Items.BEEF))
        val iron = CreateKit.depot(helper, BlockPos(6, 1, 7), ItemStack(Items.RAW_IRON))
        CreateKit.catalyse(helper, fan, FanCatalyst.FIRE, FireMode.SMOKING)
        helper.succeedWhen {
            helper.assertTrue(iron.heldItem.`is`(Items.RAW_IRON), "CD-FAN-06: smoking turned the raw iron into ${iron.heldItem}")
            helper.assertTrue(beef.heldItem.`is`(Items.COOKED_BEEF), "CD-FAN-06: the beef depot holds ${beef.heldItem}")
        }
    }

    /** A Water catalyst washes red wool into white wool (CD-FAN-07). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFan07WaterWashesRedWool(helper: GameTestHelper) {
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        val wool = CreateKit.depot(helper, BlockPos(5, 1, 7), ItemStack(Items.RED_WOOL))
        CreateKit.catalyse(helper, fan, FanCatalyst.WATER)
        helper.succeedWhen { helper.assertTrue(wool.heldItem.`is`(Items.WHITE_WOOL), "CD-FAN-07: the depot holds ${wool.heldItem}") }
    }

    /** A Ghost catalyst haunts sand into soul sand, Create's haunting recipe `create:haunting/soul_sand` (CD-FAN-08). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFan08GhostHauntsSand(helper: GameTestHelper) {
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        val sand = CreateKit.depot(helper, BlockPos(5, 1, 7), ItemStack(Items.SAND))
        CreateKit.catalyse(helper, fan, FanCatalyst.GHOST)
        helper.succeedWhen { helper.assertTrue(sand.heldItem.`is`(Items.SOUL_SAND), "CD-FAN-08: the depot holds ${sand.heldItem}") }
    }

    /**
     * A water source fills the block in front of the fan, where a worker would stand, over a depot of sand. With a Ghost catalyst,
     * the sand becomes soul sand, not Create's washed clay, because our type outranks splashing at 400 (CD-FAN-13). The depot sits
     * under the front block, so water that flows on downstream does not reach it.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFan13GhostInWaterStillHaunts(helper: GameTestHelper) {
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 64)
        val sand = CreateKit.depot(helper, BlockPos(4, 1, 7), ItemStack(Items.SAND))
        helper.setBlock(BlockPos(4, 2, 7), Blocks.WATER)
        CreateKit.catalyse(helper, fan, FanCatalyst.GHOST)
        helper.succeedWhen {
            helper.assertFalse(sand.heldItem.`is`(Items.CLAY_BALL), "CD-FAN-13: the sand was washed")
            helper.assertTrue(sand.heldItem.`is`(Items.SOUL_SAND), "CD-FAN-13: the depot holds ${sand.heldItem}")
        }
    }

    /**
     * A fan at 256 RPM with a Ghost catalyst. A wild Gastly with the fan worker mark stands in the block in front of the fan, and an
     * unmarked Gastly stands 3 blocks downstream, both with no AI. After 100 ticks the marked one has not moved and has no Blindness
     * or Slowness, and the unmarked one was pushed or haunted, so the current works (CD-FAN-12). A mob with no AI never moves on its
     * own velocity, so the push shows in the velocity that the fan gives it: east for the unmarked one, none for the marked one.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdFan12MarkedWorkerIsNeitherPushedNorHaunted(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.AIR_CURRENT), "the air current mixin did not apply")
        val fan = CreateKit.fan(helper, fanRel, Direction.EAST, rpm = 256)
        CreateKit.catalyse(helper, fan, FanCatalyst.GHOST)
        val marked = TestKit.wild(helper, BlockPos(4, 2, 7), "gastly level=10").apply { isNoAi = true }
        val control = TestKit.wild(helper, BlockPos(7, 2, 7), "gastly level=10").apply { isNoAi = true }
        FanWorkers.mark(marked, helper.absolutePos(fanRel))
        val markedStart = marked.position()
        val controlStart = control.position()
        var pushed = false
        helper.onEachTick {
            helper.assertTrue(abs(marked.deltaMovement.x) <= PUSH, "CD-FAN-12: the fan pushed the marked Gastly: ${marked.deltaMovement}")
            if (control.deltaMovement.x > PUSH) pushed = true
        }
        helper.runAfterDelay(100) {
            helper.assertTrue(marked.position().distanceTo(markedStart) <= 0.5, "CD-FAN-12: the fan moved the marked Gastly")
            val harmed = marked.hasEffect(MobEffects.BLINDNESS) || marked.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
            helper.assertFalse(harmed, "CD-FAN-12: the marked Gastly was haunted")
            val acted = control.position().distanceTo(controlStart) > 1.0 || control.hasEffect(MobEffects.BLINDNESS)
            helper.assertTrue(acted, "the current did nothing to the unmarked Gastly, so the scene proves nothing")
            helper.assertTrue(pushed, "the current never pushed the unmarked Gastly, so the scene proves nothing about the push")
            helper.succeed()
        }
    }

    private companion object {
        /** A velocity along the current above this is a push. Create's first push at 256 RPM adds about 0.3 blocks per tick. */
        const val PUSH = 0.01
    }
}
