package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SaplingBlock
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * CD-ASG-24, CD-ASG-25, R94: a worker never ends up inside the tree that grows at its target. The approver's report from COHO
 * (2026-11-19): "it looks like the lumberjacks/tree cultivators get stuck in the tree they grow because they stand on the tree." Each
 * scene checks the worker's body on the tick that the tree grows, after the engine's own work of that tick.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class GrowthSpotTests {
    /** An oak on dirt at [base], 6 logs high, with the natural leaves of `DualTypeTests`, so that its fell drops a sapling. */
    private fun oak(helper: GameTestHelper, base: BlockPos) {
        helper.setBlock(base.below(), Blocks.DIRT)
        val logs = (0 until 6).map { base.above(it) }
        for (log in logs) helper.setBlock(log, Blocks.OAK_LOG)
        fun layer(y: Int, radius: Int, round: Boolean) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val pos = BlockPos(base.x + dx, y, base.z + dz)
                    if ((round && abs(dx) == radius && abs(dz) == radius) || pos in logs) continue
                    helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState())
                }
            }
        }
        for (y in base.y + 3..base.y + 5) layer(y, 3, round = false)
        layer(base.y + 6, 3, round = true)
        layer(base.y + 7, 2, round = true)
    }

    /** Fails the test when the body of [entity] overlaps a block, which the tree at [tree] just filled. */
    private fun assertFree(helper: GameTestHelper, entity: PokemonEntity, tree: BlockPos, row: String) {
        val box = entity.boundingBox.deflate(1e-3)
        val at = TestKit.relative(helper, entity.blockPosition())
        val spot = TestKit.workerOf(helper, entity)?.spot?.let { TestKit.relative(helper, it) }
        helper.assertTrue(
            helper.level.noBlockCollision(entity, box),
            "$row: the ${entity.pokemon.species.name} (${entity.bbWidth} wide, ${entity.bbHeight} high) is inside the tree that grew at " +
                "$tree: it stands at $at, spot $spot, box $box",
        )
    }

    /**
     * CD-ASG-24, CD-LSH-30: a level 50 Nincada, Bug/Ground, at 3 times its size, so 1.2 blocks wide, as a worker wider than a block, bound
     * to an oak. It fells the oak, replants it, and grows the sapling as the Cultivator. From a cell beside the sapling its body reaches
     * into the sapling's cell, where the trunk grows. The new oak must not grow around the Nincada.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 4_000)
    fun cdAsg24NincadaGrowsItsOakFromBesideTheSapling(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val base = BlockPos(5, 2, 9)
        oak(helper, base)
        val nincada = TestKit.tether(helper, pasture, owner, "nincada level=50")
        nincada.pokemon.scaleModifier = 3f
        nincada.refreshDimensions()
        helper.assertTrue(nincada.bbWidth > 1f, "the Nincada is ${nincada.bbWidth} blocks wide")
        val bound = TestKit.bind(helper, owner, nincada, base.above(2))
        helper.assertTrue(bound?.roleId == id("lumberjack"), "CD-LSH-16: the Nincada bound to the oak as ${bound?.roleId}")
        var felled = false
        var replanted = false
        helper.onEachTick {
            val state = helper.getBlockState(base)
            if (!state.`is`(Blocks.OAK_LOG)) felled = true
            if (felled && state.`is`(Blocks.OAK_SAPLING)) replanted = true
            if (replanted && state.`is`(Blocks.OAK_LOG)) {
                assertFree(helper, nincada, base, "CD-ASG-24")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-24, CD-ASG-25: an unbound level 100 Torterra, Grass/Ground, 2.25 blocks wide and 2.4 high, grows a lone oak sapling as an
     * auto Cultivator. From a cell beside the sapling its body reaches into the sapling's cell, where the trunk grows, and the oak's
     * lowest leaves can reach its head wherever its spot is.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 3_000)
    fun cdAsg24TorterraGrowsAnOakAsAnAutoCultivator(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val sapling = BlockPos(6, 2, 9)
        helper.setBlock(sapling.below(), Blocks.DIRT)
        helper.setBlock(sapling, Blocks.OAK_SAPLING)
        val torterra = TestKit.tether(helper, pasture, owner, "torterra level=100")
        // Cobblemon's tether finds room for so wide a body on top of the floor's neighbours, so the test holds it on the floor east of
        // the sapling until its first claim, as `WideWorkerTests` holds its Camerupt.
        val hold = BlockPos(11, 2, 9)
        var claimed = false
        helper.onEachTick {
            if (!claimed && TestKit.claimOf(helper, torterra) == null) TestKit.moveTo(helper, torterra, hold) else claimed = true
            if (helper.getBlockState(sapling).`is`(Blocks.OAK_LOG)) {
                assertFree(helper, torterra, sapling, "CD-ASG-24, CD-ASG-25")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-24: a level 50 Caterpie, Bug, bound as the Lumberjack to a 2 × 2 of dark oak saplings, waits for its tree, and an unbound
     * level 100 Diglett grows the square as the Cultivator. The Caterpie comes from the side of the square's far corner, so before the
     * fix its nearest spot beside the corner was a sapling cell of the square, where the giant trunk grows.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 3_000)
    fun cdAsg24LumberjackWaitsOutsideAGiantTreesSquare(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val corner = BlockPos(7, 2, 9)
        val square = listOf(corner, corner.east(), corner.south(), corner.east().south())
        for (cell in square) {
            helper.setBlock(cell.below(), Blocks.DIRT)
            helper.setBlock(cell, Blocks.DARK_OAK_SAPLING)
        }
        val caterpie = TestKit.tether(helper, pasture, owner, "caterpie level=50")
        val bound = TestKit.bind(helper, owner, caterpie, corner.east().south())
        helper.assertTrue(bound?.roleId == id("lumberjack"), "CD-LSH-16: the Caterpie bound to the saplings as ${bound?.roleId}")
        helper.assertTrue(bound?.target == helper.absolutePos(corner), "CD-LUM-03: the binding names ${bound?.target}")
        val hold = BlockPos(11, 2, 12)
        var held = true
        var diglett: PokemonEntity? = null
        helper.onEachTick {
            if (held && TestKit.claimOf(helper, caterpie) == null) {
                TestKit.moveTo(helper, caterpie, hold)
            } else if (held) {
                held = false
                // The Cultivator comes once the Lumberjack holds its spot, so the order of the two claims is fixed.
                diglett = TestKit.tether(helper, pasture, owner, "diglett level=100")
            }
            if (diglett != null && helper.getBlockState(corner).`is`(Blocks.DARK_OAK_LOG)) {
                assertFree(helper, caterpie, corner, "CD-ASG-24")
                helper.succeed()
            }
        }
    }

    /** Waits until [entity] holds its Lumberjack claim in Standby, then runs [then] once, and checks [check] on each later tick. */
    private fun whenWaiting(helper: GameTestHelper, entity: PokemonEntity, hold: BlockPos, then: () -> Unit, check: () -> Unit) {
        var held = true
        var done = false
        helper.onEachTick {
            if (held && TestKit.claimOf(helper, entity) == null) TestKit.moveTo(helper, entity, hold) else held = false
            val worker = TestKit.workerOf(helper, entity)
            if (!done && worker?.status == WorkerStatus.STANDBY) {
                done = true
                then()
            } else if (done) {
                check()
            }
        }
    }

    /**
     * CD-ASG-24: a level 50 Caterpie bound as the Lumberjack to an oak sapling waits for its tree. Oak saplings stand east, south and
     * north of it, as in a hedge, and the Caterpie comes from the pasture in the south-east, so before the fix its nearest spot was a
     * neighbouring sapling's cell. The three neighbours then grow by a direct grow call, and the Caterpie must not be in a trunk.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdAsg24NoSpotInANeighbouringSaplingsCell(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val bound = BlockPos(6, 2, 9)
        val neighbours = listOf(bound.east(), bound.south(), bound.north())
        for (cell in neighbours + bound) {
            helper.setBlock(cell.below(), Blocks.DIRT)
            helper.setBlock(cell, Blocks.OAK_SAPLING)
        }
        val caterpie = TestKit.tether(helper, pasture, owner, "caterpie level=50")
        val binding = TestKit.bind(helper, owner, caterpie, bound)
        helper.assertTrue(binding?.roleId == id("lumberjack"), "CD-LSH-16: the Caterpie bound to the sapling as ${binding?.roleId}")
        var grownAt = -1L
        whenWaiting(helper, caterpie, BlockPos(11, 2, 9), then = {
            val spot = TestKit.workerOf(helper, caterpie)?.spot?.let { TestKit.relative(helper, it) }
            helper.assertTrue(spot !in neighbours, "CD-ASG-24: the Caterpie waits in the cell of a neighbouring sapling, $spot")
            for (neighbour in neighbours) {
                val pos = helper.absolutePos(neighbour)
                val state = helper.level.getBlockState(pos).setValue(SaplingBlock.STAGE, 1)
                (Blocks.OAK_SAPLING as SaplingBlock).advanceTree(helper.level, pos, state, helper.level.random)
            }
            grownAt = helper.tick
        }) {
            if (helper.tick >= grownAt + 2) {
                assertFree(helper, caterpie, bound, "CD-ASG-24")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-26: a level 50 Caterpie bound as the Lumberjack to an oak sapling waits beside it. The owner then plants a sapling in the
     * Caterpie's own cell and grows it with bone meal, as a player can. On the next engine tick the Caterpie must leave the trunk.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdAsg26PlayerGrowsATreeOnAWaitingLumberjack(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val bound = BlockPos(6, 2, 9)
        helper.setBlock(bound.below(), Blocks.DIRT)
        helper.setBlock(bound, Blocks.OAK_SAPLING)
        val caterpie = TestKit.tether(helper, pasture, owner, "caterpie level=50")
        val binding = TestKit.bind(helper, owner, caterpie, bound)
        helper.assertTrue(binding?.roleId == id("lumberjack"), "CD-LSH-16: the Caterpie bound to the sapling as ${binding?.roleId}")
        var grownAt = -1L
        var cell: BlockPos? = null
        whenWaiting(helper, caterpie, BlockPos(11, 2, 9), then = {
            val at = caterpie.blockPosition()
            cell = TestKit.relative(helper, at)
            helper.level.setBlockAndUpdate(at.below(), Blocks.DIRT.defaultBlockState())
            helper.level.setBlockAndUpdate(at, Blocks.OAK_SAPLING.defaultBlockState())
            var uses = 0
            while (!helper.level.getBlockState(at).`is`(Blocks.OAK_LOG) && uses++ < 200) {
                BoneMealItem.applyBonemeal(ItemStack(Items.BONE_MEAL), helper.level, at, owner)
            }
            helper.assertTrue(helper.level.getBlockState(at).`is`(Blocks.OAK_LOG), "the owner's oak did not grow in $uses uses")
            grownAt = helper.tick
        }) {
            if (helper.tick >= grownAt + 2) {
                assertFree(helper, caterpie, cell!!, "CD-ASG-26")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-28: a level 50 Caterpie bound as the Lumberjack to an oak sapling waits beside it, tires and rests in place, and its owner
     * grows an oak with bone meal in the Caterpie's cell. The net moves it out, and its rest holds it at the new place: 40 ticks later its
     * body still overlaps no block. Before the fix, the rest order put it back into the trunk on the next tick.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdAsg28ARestingWorkerStaysOutOfTheTreeThatGrewOnIt(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val bound = BlockPos(6, 2, 9)
        helper.setBlock(bound.below(), Blocks.DIRT)
        helper.setBlock(bound, Blocks.OAK_SAPLING)
        val caterpie = TestKit.tether(helper, pasture, owner, "caterpie level=50")
        val binding = TestKit.bind(helper, owner, caterpie, bound)
        helper.assertTrue(binding?.roleId == id("lumberjack"), "CD-LSH-16: the Caterpie bound to the sapling as ${binding?.roleId}")
        var held = true
        var phase = 0
        var grownAt = -1L
        var cell: BlockPos? = null
        helper.onEachTick {
            if (held && TestKit.claimOf(helper, caterpie) == null) TestKit.moveTo(helper, caterpie, BlockPos(11, 2, 9)) else held = false
            val worker = TestKit.workerOf(helper, caterpie) ?: return@onEachTick
            when (phase) {
                0 -> if (worker.status == WorkerStatus.STANDBY) {
                    worker.stamina.spend(1.0)
                    phase = 1
                }

                1 -> if (worker.status == WorkerStatus.RESTING_IN_PLACE) {
                    val at = caterpie.blockPosition()
                    cell = TestKit.relative(helper, at)
                    helper.level.setBlockAndUpdate(at.below(), Blocks.DIRT.defaultBlockState())
                    helper.level.setBlockAndUpdate(at, Blocks.OAK_SAPLING.defaultBlockState())
                    var uses = 0
                    while (!helper.level.getBlockState(at).`is`(Blocks.OAK_LOG) && uses++ < 200) {
                        BoneMealItem.applyBonemeal(ItemStack(Items.BONE_MEAL), helper.level, at, owner)
                    }
                    helper.assertTrue(helper.level.getBlockState(at).`is`(Blocks.OAK_LOG), "the owner's oak did not grow in $uses uses")
                    grownAt = helper.tick
                    phase = 2
                }

                2 -> if (helper.tick >= grownAt + 2) {
                    assertFree(helper, caterpie, cell!!, "CD-ASG-26")
                    val off = worker.restSpot?.distanceTo(caterpie.position())
                    helper.assertTrue(off != null && off <= 0.25, "CD-ASG-28: the rest spot lies $off blocks from the moved Caterpie")
                    phase = 3
                }

                else -> if (helper.tick >= grownAt + 40) {
                    helper.assertTrue(worker.status == WorkerStatus.RESTING_IN_PLACE, "CD-ASG-28: the Caterpie is ${worker.status}")
                    assertFree(helper, caterpie, cell!!, "CD-ASG-28")
                    helper.succeed()
                }
            }
        }
    }
}
