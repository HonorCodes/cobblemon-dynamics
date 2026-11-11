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
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The claim checks against the real MineColonies 1.1.1419 (CD-LND-08, CD-LND-09, CD-LND-10, and CD-QWK-26 and CD-QWK-30 for
 * MineColonies). Player A owns a colony around the test's center: B is an Officer, C a Friend, D a stranger, whom the colony gives the
 * Neutral rank, and E is Hostile. The colony's own decision is the cancellation of a `RightClickBlock` that the test posts. Each test
 * has its own batch, because a colony claims the chunks around its center.
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

    /**
     * Each of [owners] sends out a Charmander at its own furnace in [furnaces]. Each gets an order within 100 ticks exactly where the
     * colony does not cancel its posted click, and otherwise no order on any tick and "You may not use Furnace" (CD-LND-09).
     */
    private fun followsTheClick(helper: GameTestHelper, owners: List<ServerPlayer>, furnaces: List<BlockPos>) {
        val expected = owners.zip(furnaces).map { (owner, rel) -> ColonyFixture.colonyAllows(owner, helper.absolutePos(rel)) }
        val charmanders = owners.map { TestKit.partyPokemon(it, "charmander level=10") }
        for ((i, owner) in owners.withIndex()) ClaimScenes.sendOutAt(helper, owner, charmanders[i], rel = furnaces[i])
        val start = helper.tick
        helper.onEachTick {
            var done = true
            for ((i, owner) in owners.withIndex()) {
                val name = owner.gameProfile.name
                val ordered = ClaimScenes.hasOrder(helper, charmanders[i])
                if (expected[i]) {
                    done = done && ordered
                } else {
                    helper.assertFalse(ordered, "CD-LND-09: $name got an order, although the colony cancels $name's click")
                    done = done && ClaimScenes.REFUSAL in TestKit.messages(owner)
                }
            }
            if (done) return@onEachTick helper.succeed()
            val late = owners.indices.filter { i ->
                if (expected[i]) !ClaimScenes.hasOrder(helper, charmanders[i]) else ClaimScenes.REFUSAL !in TestKit.messages(owners[i])
            }
            helper.assertTrue(
                helper.tick - start < 100,
                "CD-LND-09: after 100 ticks, wrong results for ${late.map {
                    owners[it].gameProfile.name
                }}",
            )
        }
    }

    /** CD-LND-09: the Owner, an Officer, a Friend, a stranger and a Hostile player each send out a Charmander at their own furnace. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = "mc_lnd09_ranks")
    fun cdLnd09QuickWorkFollowsTheOwnersClickInTheColony(helper: GameTestHelper) {
        val rows = listOf(3, 7, 11, 15, 19)
        val furnaces = rows.map { BlockPos(4, 2, it) }
        for (rel in furnaces) TestKit.furnace(helper, rel, ItemStack(Items.RAW_IRON, 8))
        val scene = scene(helper, rows)
        followsTheClick(helper, scene.players, furnaces)
    }

    /**
     * CD-LND-09: a stranger who is an operator of level 2 passes in creative mode, as that player's own click does, and in survival mode
     * gets no order.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = "mc_lnd09_operator")
    fun cdLnd09OperatorInCreativeModePassesAndInSurvivalDoesNot(helper: GameTestHelper) {
        val furnaces = listOf(BlockPos(4, 2, 7), BlockPos(4, 2, 15))
        for (rel in furnaces) TestKit.furnace(helper, rel, ItemStack(Items.RAW_IRON, 8))
        val x = ClaimScenes.owner(helper, "x")
        ColonyFixture.colony(helper, x.player)
        val operators = furnaces.mapIndexed { i, rel ->
            ClaimScenes.owner(helper, "op$i", rel = BlockPos(14, 2, rel.z), at = rel, permissionLevel = 2).player
        }
        operators[0].setGameMode(GameType.CREATIVE)
        helper.assertTrue(
            ColonyFixture.colonyAllows(operators[0], helper.absolutePos(furnaces[0])),
            "the colony cancels the creative operator",
        )
        helper.assertFalse(
            ColonyFixture.colonyAllows(operators[1], helper.absolutePos(furnaces[1])),
            "the colony allows the survival operator",
        )
        followsTheClick(helper, operators, furnaces)
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
}
