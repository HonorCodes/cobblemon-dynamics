package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.capability.FluidFillHandler
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Pump through NeoForge's real fluid capability, into Create's fluid tank and basin, and into a vanilla cauldron by its block
 * state. The tests place Create's blocks by id and read them through the capability, so they name no Create class.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class PumpTests {
    private val tankRel = BlockPos(3, 1, 3)

    private fun pasture(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return owner to pasture
    }

    private fun tank(helper: GameTestHelper, rel: BlockPos): IFluidHandler =
        helper.level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(rel), null)
            ?: throw GameTestAssertException("no fluid capability at $rel")

    /** The water in the first tank of the block at [rel]. A tank of any other fluid fails the test. */
    private fun water(helper: GameTestHelper, rel: BlockPos): Int {
        val stack = tank(helper, rel).getFluidInTank(0)
        helper.assertTrue(stack.isEmpty || stack.`is`(Fluids.WATER), "the tank at $rel holds $stack")
        return stack.amount
    }

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, rel: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, helper.absolutePos(rel)) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /** Binds [entity] to [rel] with the Work Leash and checks that the Pump role took it. */
    private fun bindPump(helper: GameTestHelper, owner: ServerPlayer, entity: PokemonEntity, rel: BlockPos) {
        val binding = TestKit.bind(helper, owner, entity, rel)
        val reply = Feedback.recorded[owner.uuid].orEmpty().lastOrNull()?.string
        helper.assertTrue(
            binding?.target == helper.absolutePos(rel) && binding.roleId == id("pump"),
            "CD-PMP-01: the bind to ${helper.getBlockState(rel).block} gave $binding and answered '$reply'",
        )
    }

    /**
     * One Lv10 Squirtle is bound to a Create fluid tank, then a basin, then a cauldron, and each bind takes (CD-PMP-01). On the
     * cauldron it delivers 50 mB per tick and raises the water level per 333 mB, so the cauldron is full after 20 ticks of work
     * (CD-PMP-04).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdPmp01BindsToATankABasinAndACauldron(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val basinRel = BlockPos(7, 1, 3)
        val cauldronRel = BlockPos(11, 1, 3)
        helper.setBlock(tankRel, TestKit.ae2Block("create:fluid_tank"))
        helper.setBlock(basinRel, TestKit.ae2Block("create:basin"))
        helper.setBlock(cauldronRel, Blocks.CAULDRON)
        val squirtle = TestKit.tether(helper, pasture, owner, "squirtle level=10")
        for (rel in listOf(tankRel, basinRel, cauldronRel)) bindPump(helper, owner, squirtle, rel)
        var workingSince = -1L
        helper.onEachTick {
            val state = helper.getBlockState(cauldronRel)
            val level = if (state.`is`(Blocks.WATER_CAULDRON)) state.getValue(LayeredCauldronBlock.LEVEL) else 0
            // The engine works after the block entities, so the first Working tick has delivered its 50 mB already.
            if (workingSince < 0) {
                if (!isWorkingOn(helper, squirtle, cauldronRel)) {
                    helper.assertTrue(level == 0, "the cauldron holds water before the Pump works it")
                    return@onEachTick
                }
                workingSince = helper.tick
            }
            if (level < LayeredCauldronBlock.MAX_FILL_LEVEL) return@onEachTick
            val ticks = helper.tick - workingSince + 1
            helper.assertTrue(ticks in 19L..21L, "CD-PMP-04: the Lv10 Pump filled the cauldron in $ticks ticks")
            helper.succeed()
        }
    }

    /** A Lv1 Pump gives 5 mB on each tick of work, so an empty tank holds 5,000 mB after 1,000 ticks (CD-PMP-03). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_600)
    fun cdPmp03Lv1PumpAdds5000MbIn1000Ticks(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        helper.setBlock(tankRel, TestKit.ae2Block("create:fluid_tank"))
        val squirtle = TestKit.tether(helper, pasture, owner, "squirtle level=1")
        bindPump(helper, owner, squirtle, tankRel)
        var workingSince = -1L
        helper.onEachTick {
            val stored = water(helper, tankRel)
            if (workingSince < 0) {
                if (!isWorkingOn(helper, squirtle, tankRel)) {
                    helper.assertTrue(stored == 0, "the tank holds $stored mB before the Pump works it")
                    return@onEachTick
                }
                workingSince = helper.tick
            }
            val ticks = helper.tick - workingSince + 1
            helper.assertTrue(isWorkingOn(helper, squirtle, tankRel), "the Lv1 Pump stopped after $ticks ticks")
            helper.assertTrue(stored.toLong() == 5L * ticks, "CD-PMP-03: after $ticks ticks of work the tank holds $stored mB")
            if (ticks == 1_000L) {
                helper.assertTrue(stored in 4_950..5_050, "CD-PMP-03: the Lv1 Pump added $stored mB in 1,000 ticks")
                helper.succeed()
            }
        }
    }

    /**
     * A Lv100 Pump gives 500 mB on each tick of work until the tank is full (CD-PMP-03). Then a simulated 1 mB fill fails, and the
     * worker is in Standby within 20 ticks, on its claim (CD-PMP-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        helper.setBlock(tankRel, TestKit.ae2Block("create:fluid_tank"))
        val squirtle = TestKit.tether(helper, pasture, owner, "squirtle level=100")
        bindPump(helper, owner, squirtle, tankRel)
        val capacity = tank(helper, tankRel).getTankCapacity(0)
        var workingSince = -1L
        var fullAt = -1L
        helper.onEachTick {
            val stored = water(helper, tankRel)
            if (workingSince < 0) {
                if (!isWorkingOn(helper, squirtle, tankRel)) {
                    helper.assertTrue(stored == 0, "the tank holds $stored mB before the Pump works it")
                    return@onEachTick
                }
                workingSince = helper.tick
            }
            val worker = TestKit.workerOf(helper, squirtle) ?: throw GameTestAssertException("the Squirtle is no worker")
            if (fullAt < 0) {
                val ticks = helper.tick - workingSince + 1
                val expected = minOf(capacity.toLong(), 500L * ticks)
                helper.assertTrue(stored.toLong() == expected, "CD-PMP-03: after $ticks ticks of work the tank holds $stored mB")
                if (stored == capacity) fullAt = helper.tick
            } else if (worker.status == WorkerStatus.STANDBY) {
                val claim = TestKit.claimOf(helper, squirtle)
                helper.assertTrue(claim?.key == TestKit.keyOf(helper, helper.absolutePos(tankRel)), "the Pump left its claim: $claim")
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - fullAt < 20, "CD-PMP-05: the Pump is ${worker.status} 20 ticks after its tank filled")
            }
        }
    }

    /**
     * Five real cauldrons: empty, water at level 2, water at level 3, lava and powder snow. The Pump handler gives demand for the first
     * two only (CD-PMP-06). Five Lv10 Squirtles bound one to each cauldron agree: the two with demand Work and fill to level 3, and the
     * three without wait in Standby and never Work, and their cauldrons stay as they were.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdPmp06FiveCauldronsOnRealBlocks(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val water2 = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2)
        val water3 = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
        val snow = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
        val cases = listOf(
            Triple("the empty cauldron", Blocks.CAULDRON.defaultBlockState(), true),
            Triple("the water cauldron at level 2", water2, true),
            Triple("the water cauldron at level 3", water3, false),
            Triple("the lava cauldron", Blocks.LAVA_CAULDRON.defaultBlockState(), false),
            Triple("the powder snow cauldron", snow, false),
        )
        val rels = cases.indices.map { BlockPos(3 + 2 * it, 1, 3) }
        for ((rel, case) in rels.zip(cases)) helper.setBlock(rel, case.second)
        val pumps = rels.map { rel ->
            val squirtle = TestKit.tether(helper, pasture, owner, "squirtle level=10")
            bindPump(helper, owner, squirtle, rel)
            squirtle
        }
        val pump = RoleRegistry.current.get(id("pump"))?.definition ?: throw GameTestAssertException("no Pump role")
        for ((index, case) in cases.withIndex()) {
            val entity = pumps[index]
            val ctx = WorkContext(helper.level, helper.absolutePos(rels[index]), entity, entity.pokemon, pump, helper.level.gameTime)
            val demand = FluidFillHandler.hasDemand(ctx)
            helper.assertTrue(demand == case.third, "CD-PMP-06: the Pump handler gives ${case.first} demand $demand")
        }
        val worked = BooleanArray(cases.size)
        helper.onEachTick {
            var settled = 0
            for ((index, case) in cases.withIndex()) {
                val (what, start, demand) = case
                val worker = TestKit.workerOf(helper, pumps[index]) ?: return@onEachTick
                if (worker.status == WorkerStatus.WORKING) worked[index] = true
                val state = helper.getBlockState(rels[index])
                if (demand) {
                    if (worked[index] && state == water3 && worker.status == WorkerStatus.STANDBY) settled++
                } else {
                    helper.assertFalse(worked[index], "CD-PMP-06: a Pump works $what")
                    helper.assertTrue(state == start, "CD-PMP-06: $what became $state")
                    if (worker.status == WorkerStatus.STANDBY && isOn(helper, pumps[index], rels[index])) settled++
                }
            }
            if (settled == cases.size) helper.succeed()
        }
    }

    private fun isOn(helper: GameTestHelper, entity: PokemonEntity, rel: BlockPos): Boolean =
        TestKit.claimOf(helper, entity)?.key == TestKit.keyOf(helper, helper.absolutePos(rel))

    /** The row's case: an unleashed Water-type and an empty Create fluid tank for 400 ticks. The Pump is leash only (CD-ASG-05). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdAsg05UnleashedWaterTypeLeavesAnEmptyTankAlone(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val besideRel = BlockPos(7, 1, 5)
        helper.setBlock(besideRel, TestKit.ae2Block("create:fluid_tank"))
        val squirtle = TestKit.tether(helper, pasture, owner, "squirtle level=30")
        var joinedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, squirtle) ?: return@onEachTick
            if (joinedAt < 0) joinedAt = helper.tick
            val claim = TestKit.claimOf(helper, squirtle)
            helper.assertTrue(claim == null, "CD-ASG-05: the unleashed Squirtle holds $claim")
            helper.assertTrue(worker.status == WorkerStatus.IDLE, "CD-ASG-05: the unleashed Squirtle is ${worker.status}")
            helper.assertTrue(water(helper, besideRel) == 0, "the tank took water")
            if (helper.tick - joinedAt >= 400) helper.succeed()
        }
    }
}
