package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.tree.FellPlan
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.tree.LumberjackParams
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.WallTorchBlock
import net.minecraft.world.level.block.state.properties.RailShape
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * The Lumberjack on a real oak (ADR-0017). The tests build the oak by hand: 6 logs and 210 natural leaves, each within 6 steps of the
 * trunk, so its fell drops no sapling about once in 47,000 runs. The canopy starts 3 blocks above the ground, which leaves room for the
 * worker's spot beside the trunk.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class LumberjackTests {
    private val base = BlockPos(11, 2, 9)

    private class Oak(val logs: List<BlockPos>, val leaves: List<BlockPos>)

    private fun scene(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return owner to pasture
    }

    /** An oak on dirt at [base], with natural leaves, as world generation and a sapling leave them. */
    private fun oak(helper: GameTestHelper): Oak {
        helper.setBlock(base.below(), Blocks.DIRT)
        val logs = (0 until 6).map { base.above(it) }
        for (log in logs) helper.setBlock(log, Blocks.OAK_LOG)
        val leaves = ArrayList<BlockPos>()
        fun layer(y: Int, radius: Int, round: Boolean) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val pos = BlockPos(base.x + dx, y, base.z + dz)
                    if ((round && abs(dx) == radius && abs(dz) == radius) || pos in logs) continue
                    helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState())
                    leaves += pos
                }
            }
        }
        for (y in 5..7) layer(y, 3, round = false)
        layer(8, 3, round = true)
        layer(9, 2, round = true)
        return Oak(logs, leaves)
    }

    private fun plan(helper: GameTestHelper): FellPlan =
        LumberjackHandler.plan(helper.level, helper.absolutePos(base), LumberjackParams.DEFAULT)
            ?: throw GameTestAssertException("no fell plan at the oak")

    /**
     * CD-LUM-03, CD-LUM-06, CD-LUM-09, CD-LUM-14, CD-LUM-17, CD-LUM-19, CD-LUM-22: a level 100 Scyther bound to a log of the oak fells
     * it with every natural leaf, the torch on its trunk and the 3 vines below a leaf, and replants a sapling from the drops. The 4
     * placed leaves on top and the flower beside the trunk stay, and the binding holds. A Magnemite bound to a chest collects the logs.
     * CD-LUM-30: an item frame with a diamond comes off the trunk with no sound, and a rail that rises onto the base cell joins the
     * shell, so the chest also gets the frame, the diamond and the rail.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdLum14LumberjackFellsAndReplants(helper: GameTestHelper) {
        val (owner, pasture) = scene(helper)
        val oak = oak(helper)
        val placed = listOf(BlockPos(10, 10, 8), BlockPos(12, 10, 8), BlockPos(10, 10, 10), BlockPos(12, 10, 10))
        for (pos in placed) helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true))
        val torch = base.above().west()
        helper.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST))
        val vines = (0 until 3).map { BlockPos(base.x + 4, 6 - it, base.z) }
        for (vine in vines) helper.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true))
        helper.setBlock(base.below().north(), Blocks.GRASS_BLOCK)
        val flower = base.north()
        helper.setBlock(flower, Blocks.DANDELION)
        val chest = BlockPos(14, 2, 14)
        helper.setBlock(chest, Blocks.CHEST)
        val rail = base.east()
        helper.setBlock(rail, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.ASCENDING_WEST))
        val frame = ItemFrame(helper.level, helper.absolutePos(base.above(2).east()), Direction.EAST)
        frame.setItem(ItemStack(Items.DIAMOND))
        helper.level.addFreshEntity(frame)
        // CD-LUM-09: each block's own canSurvive, against the level with the tree's cells empty.
        val plan = plan(helper)
        val shell = plan.steps.filter { it.shell }.map { TestKit.relative(helper, it.pos) }.toSet()
        helper.assertTrue(shell == (vines + torch + rail).toSet(), "CD-LUM-09, CD-LUM-30: the shell is $shell")
        val planned = plan.steps.map { TestKit.relative(helper, it.pos) }.toSet()
        helper.assertTrue(planned == (oak.logs + oak.leaves + vines + torch + rail).toSet(), "the plan holds ${planned.size} blocks")
        helper.assertTrue(frame.pos in plan.questions, "CD-LUM-30: the fell does not ask about the item frame's cell")
        val scyther = TestKit.tether(helper, pasture, owner, "scyther level=100")
        val magnemite = TestKit.tether(helper, pasture, owner, "magnemite level=50")
        val binding = TestKit.bind(helper, owner, scyther, base.above(3))
        helper.assertTrue(binding?.roleId == id("lumberjack"), "the Scyther bound as ${binding?.roleId}")
        helper.assertTrue(binding?.target == helper.absolutePos(base), "CD-LUM-03: the binding names ${binding?.target}, not the base cell")
        val magnet = TestKit.bind(helper, owner, magnemite, chest)
        helper.assertTrue(magnet?.roleId == id("magnet"), "the Magnemite bound as ${magnet?.roleId}")
        var felledAt = -1L
        helper.onEachTick {
            if (felledAt < 0) {
                if (helper.getBlockState(base).`is`(Blocks.OAK_LOG)) return@onEachTick
                felledAt = helper.tick
                // The base cell is the last step, and the replant comes in the tick of the last removal.
                for (log in oak.logs) helper.assertTrue(!helper.getBlockState(log).`is`(BlockTags.LOGS), "CD-LUM-14: a log stays at $log")
                for (leaf in oak.leaves) helper.assertTrue(helper.getBlockState(leaf).isAir, "CD-LUM-14: the natural leaf at $leaf stays")
                for (leaf in placed) {
                    helper.assertTrue(
                        helper.getBlockState(leaf).`is`(Blocks.OAK_LEAVES),
                        "CD-LUM-06: the placed leaf at $leaf broke",
                    )
                }
                for (cell in vines + torch + rail) {
                    helper.assertTrue(
                        helper.getBlockState(cell).isAir,
                        "CD-LUM-09: the block at $cell stays",
                    )
                }
                helper.assertTrue(frame.isRemoved, "CD-LUM-30: the item frame stays on the felled trunk")
                helper.assertTrue(helper.getBlockState(flower).`is`(Blocks.DANDELION), "CD-LUM-09: the flower beside the trunk broke")
                val replant = helper.getBlockState(base)
                helper.assertTrue(replant.`is`(Blocks.OAK_SAPLING), "CD-LUM-17: the base cell holds $replant after the fell")
                helper.assertTrue(BindingStore.read(scyther.pokemon) != null, "CD-LUM-19: the Scyther lost its binding at the fell")
                return@onEachTick
            }
            val collected = listOf(Items.OAK_LOG, Items.ITEM_FRAME, Items.DIAMOND, Items.RAIL).all {
                TestKit.countIn(helper, chest, it) > 0
            }
            if (collected) {
                helper.assertTrue(BindingStore.read(scyther.pokemon) != null, "CD-LUM-19: the Scyther lost its binding on the sapling")
                helper.succeed()
            } else {
                val held = listOf(Items.OAK_LOG, Items.ITEM_FRAME, Items.DIAMOND, Items.RAIL).associateWith {
                    TestKit.countIn(helper, chest, it)
                }
                helper.assertTrue(helper.tick - felledAt <= 200, "CD-LUM-22, CD-LUM-30: 200 ticks after the fell the chest holds $held")
            }
        }
    }

    /**
     * CD-LUM-12: the test claim provider refuses the break question of one leaf, and no block of the oak breaks. The fell asks each claim
     * mod once for each chunk and block id, about the first block of each in the order of the fell, so the test refuses that leaf.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 800)
    fun cdLum12RefusedLeafStopsTheFell(helper: GameTestHelper) {
        val (owner, pasture) = scene(helper)
        val oak = oak(helper)
        val refused = plan(helper).questions.first { helper.level.getBlockState(it).block is LeavesBlock }
        TestLandClaims.refusedBreak += refused
        TestKit.afterTest(helper) { TestLandClaims.refusedBreak -= refused }
        val scyther = TestKit.tether(helper, pasture, owner, "scyther level=100")
        val binding = TestKit.bind(helper, owner, scyther, base)
        helper.assertTrue(binding?.roleId == id("lumberjack"), "the Scyther bound as ${binding?.roleId}")
        var workingAt = -1L
        helper.onEachTick {
            if (workingAt < 0 && TestKit.workerOf(helper, scyther)?.status == WorkerStatus.WORKING) workingAt = helper.tick
            for (cell in oak.logs + oak.leaves) {
                helper.assertTrue(!helper.getBlockState(cell).isAir, "CD-LUM-12: the block at $cell broke, though the claim refuses a leaf")
            }
            // A level 100 worker fells 6 logs 30 ticks after its work starts.
            if (workingAt >= 0 && helper.tick - workingAt >= 60) helper.succeed()
        }
    }

    /**
     * CD-LUM-23, CD-LUM-24: the wood line. A level 100 Diglett grows the oak sapling of a level 100 Scyther's binding with bone meal, and
     * the Scyther fells the grown oak. GameTest chunks get no random ticks, so the oak grew from the Diglett's growth uses. No flower
     * stands within 2 blocks of the sapling, so the oak grows with no bee nest, which the never tag skips. The template leaves room above
     * the sapling for a fancy oak, which grows from 1 sapling in 10.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 3_600)
    fun cdLum23WoodLine(helper: GameTestHelper) {
        val (owner, pasture) = scene(helper)
        helper.setBlock(base.below(), Blocks.DIRT)
        helper.setBlock(base, Blocks.OAK_SAPLING)
        val sapling = helper.absolutePos(base)
        val since = helper.level.gameTime
        val scyther = TestKit.tether(helper, pasture, owner, "scyther level=100")
        val binding = TestKit.bind(helper, owner, scyther, base)
        helper.assertTrue(binding?.roleId == id("lumberjack"), "the Scyther bound as ${binding?.roleId}")
        helper.assertTrue(binding?.target == sapling, "CD-LUM-03: the binding names ${binding?.target}, not the sapling")
        TestKit.tether(helper, pasture, owner, "diglett level=100")
        var grown = false
        helper.onEachTick {
            if (!grown) {
                if (!helper.getBlockState(base).`is`(Blocks.OAK_LOG)) return@onEachTick
                grown = true
                val uses = BonemealUses.uses.filter { it.pos == sapling && it.tick >= since }
                val byDiglett = uses.isNotEmpty() && uses.all { it.player == null }
                helper.assertTrue(byDiglett, "CD-LUM-23: the oak grew with no growth use of the Diglett")
                return@onEachTick
            }
            if (oakLogs(helper) > 0) return@onEachTick
            helper.assertTrue(BindingStore.read(scyther.pokemon) != null, "CD-LUM-19: the Scyther lost its binding at the fell")
            helper.succeed()
        }
    }

    /** The oak logs around the base, up to the height of a fancy oak. */
    private fun oakLogs(helper: GameTestHelper): Int =
        BlockPos.betweenClosed(base.offset(-7, 0, -7), base.offset(7, 18, 7)).count { helper.getBlockState(it).`is`(Blocks.OAK_LOG) }
}
