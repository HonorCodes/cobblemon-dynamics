package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * CD-ASG-09 on real blocks with workers wider than one block. A work spot is one cell, but the engine puts a worker on its spot's
 * center, so a spot is standable for a worker only when the worker's whole body fits there.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WideWorkerTests {
    /**
     * The chosen spot of [entity], checked as CD-ASG-09 states: inside the work area of [pasture], with a clear line to [center], and
     * standable for the worker, which means room for its body where the engine puts it, on the spot's center.
     */
    private fun assertSpot(helper: GameTestHelper, entity: PokemonEntity, pasture: PokemonPastureBlockEntity, center: Vec3) {
        val worker = TestKit.workerOf(helper, entity) ?: throw GameTestAssertException("the ${entity.pokemon.species.name} is no worker")
        val spot = worker.spot ?: throw GameTestAssertException("a claim with no spot")
        val rel = TestKit.relative(helper, spot)
        val area = WorkArea.of(pasture.blockPos, WorkEngineSettings.current, PastureAdapter.maxWanderDistance())
        helper.assertTrue(area.contains(spot), "CD-ASG-09: the spot $rel lies outside the work area")
        val from = Vec3.atCenterOf(spot)
        val hit = helper.level.clip(ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
        val blocked = hit.type == HitResult.Type.BLOCK && hit.location.distanceTo(center) > 0.75
        helper.assertFalse(blocked, "CD-ASG-09: a block at ${hit.location} stands between the spot $rel and the target center")
        val half = entity.bbWidth / 2.0
        val at = Vec3.atBottomCenterOf(spot)
        val body = AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + entity.bbHeight, at.z + half).deflate(1e-3)
        val name = entity.pokemon.species.name
        helper.assertTrue(
            helper.level.noBlockCollision(entity, body),
            "CD-ASG-09: the spot $rel is not standable for the ${entity.bbWidth}-block-wide $name: its body there overlaps blocks",
        )
    }

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /**
     * A furnace with input stands on the floor, and two tall pillars leave a gap one block wide 5 blocks south of it, with a clear line to
     * the furnace. A Camerupt, 2.4 blocks wide, is held 8 blocks south of the furnace until it claims: the free cells nearest to it that
     * see the furnace are in and beside the gap, where its body overlaps the pillars. Its spot must have room for its body
     * (CD-ASG-09), and it must reach that spot and Work within 300 ticks.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg09WideWorkerGetsASpotItsBodyFits(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 10), owner)
        TestKit.workMode(pasture, true)
        val furnaceRel = BlockPos(7, 2, 2)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64)).blockPos
        // Four blocks high, so no cell on top of a pillar is in the Stoker's range.
        for (x in listOf(6, 8)) for (y in 2..5) helper.setBlock(BlockPos(x, y, 7), Blocks.STONE)
        val camerupt = TestKit.tether(helper, pasture, owner, "camerupt level=40")
        val hold = BlockPos(7, 2, 10)
        var claimedAt = -1L
        helper.onEachTick {
            if (claimedAt < 0) {
                if (TestKit.claimOf(helper, camerupt) == null) {
                    TestKit.moveTo(helper, camerupt, hold)
                    return@onEachTick
                }
                helper.assertTrue(camerupt.bbWidth > 2f, "the Camerupt is only ${camerupt.bbWidth} blocks wide")
                assertSpot(helper, camerupt, pasture, Vec3.atCenterOf(furnace))
                claimedAt = helper.tick
                return@onEachTick
            }
            if (isWorkingOn(helper, camerupt, furnace)) {
                helper.succeed()
                return@onEachTick
            }
            val waited = helper.tick - claimedAt
            val worker = TestKit.workerOf(helper, camerupt)
            val path = camerupt.navigation.path
            val walk = camerupt.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null)
            val nav = "spot ${worker?.spot?.let { TestKit.relative(helper, it) }}, path ${path?.nodeCount} nodes to ${path?.target}, " +
                "done ${path?.isDone}, reached ${path?.canReach()}, walk ${walk?.target?.currentBlockPosition()}"
            helper.assertTrue(
                waited < 300,
                "the Camerupt is ${worker?.status} at ${camerupt.position()} $waited ticks after its claim: $nav",
            )
        }
    }
}
