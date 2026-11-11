package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * Quick Work (ADR-0013): a party Pokémon that its owner sends out at a furnace works it. The tests list fake players in the player
 * list, so they run in their own batch. Each test has one owner.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class QuickWorkTests {
    private class Scene(
        val owner: ServerPlayer,
        val charmander: Pokemon,
        val a: AbstractFurnaceBlockEntity,
        val b: AbstractFurnaceBlockEntity,
    )

    /** Furnaces A and B with 8 raw iron each, and a listed owner 10 blocks east of A who looks at A, with a Charmander in slot 0. */
    private fun scene(helper: GameTestHelper): Scene {
        val a = TestKit.furnace(helper, A, ItemStack(Items.RAW_IRON, 8))
        val b = TestKit.furnace(helper, B, ItemStack(Items.RAW_IRON, 8))
        val owner = TestKit.listedOwner(helper, OWNER)
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        TestKit.lookAt(helper, owner, A)
        return Scene(owner, charmander, a, b)
    }

    private fun claimedBy(helper: GameTestHelper, pokemon: Pokemon): BlockPos? =
        (TestKit.engine(helper).claims.claimOf(pokemon.uuid)?.key as? TargetKey.Block)?.let { BlockPos.of(it.pos) }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        // CD-QWK-01: the aim at the key press counts, so a turn to furnace B before the send-out ends changes nothing.
        helper.runAfterDelay(1) { TestKit.lookAt(helper, s.owner, B) }
        var workingSince = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (helper.tick == 100L && claimedBy(helper, s.charmander) == null) helper.fail("CD-QWK-02: no claim after 100 ticks")
            if (helper.tick == 400L && workingSince < 0) helper.fail("CD-QWK-02: not Working after 400 ticks")
            val worker = engine.worker(s.charmander.uuid) ?: return@onEachTick
            val entity = engine.entityOf(s.charmander.uuid) ?: return@onEachTick
            if (worker.status != WorkerStatus.WORKING) return@onEachTick
            if (workingSince < 0) workingSince = helper.tick
            // CD-QWK-12: Cobblemon's follow-owner task would walk it to its owner, who stands more than 5 blocks away.
            val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
            if (!WorkEngine.isArrived(entity, spot)) helper.fail("CD-QWK-12: the Charmander left its work spot at tick ${helper.tick}")
        }
        helper.succeedWhen {
            val engine = TestKit.engine(helper)
            helper.assertTrue(claimedBy(helper, s.charmander) == s.a.blockPos, "CD-QWK-01: the order is not on furnace A")
            helper.assertTrue(engine.claims.holderOf(TestKit.keyOf(helper, s.b.blockPos)) == null, "CD-QWK-01: furnace B is claimed")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander goes to work on Furnace"),
                "CD-QWK-11: ${TestKit.messages(s.owner)}",
            )
            helper.assertTrue(workingSince >= 0 && helper.tick - workingSince >= 200, "CD-QWK-12: fewer than 200 ticks of Working")
            val lit = helper.level.getBlockState(s.a.blockPos).getValue(AbstractFurnaceBlock.LIT)
            helper.assertTrue(lit, "CD-QWK-02: furnace A is not lit")
            helper.assertTrue((engine.worker(s.charmander.uuid)?.stamina?.fraction ?: 1.0) < 1.0, "CD-QWK-02: no Work Stamina drained")
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_400, batch = BATCH)
    fun cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        var movedAt = -1L
        var endedAt = -1L
        var distanceAtEnd = 0.0
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val entity = s.charmander.entity ?: return@onEachTick
            val tick = helper.tick
            when {
                movedAt < 0 -> if (engine.worker(s.charmander.uuid)?.status == WorkerStatus.WORKING) {
                    // CD-QWK-13: 17.5 blocks from the box of furnace A, past the default leash of 16.
                    val far = helper.absoluteVec(FAR.bottomCenter)
                    TestKit.place(s.owner, far.x, far.y, far.z)
                    movedAt = tick
                }

                endedAt < 0 -> if (!engine.isQuickWorker(s.charmander.uuid) && engine.claims.claimOf(s.charmander.uuid) == null) {
                    endedAt = tick
                    distanceAtEnd = entity.distanceTo(s.owner).toDouble()
                } else if (tick - movedAt > 2) {
                    helper.fail("CD-QWK-13: the order outlived the leash by ${tick - movedAt} ticks")
                }

                tick == endedAt + 100 -> {
                    helper.assertTrue(!BrainBridge.holdsOrder(entity), "CD-QWK-15: the Brain still holds a work order")
                    val closer = entity.distanceTo(s.owner) <= distanceAtEnd - 2
                    helper.assertTrue(closer, "CD-QWK-15: the Charmander does not follow its owner")
                    val near = helper.absoluteVec(NEAR.bottomCenter)
                    TestKit.place(s.owner, near.x, near.y, near.z)
                    TestKit.lookAt(helper, s.owner, A)
                }

                tick > endedAt + 100 -> {
                    // CD-QWK-14: the owner is back beside furnace A and looks at it, and nothing starts again.
                    val holder = engine.claims.holderOf(TestKit.keyOf(helper, s.a.blockPos))
                    helper.assertTrue(holder == null, "CD-QWK-14: furnace A is claimed again")
                    helper.assertTrue(engine.worker(s.charmander.uuid) == null, "CD-QWK-14: the engine still holds a slot")
                    helper.assertTrue(engine.aimOf(s.charmander.uuid) == null, "CD-QWK-14: the engine still holds an aim")
                }
            }
        }
        helper.succeedWhen {
            helper.assertTrue(endedAt >= 0 && helper.tick >= endedAt + 300, "the test runs 200 ticks past the owner's return")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander stopped working: you are too far from Furnace"),
                "CD-QWK-13: ${TestKit.messages(s.owner)}",
            )
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk16RecallEndsTheOrder(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        var recalledAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (recalledAt < 0) {
                if (engine.worker(s.charmander.uuid)?.status == WorkerStatus.WORKING) {
                    TestKit.pressSendOut(s.owner)
                    recalledAt = helper.tick
                }
            } else if (helper.tick - recalledAt > 2 && engine.claims.claimOf(s.charmander.uuid) != null) {
                helper.fail("CD-QWK-16: the order outlived the recall by ${helper.tick - recalledAt} ticks")
            }
        }
        helper.succeedWhen {
            helper.assertTrue(recalledAt >= 0 && helper.tick - recalledAt >= 40, "no recall yet")
            helper.assertTrue(s.charmander.entity == null, "Cobblemon did not recall the Charmander")
            helper.assertTrue(!TestKit.engine(helper).isQuickWorker(s.charmander.uuid), "CD-QWK-16: the order remains")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander stopped working: it was recalled"),
                "CD-QWK-16: ${TestKit.messages(s.owner)}",
            )
        }
    }

    private companion object {
        const val BATCH = "quick_work"
        val A = BlockPos(4, 2, 11)
        val B = BlockPos(4, 2, 6)
        val OWNER = BlockPos(14, 2, 11)
        val FAR = BlockPos(22, 2, 11)
        val NEAR = BlockPos(10, 2, 11)
    }
}
