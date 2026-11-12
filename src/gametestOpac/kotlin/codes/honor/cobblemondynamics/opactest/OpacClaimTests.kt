package codes.honor.cobblemondynamics.opactest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.compat.LandClaims
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import codes.honor.cobblemondynamics.test.claims.ClaimScenes
import codes.honor.cobblemondynamics.test.claims.PastureOracle
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import xaero.pac.common.server.parties.party.api.IServerPartyAPI

/**
 * The claim checks against the real Open Parties and Claims 0.33.0 (CD-QWK-28, CD-QWK-29, CD-QWK-31, CD-QWK-32, CD-LND-07,
 * CD-LND-10). Player A claims the chunk of furnace A: B is a member of A's party, C a member of a party that A's party allies, and D a
 * stranger. Every player is of the exact class ServerPlayer (CD-QWK-31). Each test has its own batch, because a claim covers a chunk.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class OpacClaimTests {
    private class Scene(
        val a: TestKit.PlainOwner,
        val b: TestKit.PlainOwner,
        val c: TestKit.PlainOwner,
        val d: TestKit.PlainOwner,
        val party: IServerPartyAPI,
        val furnace: BlockPos,
    ) {
        val players get() = listOf(a.player, b.player, c.player, d.player)
    }

    /** Furnace A, the four players, A's party with B, and C's party, which A's party allies. A claims the chunk of furnace A. */
    private fun scene(helper: GameTestHelper): Scene {
        val furnace = ClaimScenes.furnaceA(helper)
        val (a, b, c, d) = listOf("a", "b", "c", "d").map { ClaimScenes.owner(helper, it) }
        val party = OpacFixture.party(a.player, members = listOf(b.player))
        val allied = OpacFixture.party(c.player, members = emptyList())
        party.addAllyParty(allied.id)
        TestKit.afterTest(helper) {
            OpacFixture.unclaim(helper.level, furnace)
            OpacFixture.fullAccess(a.player, "P")
            OpacFixture.removeParty(helper.level, party)
            OpacFixture.removeParty(helper.level, allied)
        }
        OpacFixture.claim(a.player, helper.level, furnace)
        return Scene(a, b, c, d, party, furnace)
    }

    /**
     * The facts of the claims plan that the run proves first: the server starts Open Parties and Claims, a test player gets a party,
     * and the check of a right-click with an empty hand allows A and B and prevents D.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "opac_fixture")
    fun opacFixtureAnswersLikeARightClick(helper: GameTestHelper) {
        val scene = scene(helper)
        val answers = listOf(scene.a, scene.b, scene.d).map { OpacFixture.allowsClick(it.player, scene.furnace) }
        helper.assertTrue(answers == listOf(true, true, false), "Open Parties and Claims allows A, B and D a right-click: $answers")
        helper.succeed()
    }

    /**
     * CD-LND-07: the pasture check equals the check of Open Parties and Claims for each online player, on furnace A with the default
     * `fullAccess` of `P`, and for ally C with `A`, and gives the same answers after the players log out.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "opac_lnd07")
    fun cdLnd07PastureCheckEqualsFullAccessForEachPlayer(helper: GameTestHelper) {
        val scene = scene(helper)
        val setups = listOf("P" to scene.players, "A" to listOf(scene.c.player))
        val answers = setups.map { (access, players) ->
            OpacFixture.fullAccess(scene.a.player, access)
            PastureOracle.assertSame(helper, players, scene.furnace, "furnace A with fullAccess $access") {
                OpacFixture.allowsClick(it, scene.furnace)
            }
        }
        PastureOracle.logOut(helper, scene.players)
        for ((setup, online) in setups.zip(answers)) {
            OpacFixture.fullAccess(scene.a.player, setup.first)
            PastureOracle.assertSameOffline(helper, online)
        }
        helper.succeed()
    }

    /**
     * CD-LND-10: the online stranger P places a Work Mode pasture whose work area holds furnace A, in the claim of X. For 400 ticks the
     * scan passes refuse furnace A, and P gets no chat or action-bar packet.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "opac_lnd10")
    fun cdLnd10PasturePlacerGetsNoPacket(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val x = ClaimScenes.owner(helper, "x")
        val p = ClaimScenes.owner(helper, "p", rel = BlockPos(14, 2, 5))
        TestKit.afterTest(helper) { OpacFixture.unclaim(helper.level, furnace) }
        OpacFixture.claim(x.player, helper.level, furnace)
        helper.assertFalse(LandClaims.ownerMayUse(p.player.uuid, helper.level, furnace), "the pasture check allows P furnace A")
        val (_, charmander) = ClaimScenes.pastureOf(helper, p.player)
        val from = p.packets.size
        val start = helper.tick
        helper.onEachTick {
            val claim = TestKit.claimOf(helper, charmander)
            helper.assertFalse(claim?.key == TestKit.keyOf(helper, furnace), "CD-LND-01: P's Charmander claimed furnace A")
            val messages = ClaimScenes.messagePackets(p, from)
            helper.assertTrue(messages.isEmpty(), "CD-LND-10: the placer got $messages")
            if (helper.tick - start >= 400) helper.succeed()
        }
    }

    /** [owner]'s send-out at furnace A starts an order within 100 ticks (CD-QWK-28). */
    private fun getsAnOrder(helper: GameTestHelper, owner: ServerPlayer) {
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        ClaimScenes.sendOutAt(helper, owner, charmander)
        val start = helper.tick
        helper.onEachTick {
            if (ClaimScenes.hasOrder(helper, charmander)) {
                helper.succeed()
            } else {
                helper.assertTrue(
                    helper.tick - start < 100,
                    "CD-QWK-28: ${owner.gameProfile.name} got no order: ${TestKit.messages(owner)}",
                )
            }
        }
    }

    /** [owner]'s send-out at furnace A gets no order and no claim on any tick, and the refusal (CD-QWK-28). */
    private fun getsNoOrder(helper: GameTestHelper, owner: ServerPlayer) {
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        ClaimScenes.assertNoOrder(helper, "CD-QWK-28", charmander)
        ClaimScenes.sendOutAt(helper, owner, charmander)
        helper.succeedWhen {
            helper.assertTrue(ClaimScenes.REFUSAL in TestKit.messages(owner), "CD-QWK-28: got ${TestKit.messages(owner)}")
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "opac_qwk28_owner")
    fun cdQwk28ClaimOwnerGetsAnOrder(helper: GameTestHelper) = getsAnOrder(helper, scene(helper).a.player)

    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "opac_qwk28_member")
    fun cdQwk28MemberGetsAnOrder(helper: GameTestHelper) = getsAnOrder(helper, scene(helper).b.player)

    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "opac_qwk28_ally")
    fun cdQwk28AllyGetsNoOrderByDefault(helper: GameTestHelper) = getsNoOrder(helper, scene(helper).c.player)

    /** After A sets `fullAccess` to `A`, the allied party's member C gets an order (CD-QWK-28). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "opac_qwk28_ally_access")
    fun cdQwk28AllyGetsAnOrderWithFullAccessForAllies(helper: GameTestHelper) {
        val scene = scene(helper)
        OpacFixture.fullAccess(scene.a.player, "A")
        getsAnOrder(helper, scene.c.player)
    }

    /**
     * Stranger D gets no order, the last action-bar packet reads "You may not use Furnace", and D gets no chat message, because the check
     * runs with its messages off (CD-QWK-28, CD-QWK-32).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "opac_qwk28_stranger")
    fun cdQwk28Qwk32StrangerGetsNoOrder(helper: GameTestHelper) {
        val d = scene(helper).d
        getsNoOrder(helper, d.player)
        helper.onEachTick {
            val chat = TestKit.systemMessages(d, overlay = false)
            helper.assertTrue(chat.isEmpty(), "CD-QWK-32: Open Parties and Claims sent D the chat messages $chat")
            if (ClaimScenes.REFUSAL in TestKit.messages(d.player)) {
                helper.assertTrue(
                    TestKit.lastActionBar(d) == ClaimScenes.REFUSAL,
                    "CD-QWK-32: D's last action bar is ${TestKit.lastActionBar(d)}",
                )
            }
        }
    }

    /** B's Charmander works furnace A, and A's party removes B. 21 ticks later the order is gone, with the refusal (CD-QWK-29). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "opac_qwk29_removed")
    fun cdQwk29RemovedMemberStops(helper: GameTestHelper) {
        val scene = scene(helper)
        val b = scene.b.player
        val charmander = TestKit.partyPokemon(b, "charmander level=10")
        ClaimScenes.sendOutAt(helper, b, charmander)
        var removedAt = -1L
        helper.onEachTick {
            if (removedAt < 0) {
                if (TestKit.engine(helper).worker(charmander.uuid)?.status != WorkerStatus.WORKING) return@onEachTick
                scene.party.removeMember(b.uuid)
                removedAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - removedAt
            if (age < 21) return@onEachTick
            helper.assertFalse(ClaimScenes.hasOrder(helper, charmander), "CD-QWK-29: the order outlived the removal by $age ticks")
            helper.assertTrue(TestKit.lastMessage(b) == ClaimScenes.REFUSAL, "CD-QWK-29: B got ${TestKit.messages(b)}")
            helper.succeed()
        }
    }
}
