package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * Quick Work asks the loaded claim mods (CD-QWK-26, CD-QWK-29, CD-QWK-30), here the test mod's claim provider, [TestLandClaims], which
 * guards the absolute positions that a test adds. The scene is that of `QuickWorkTests`: furnace A with raw iron, and a listed owner
 * 10 blocks east of A who looks at A. The tests list fake players in the player list, so they run in their own batch.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class QuickWorkClaimTests {
    private fun owner(helper: GameTestHelper): ServerPlayer {
        TestKit.furnace(helper, A, ItemStack(Items.RAW_IRON, 8))
        val owner = TestKit.listedOwner(helper, OWNER)
        TestKit.lookAt(helper, owner, A)
        return owner
    }

    /** Furnace A in [set] until the test ends. */
    private fun guard(helper: GameTestHelper, set: MutableSet<BlockPos>): BlockPos {
        val a = helper.absolutePos(A)
        set += a
        TestKit.afterTest(helper) { set -= a }
        return a
    }

    private fun refusals(owner: ServerPlayer) = TestKit.messages(owner).count { it == REFUSAL }

    /**
     * The claim provider refuses furnace A, and the owner sends out a Charmander at it: no order, no claim, and the owner learns that it
     * may not use the furnace. Then the provider allows furnace A, and a second Charmander gets an order within 100 ticks (CD-QWK-26).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 500, batch = BATCH)
    fun cdQwk26RefusedClaimGivesNoOrder(helper: GameTestHelper) {
        val owner = owner(helper)
        val first = TestKit.partyPokemon(owner, "charmander level=10")
        val second = TestKit.partyPokemon(owner, "charmander level=10")
        val a = guard(helper, TestLandClaims.refused)
        assertNoOrder(helper, "CD-QWK-26", first)
        var allowedAt = -1L
        helper.onEachTick {
            val late = allowedAt >= 0 && helper.tick - allowedAt > 100 && !TestKit.engine(helper).isQuickWorker(second.uuid)
            if (late) helper.fail("CD-QWK-26: the second Charmander got no order within 100 ticks of the change")
        }
        helper.startSequence()
            .thenExecute { sendOutAt(helper, owner, first, A, slot = 0) }
            .thenWaitUntil { helper.assertTrue(refusals(owner) == 1, "CD-QWK-26: the refused owner got ${TestKit.messages(owner)}") }
            .thenExecute {
                TestLandClaims.refused -= a
                allowedAt = helper.tick
                sendOutAt(helper, owner, second, A, slot = 1)
            }
            .thenWaitUntil { helper.assertTrue(TestKit.engine(helper).isQuickWorker(second.uuid), "the second Charmander has no order") }
            .thenSucceed()
    }

    /** The claim provider starts to refuse furnace A on the first Working tick of the order. 21 ticks later the order is gone (CD-QWK-29). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = BATCH)
    fun cdQwk29LostPermissionEndsTheOrder(helper: GameTestHelper) {
        val owner = owner(helper)
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        val a = helper.absolutePos(A)
        TestKit.afterTest(helper) { TestLandClaims.refused -= a }
        sendOutAt(helper, owner, charmander, A)
        var refusedAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (refusedAt < 0) {
                if (engine.worker(charmander.uuid)?.status != WorkerStatus.WORKING) return@onEachTick
                TestLandClaims.refused += a
                refusedAt = helper.tick
            } else if (helper.tick - refusedAt >= 21 && engine.isQuickWorker(charmander.uuid)) {
                helper.fail("CD-QWK-29: the order outlived its permission by ${helper.tick - refusedAt} ticks")
            }
        }
        helper.succeedWhen {
            helper.assertTrue(refusedAt >= 0 && helper.tick - refusedAt >= 21, "the provider has not refused furnace A for 21 ticks yet")
            helper.assertTrue(refusals(owner) == 1, "CD-QWK-29: the owner got ${TestKit.messages(owner)}")
        }
    }

    /** The claim provider throws for furnace A: the send-out gets no order and no claim, and the owner gets the refusal (CD-QWK-30). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = BATCH)
    fun cdQwk30ThrowingCheckRefuses(helper: GameTestHelper) {
        val owner = owner(helper)
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        guard(helper, TestLandClaims.throwing)
        sendOutAt(helper, owner, charmander, A)
        assertNoOrder(helper, "CD-QWK-30", charmander)
        helper.succeedWhen { helper.assertTrue(refusals(owner) == 1, "CD-QWK-30: the owner got ${TestKit.messages(owner)}") }
    }

    /** On every tick, none of [pokemon] has an order or a claim of any kind, so each send-out stays a normal one. */
    private fun assertNoOrder(helper: GameTestHelper, row: String, vararg pokemon: Pokemon) {
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            for (p in pokemon) {
                helper.assertFalse(engine.isQuickWorker(p.uuid), "$row: the ${p.species.name} got an order")
                val claim = engine.claims.claimOf(p.uuid)
                helper.assertTrue(claim == null, "$row: the ${p.species.name} holds $claim")
            }
        }
    }

    /** Presses R for party [slot] while [owner] looks at [rel], and checks that the send-out of [pokemon] aimed at that block. */
    private fun sendOutAt(helper: GameTestHelper, owner: ServerPlayer, pokemon: Pokemon, rel: BlockPos, slot: Int = 0) {
        TestKit.lookAt(helper, owner, rel)
        TestKit.pressSendOut(owner, slot)
        val aimed = TestKit.engine(helper).aimOf(pokemon.uuid)?.pos
        helper.assertTrue(aimed == helper.absolutePos(rel), "the send-out of the ${pokemon.species.name} aimed at $aimed, not at $rel")
    }

    private companion object {
        const val BATCH = "quick_work_claims"
        const val REFUSAL = "You may not use Furnace"
        val A = BlockPos(4, 2, 11)
        val OWNER = BlockPos(14, 2, 11)
    }
}
