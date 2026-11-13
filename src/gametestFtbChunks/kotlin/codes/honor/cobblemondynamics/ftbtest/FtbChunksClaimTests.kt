package codes.honor.cobblemondynamics.ftbtest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.compat.LandClaims
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import codes.honor.cobblemondynamics.test.claims.ClaimScenes
import codes.honor.cobblemondynamics.test.claims.PastureOracle
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties
import dev.ftb.mods.ftbchunks.api.Protection
import dev.ftb.mods.ftblibrary.math.ChunkDimPos
import dev.ftb.mods.ftbteams.api.TeamRank
import dev.ftb.mods.ftbteams.api.property.PrivacyMode
import dev.ftb.mods.ftbteams.data.PartyTeam
import dev.ftb.mods.ftbteams.data.TeamManagerImpl
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The claim checks against the real FTB Chunks 2101.1.23 (CD-QWK-27, CD-QWK-29, CD-QWK-31, CD-QWK-32, CD-LND-06, CD-LND-10, CD-HRV-17).
 * The party of player A claims the chunk of furnace A: B is a member, C an ally and D a stranger. Every player is of the exact class
 * ServerPlayer, which FTB Chunks judges as a player (CD-QWK-31). Each test has its own batch, because a claim covers a whole chunk.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class FtbChunksClaimTests {
    private class Scene(
        val a: TestKit.PlainOwner,
        val b: TestKit.PlainOwner,
        val c: TestKit.PlainOwner,
        val d: TestKit.PlainOwner,
        val party: PartyTeam,
        val furnace: BlockPos,
    ) {
        val players get() = listOf(a.player, b.player, c.player, d.player)
    }

    /** Furnace A, the four players, and the party of A. With [claim], the party claims the chunk of furnace A. */
    private fun scene(helper: GameTestHelper, claim: Boolean = true): Scene {
        val furnace = ClaimScenes.furnaceA(helper)
        val owners = listOf("a", "b", "c", "d").map { ClaimScenes.owner(helper, it) }
        val (a, b, c, d) = owners
        // FTB Chunks sets the claim limits of a team when its player logs in, so the login comes before the claim.
        FtbChunksFixture.login(*owners.map { it.player }.toTypedArray())
        val party = FtbChunksFixture.party(a.player, members = listOf(b.player), allies = listOf(c.player))
        TestKit.afterTest(helper) {
            FtbChunksFixture.unclaim(helper.level, furnace)
            FtbChunksFixture.disband(party, helper.level)
        }
        if (claim) FtbChunksFixture.claim(a.player, helper.level, furnace)
        return Scene(a, b, c, d, party, furnace)
    }

    /**
     * The facts of the claims plan that the run proves first: the server starts FTB Teams and FTB Chunks, a login gives a test player
     * its own team and sends its packets to the test connection, the party owner adds a member and an ally without FTB Ranks, and the
     * check of a right-click allows A, B and C in the default mode and prevents D.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "ftb_fixture")
    fun ftbFixtureAnswersLikeARightClick(helper: GameTestHelper) {
        helper.assertTrue(FTBChunksAPI.api().isManagerLoaded, "the GameTest server did not load the claim manager of FTB Chunks")
        val scene = scene(helper)
        for (owner in listOf(scene.a, scene.b, scene.c, scene.d)) {
            val name = owner.player.gameProfile.name
            helper.assertTrue(TeamManagerImpl.INSTANCE.getTeamForPlayer(owner.player).isPresent, "$name has no team after the login")
            helper.assertTrue(owner.packets.isNotEmpty(), "the login sent $name no packet")
        }
        helper.assertTrue(scene.party.getRankForPlayer(scene.b.player.uuid).isMemberOrBetter, "B is no member of the party")
        helper.assertTrue(scene.party.getRankForPlayer(scene.c.player.uuid) == TeamRank.ALLY, "C is no ally of the party")
        val answers = scene.players.map { FtbChunksFixture.allowsClick(it, scene.furnace) }
        helper.assertTrue(answers == listOf(true, true, true, false), "FTB Chunks allows A, B, C and D a right-click: $answers")
        helper.succeed()
    }

    /**
     * CD-LND-06: the pasture check of each setup equals the check of FTB Chunks for each online player, and gives the same answers
     * after the players log out. The setups: furnace A for all in the mode `allies`, for C in the mode `private`, for D in the mode
     * `public`, a crafting table of the whitelist in the claim for D, furnace A for D with the admin bypass, and a furnace in an
     * unclaimed chunk for D.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "ftb_lnd06")
    fun cdLnd06PastureCheckEqualsFtbChunksForEachPlayer(helper: GameTestHelper) {
        val scene = scene(helper)
        val manager = FTBChunksAPI.api().manager
        val dId = scene.d.player.uuid
        TestKit.afterTest(helper) { manager.setBypassProtection(dId, false) }
        // A neighbour of furnace A in the same chunk holds the crafting table.
        val tableRel = listOf(BlockPos(5, 2, 11), BlockPos(3, 2, 11)).first { ChunkPos(helper.absolutePos(it)) == ChunkPos(scene.furnace) }
        helper.setBlock(tableRel, Blocks.CRAFTING_TABLE)
        val table = helper.absolutePos(tableRel)
        val unclaimed = TestKit.furnace(helper, ClaimScenes.UNCLAIMED, ItemStack(Items.RAW_IRON, 8)).blockPos
        helper.assertTrue(manager.getChunk(ChunkDimPos(helper.level, unclaimed)) == null, "the chunk is claimed")

        class Setup(val name: String, val players: List<ServerPlayer>, val pos: BlockPos, val mode: PrivacyMode, val bypass: Boolean) {
            fun apply() {
                FtbChunksFixture.mode(scene.party, mode)
                manager.setBypassProtection(dId, bypass)
            }
        }
        val d = listOf(scene.d.player)
        val setups = listOf(
            Setup("furnace A in the mode allies", scene.players, scene.furnace, PrivacyMode.ALLIES, bypass = false),
            Setup("furnace A in the mode private", listOf(scene.c.player), scene.furnace, PrivacyMode.PRIVATE, bypass = false),
            Setup("furnace A in the mode public", d, scene.furnace, PrivacyMode.PUBLIC, bypass = false),
            Setup("the crafting table in the claim", d, table, PrivacyMode.ALLIES, bypass = false),
            Setup("furnace A with the admin bypass", d, scene.furnace, PrivacyMode.ALLIES, bypass = true),
            Setup("a furnace in an unclaimed chunk", d, unclaimed, PrivacyMode.ALLIES, bypass = false),
        )
        val answers = setups.map { setup ->
            setup.apply()
            PastureOracle.assertSame(helper, setup.players, setup.pos, setup.name) { FtbChunksFixture.allowsClick(it, setup.pos) }
        }
        PastureOracle.logOut(helper, scene.players)
        for ((setup, online) in setups.zip(answers)) {
            setup.apply()
            PastureOracle.assertSameOffline(helper, online)
        }
        helper.succeed()
    }

    /**
     * CD-LND-10: the online stranger P places a Work Mode pasture whose work area holds furnace A, in the claim of X's team. For 400
     * ticks the scan passes refuse furnace A, and P gets no chat or action-bar packet.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "ftb_lnd10")
    fun cdLnd10PasturePlacerGetsNoPacket(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val x = ClaimScenes.owner(helper, "x")
        val p = ClaimScenes.owner(helper, "p", rel = BlockPos(14, 2, 5))
        FtbChunksFixture.login(x.player, p.player)
        TestKit.afterTest(helper) { FtbChunksFixture.unclaim(helper.level, furnace) }
        FtbChunksFixture.claim(x.player, helper.level, furnace)
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

    /**
     * CD-HRV-17: the break question of a Quick Worker and the one of a pasture both equal the check of FTB Chunks on a block break for
     * each online player, on wheat at age 7 in the party's claim. The setups: the block edit mode `allies`, and the mode `private`, with
     * the interact mode kept. The pasture's question gives the same answers after the players log out.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "ftb_hrv17")
    fun cdHrv17FtbChunksBreakOracle(helper: GameTestHelper) {
        val scene = scene(helper)
        val wheat = PastureOracle.wheat(helper)
        val modes = listOf(PrivacyMode.ALLIES to "wheat in the edit mode allies", PrivacyMode.PRIVATE to "wheat in the edit mode private")
        val answers = modes.map { (mode, name) ->
            scene.party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, mode)
            PastureOracle.assertSame(helper, scene.players, wheat, name, PastureOracle.Question.BREAK) { allowsBreak(it, wheat) }
        }
        PastureOracle.logOut(helper, scene.players)
        for ((setup, online) in modes.zip(answers)) {
            scene.party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, setup.first)
            PastureOracle.assertSameOffline(helper, online)
        }
        helper.succeed()
    }

    /** The check of FTB Chunks on a block break, which shows the player its own text when it prevents (CD-HRV-17). */
    private fun allowsBreak(player: ServerPlayer, pos: BlockPos): Boolean =
        !FTBChunksAPI.api().manager.shouldPreventInteraction(player, InteractionHand.MAIN_HAND, pos, Protection.EDIT_BLOCK, null)

    /** A's, B's or C's send-out at furnace A in the default mode starts an order within 100 ticks (CD-QWK-27). */
    private fun getsAnOrder(helper: GameTestHelper, pick: (Scene) -> TestKit.PlainOwner) {
        val owner = pick(scene(helper)).player
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        ClaimScenes.sendOutAt(helper, owner, charmander)
        val start = helper.tick
        helper.onEachTick {
            if (ClaimScenes.hasOrder(helper, charmander)) {
                helper.succeed()
            } else {
                helper.assertTrue(
                    helper.tick - start < 100,
                    "CD-QWK-27: ${owner.gameProfile.name} got no order: ${TestKit.messages(owner)}",
                )
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "ftb_qwk27_owner")
    fun cdQwk27ClaimOwnerGetsAnOrder(helper: GameTestHelper) = getsAnOrder(helper) { it.a }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "ftb_qwk27_member")
    fun cdQwk27MemberGetsAnOrder(helper: GameTestHelper) = getsAnOrder(helper) { it.b }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "ftb_qwk27_ally")
    fun cdQwk27AllyGetsAnOrder(helper: GameTestHelper) = getsAnOrder(helper) { it.c }

    /**
     * Stranger D gets no order and no claim on any tick. In the tick of the refusal, FTB Chunks shows its own text, and the last
     * action-bar packet that D gets reads "You may not use Furnace" (CD-QWK-27, CD-QWK-32).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "ftb_qwk27_stranger")
    fun cdQwk27Qwk32StrangerGetsNoOrder(helper: GameTestHelper) {
        val scene = scene(helper)
        val d = scene.d
        val charmander = TestKit.partyPokemon(d.player, "charmander level=10")
        ClaimScenes.assertNoOrder(helper, "CD-QWK-27", charmander)
        ClaimScenes.sendOutAt(helper, d.player, charmander)
        helper.succeedWhen {
            helper.assertTrue(ClaimScenes.REFUSAL in TestKit.messages(d.player), "CD-QWK-27: D got ${TestKit.messages(d.player)}")
            val bars = TestKit.systemMessages(d, overlay = true)
            helper.assertTrue(bars.size >= 2, "CD-QWK-32: D's action bar showed $bars, not the text of FTB Chunks and then the refusal")
            helper.assertTrue(TestKit.lastActionBar(d) == ClaimScenes.REFUSAL, "CD-QWK-32: the last action bar of D reads ${bars.last()}")
        }
    }

    /** In the mode `private`, ally C's send-out gets no order (CD-QWK-27). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200, batch = "ftb_qwk27_private")
    fun cdQwk27AllyGetsNoOrderInPrivateMode(helper: GameTestHelper) {
        val scene = scene(helper)
        FtbChunksFixture.mode(scene.party, PrivacyMode.PRIVATE)
        val c = scene.c.player
        val charmander = TestKit.partyPokemon(c, "charmander level=10")
        ClaimScenes.assertNoOrder(helper, "CD-QWK-27", charmander)
        ClaimScenes.sendOutAt(helper, c, charmander)
        helper.succeedWhen { helper.assertTrue(ClaimScenes.REFUSAL in TestKit.messages(c), "CD-QWK-27: C got ${TestKit.messages(c)}") }
    }

    /** The order of [owner]'s Charmander Works furnace A, then [change] runs. 21 ticks later the order is gone, with the refusal (CD-QWK-29). */
    private fun losesTheOrder(helper: GameTestHelper, owner: ServerPlayer, change: () -> Unit) {
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        ClaimScenes.sendOutAt(helper, owner, charmander)
        var changedAt = -1L
        helper.onEachTick {
            if (changedAt < 0) {
                if (TestKit.engine(helper).worker(charmander.uuid)?.status != WorkerStatus.WORKING) return@onEachTick
                change()
                changedAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - changedAt
            if (age < 21) return@onEachTick
            helper.assertFalse(ClaimScenes.hasOrder(helper, charmander), "CD-QWK-29: the order outlived the change by $age ticks")
            helper.assertTrue(TestKit.lastMessage(owner) == ClaimScenes.REFUSAL, "CD-QWK-29: the owner got ${TestKit.messages(owner)}")
            helper.succeed()
        }
    }

    /** Member B's Charmander works furnace A, and B leaves the party (CD-QWK-29). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "ftb_qwk29_leave")
    fun cdQwk29MemberWhoLeavesThePartyStops(helper: GameTestHelper) {
        val scene = scene(helper)
        losesTheOrder(helper, scene.b.player) { scene.party.leave(scene.b.player.uuid) }
    }

    /** The chunk starts unclaimed, stranger D's Charmander works furnace A, and then the party of A claims the chunk (CD-QWK-29). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "ftb_qwk29_claim")
    fun cdQwk29ClaimOfARunningOrderStopsIt(helper: GameTestHelper) {
        val scene = scene(helper, claim = false)
        losesTheOrder(helper, scene.d.player) { FtbChunksFixture.claim(scene.a.player, helper.level, scene.furnace) }
    }
}
