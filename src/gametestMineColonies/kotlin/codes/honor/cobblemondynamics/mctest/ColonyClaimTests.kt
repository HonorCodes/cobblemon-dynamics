package codes.honor.cobblemondynamics.mctest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.compat.LandClaims
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import codes.honor.cobblemondynamics.test.claims.ClaimScenes
import codes.honor.cobblemondynamics.test.claims.PastureOracle
import com.cobblemon.mod.common.pokemon.Pokemon
import com.minecolonies.api.colony.IColony
import com.minecolonies.api.colony.IColonyManager
import com.minecolonies.api.colony.permissions.Action
import com.minecolonies.api.colony.permissions.Rank
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The claim checks against the real MineColonies 1.1.1420 (CD-LND-08, CD-LND-09, CD-LND-10, CD-HRV-19, and CD-QWK-26 and CD-QWK-30
 * for MineColonies). Player A owns a colony around the test's center: B is an Officer, C a Friend, D a stranger, whom the colony gives
 * the Neutral rank, and E is Hostile. The colony's own decision is the cancellation of an event that the test posts: a `RightClickBlock`
 * for a use, and a `BreakEvent` for a break. Each test has its own batch, because a colony claims the chunks around its center.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ColonyClaimTests {
    private class Scene(val colony: IColony, val owners: List<TestKit.PlainOwner>) {
        val players get() = owners.map { it.player }
    }

    /**
     * A's colony and the players A to E at the plain owners' cells in [rows], each looking at its own block at x = 4 in its row, with
     * the ranks Owner, Officer, Friend, Neutral and Hostile.
     */
    private fun scene(helper: GameTestHelper, rows: List<Int> = List(5) { 11 }): Scene {
        val owners = listOf("a", "b", "c", "d", "e").zip(rows).map { (name, z) ->
            ClaimScenes.owner(helper, name, rel = BlockPos(14, 2, z), at = BlockPos(4, 2, z))
        }
        val colony = ColonyFixture.colony(helper, owners[0].player)
        ColonyFixture.rank(colony, owners[1].player) { it.rankOfficer }
        ColonyFixture.rank(colony, owners[2].player) { it.rankFriend }
        ColonyFixture.rank(colony, owners[4].player) { it.rankHostile }
        return Scene(colony, owners)
    }

    /** The first fact of the plan's "Inferred" list: the colony of plain owner A holds furnace A in the tick that made it. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "mc_fixture_colony")
    fun mcFixtureColonyHoldsFurnaceAInTheSameTick(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val a = ClaimScenes.owner(helper, "a")
        val colony = ColonyFixture.colony(helper, a.player)
        val found = IColonyManager.getInstance().getColonyByPosFromWorld(helper.level, furnace)
        helper.assertTrue(found?.id == colony.id, "the colony at furnace A is ${found?.id}, not ${colony.id}")
        helper.assertTrue(colony.isCoordInColony(helper.level, furnace), "furnace A is not in the colony's land")
        helper.succeed()
    }

    /** The second fact: a stranger's posted click on furnace A reaches the colony's event handler, which cancels it, and A's passes. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "mc_fixture_click")
    fun mcFixtureStrangersClickIsCanceled(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val a = ClaimScenes.owner(helper, "a")
        val d = ClaimScenes.owner(helper, "d")
        ColonyFixture.colony(helper, a.player)
        helper.assertTrue(ColonyFixture.colonyAllows(a.player, furnace), "the colony canceled its owner's click on furnace A")
        helper.assertFalse(ColonyFixture.colonyAllows(d.player, furnace), "the colony did not cancel a stranger's click on furnace A")
        helper.succeed()
    }

    /** The third fact: the default flags of the ranks, by the order of the defaults in `Permissions`. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "mc_fixture_ranks")
    fun mcFixtureDefaultRankFlags(helper: GameTestHelper) {
        val a = ClaimScenes.owner(helper, "a")
        val permissions = ColonyFixture.colony(helper, a.player).permissions
        val flags = listOf(permissions.rankOfficer, permissions.rankFriend, permissions.rankNeutral).map { rank ->
            listOf(Action.RIGHTCLICK_BLOCK, Action.OPEN_CONTAINER).map { permissions.hasPermission(rank, it) }
        }
        val expected = listOf(listOf(true, true), listOf(true, false), listOf(false, false))
        helper.assertTrue(flags == expected, "RIGHTCLICK_BLOCK and OPEN_CONTAINER of Officer, Friend and Neutral are $flags, not $expected")
        helper.succeed()
    }

    /**
     * CD-LND-08: the pasture check equals the colony's own decision for each online player, on furnace A and on an enchanting table
     * with the default rank permissions, and for C after A gives the Friend rank OPEN_CONTAINER, and gives the same answers after the
     * players log out.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "mc_lnd08")
    fun cdLnd08PastureCheckEqualsTheColonyForEachRank(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val tableRel = BlockPos(4, 2, 14)
        helper.setBlock(tableRel, Blocks.ENCHANTING_TABLE)
        val table = helper.absolutePos(tableRel)
        val scene = scene(helper)
        val permissions = scene.colony.permissions
        val friend = permissions.rankFriend
        val c = listOf(scene.owners[2].player)
        val setups = listOf(
            Triple("furnace A with the default ranks", scene.players to furnace, false),
            Triple("the enchanting table with the default ranks", scene.players to table, false),
            Triple("furnace A with OPEN_CONTAINER for Friends", c to furnace, true),
        )
        val answers = setups.map { (name, at, open) ->
            permissions.setPermission(friend, Action.OPEN_CONTAINER, open)
            PastureOracle.assertSame(helper, at.first, at.second, name) { ColonyFixture.colonyAllows(it, at.second) }
        }
        PastureOracle.logOut(helper, scene.players)
        for ((setup, online) in setups.zip(answers)) {
            permissions.setPermission(friend, Action.OPEN_CONTAINER, setup.third)
            PastureOracle.assertSameOffline(helper, online)
        }
        permissions.setPermission(friend, Action.OPEN_CONTAINER, false)
        helper.succeed()
    }

    /** One turn of CD-LND-09: [setup] gives the owner a rank or a game mode, then the owner sends out the next Charmander at furnace A. */
    private class Turn(val name: String, val setup: () -> Unit)

    /**
     * The owner holds each setup of [turns] in turn and sends out a new Charmander at furnace A each time. Each send-out starts an
     * order within 100 ticks exactly where the colony does not cancel the click that the test posts for the owner, and otherwise gets
     * no order on any tick and "You may not use Furnace" (CD-LND-09). An order that a later turn would find on furnace A ends first.
     */
    private fun inTurn(helper: GameTestHelper, owner: ServerPlayer, furnace: BlockPos, turns: List<Turn>) {
        val charmanders = turns.map { TestKit.partyPokemon(owner, "charmander level=10") }
        val refused = ArrayList<Pokemon>()
        var step = -1
        var allowed = false
        var sentAt = 0L
        var refusalsBefore = 0
        fun refusals() = TestKit.messages(owner).count { it == ClaimScenes.REFUSAL }
        fun next() {
            step++
            turns[step].setup()
            allowed = ColonyFixture.colonyAllows(owner, furnace)
            refusalsBefore = refusals()
            ClaimScenes.sendOutAt(helper, owner, charmanders[step], slot = step)
            sentAt = helper.tick
        }
        next()
        var recalling = false
        helper.onEachTick {
            for (pokemon in refused) helper.assertFalse(ClaimScenes.hasOrder(helper, pokemon), "CD-LND-09: a refused send-out got an order")
            val turn = turns[step]
            val charmander = charmanders[step]
            val ordered = ClaimScenes.hasOrder(helper, charmander)
            if (recalling) {
                if (ordered) return@onEachTick
                recalling = false
                return@onEachTick next()
            }
            val age = helper.tick - sentAt
            if (allowed) {
                if (!ordered) {
                    return@onEachTick helper.assertTrue(
                        age < 100,
                        "CD-LND-09: ${turn.name} got no order: ${TestKit.messages(owner)}",
                    )
                }
            } else {
                helper.assertFalse(ordered, "CD-LND-09: ${turn.name} got an order, although the colony cancels the click")
                if (refusals() == refusalsBefore) return@onEachTick helper.assertTrue(age < 100, "CD-LND-09: ${turn.name} got no refusal")
                refused += charmander
            }
            when {
                step == turns.lastIndex -> helper.succeed()

                // The order of this turn holds furnace A, so the Charmander goes back before the next send-out.
                allowed -> {
                    TestKit.pressSendOut(owner, step)
                    recalling = true
                }

                else -> next()
            }
        }
    }

    /**
     * CD-LND-09: the owner of the send-outs is a Friend, a stranger with the Neutral rank, a Hostile player and an Officer of A's
     * colony in turn, and sends out a Charmander at furnace A each time.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "mc_lnd09_ranks")
    fun cdLnd09QuickWorkFollowsTheOwnersClickInTheColony(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val a = ClaimScenes.owner(helper, "a")
        val b = ClaimScenes.owner(helper, "b").player
        val colony = ColonyFixture.colony(helper, a.player)
        val permissions = colony.permissions
        fun rank(rank: Rank) = permissions.setPlayerRank(b.uuid, rank, helper.level)
        ColonyFixture.rank(colony, b) { it.rankFriend }
        val turns = listOf(
            Turn("a Friend") { rank(permissions.rankFriend) },
            Turn("a stranger") { permissions.removePlayer(b.uuid) },
            Turn("a Hostile player") { ColonyFixture.rank(colony, b) { it.rankHostile } },
            Turn("an Officer") { rank(permissions.rankOfficer) },
        )
        inTurn(helper, b, furnace, turns)
    }

    /**
     * CD-LND-09: a stranger to A's colony who is an operator of level 2 gets no order in survival mode, and gets one in creative mode, as
     * that player's own click does.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "mc_lnd09_operator")
    fun cdLnd09OperatorInCreativeModePassesAndInSurvivalDoesNot(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val a = ClaimScenes.owner(helper, "a")
        ColonyFixture.colony(helper, a.player)
        val operator = ClaimScenes.owner(helper, "op", permissionLevel = 2).player
        val turns = listOf(
            Turn("an operator in survival mode") { operator.setGameMode(GameType.SURVIVAL) },
            Turn("an operator in creative mode") { operator.setGameMode(GameType.CREATIVE) },
        )
        inTurn(helper, operator, furnace, turns)
    }

    /** CD-LND-09, CD-QWK-29: A lowers Officer B to Neutral while B's order runs. 21 ticks later the order is gone, with the refusal. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "mc_lnd09_lowered")
    fun cdLnd09LoweredRankEndsTheOrderWithin20Ticks(helper: GameTestHelper) {
        ClaimScenes.furnaceA(helper)
        val scene = scene(helper)
        val b = scene.owners[1].player
        val charmander: Pokemon = TestKit.partyPokemon(b, "charmander level=10")
        ClaimScenes.sendOutAt(helper, b, charmander)
        var loweredAt = -1L
        helper.onEachTick {
            if (loweredAt < 0) {
                if (TestKit.engine(helper).worker(charmander.uuid)?.status != WorkerStatus.WORKING) return@onEachTick
                scene.colony.permissions.setPlayerRank(b.uuid, scene.colony.permissions.rankNeutral, helper.level)
                loweredAt = helper.tick
                return@onEachTick
            }
            val age = helper.tick - loweredAt
            if (age < 21) return@onEachTick
            helper.assertFalse(ClaimScenes.hasOrder(helper, charmander), "CD-LND-09: the order outlived the lower rank by $age ticks")
            helper.assertTrue(TestKit.lastMessage(b) == ClaimScenes.REFUSAL, "CD-LND-09: B got ${TestKit.messages(b)}")
            helper.succeed()
        }
    }

    /**
     * CD-LND-10: the online stranger P places a Work Mode pasture whose work area holds furnace A, in X's colony. For 400 ticks the scan
     * passes refuse furnace A, and P gets no chat or action-bar packet: the pasture check posts no event and counts no attempt.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = "mc_lnd10")
    fun cdLnd10PasturePlacerGetsNoPacket(helper: GameTestHelper) {
        val furnace = ClaimScenes.furnaceA(helper)
        val x = ClaimScenes.owner(helper, "x")
        val p = ClaimScenes.owner(helper, "p", rel = BlockPos(14, 2, 5))
        ColonyFixture.colony(helper, x.player)
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
     * CD-HRV-19: the break question of a Quick Worker and the one of a pasture both equal the colony's own decision on a break for each
     * online player, on wheat at age 7 in the colony. The setups: the default rank permissions, and BREAK_BLOCKS for the Friend rank,
     * which A gives it. The pasture's question gives the same answers after the players log out.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100, batch = "mc_hrv19")
    fun cdHrv19ColonyBreakOracle(helper: GameTestHelper) {
        val wheat = PastureOracle.wheat(helper)
        val scene = scene(helper)
        val permissions = scene.colony.permissions
        val friend = permissions.rankFriend
        val setups = listOf("wheat with the default ranks" to false, "wheat with BREAK_BLOCKS for Friends" to true)
        val answers = setups.map { (name, breaks) ->
            permissions.setPermission(friend, Action.BREAK_BLOCKS, breaks)
            PastureOracle.assertSame(helper, scene.players, wheat, name, PastureOracle.Question.BREAK) { colonyAllowsBreak(it, wheat) }
        }
        PastureOracle.logOut(helper, scene.players)
        for ((setup, online) in setups.zip(answers)) {
            permissions.setPermission(friend, Action.BREAK_BLOCKS, setup.second)
            PastureOracle.assertSameOffline(helper, online)
        }
        permissions.setPermission(friend, Action.BREAK_BLOCKS, false)
        helper.succeed()
    }

    /** The colony's own decision on a break of [pos] by [player]: the `BreakEvent` that the test posts is not canceled (CD-HRV-19). */
    private fun colonyAllowsBreak(player: ServerPlayer, pos: BlockPos): Boolean {
        val level = player.serverLevel()
        return !NeoForge.EVENT_BUS.post(BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled
    }
}
