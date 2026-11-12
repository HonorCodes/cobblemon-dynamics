package codes.honor.cobblemondynamics.test.claims

import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

/**
 * CD-LND-06 to CD-LND-08: the pasture check by UUID must give the answer of the claim mod's own check of the online player, and the same
 * answer after that player logs out. Names no class of a claim mod: the test gives the claim mod's own check as [assertSame]'s `own`.
 */
object PastureOracle {
    /** The answers of the claim mod's own check in one setup, by player, at [pos]. */
    class Answers(val setup: String, val pos: BlockPos, val own: Map<ServerPlayer, Boolean>)

    /** For each of [players] at [pos], the pasture check equals [own]. Returns the answers, for [assertSameOffline] after the logout. */
    fun assertSame(
        helper: GameTestHelper,
        players: List<ServerPlayer>,
        pos: BlockPos,
        setup: String,
        own: (ServerPlayer) -> Boolean,
    ): Answers {
        val answers = LinkedHashMap<ServerPlayer, Boolean>()
        for (player in players) {
            val expected = own(player)
            val pasture = LandClaims.ownerMayUse(player.uuid, helper.level, pos)
            helper.assertTrue(
                pasture == expected,
                "$setup: the pasture check says $pasture for ${name(player)}, the claim mod says $expected",
            )
            answers[player] = expected
        }
        return Answers(setup, pos, answers)
    }

    /** Each of [players] leaves as at a real logout, through `PlayerList.remove`, which posts the logout event. */
    fun logOut(helper: GameTestHelper, players: Collection<ServerPlayer>) {
        val list = helper.level.server.playerList
        for (player in players) list.remove(player)
        for (player in players) helper.assertTrue(list.getPlayer(player.uuid) == null, "${name(player)} is still online after the logout")
    }

    /** After the logout, the pasture check gives each player the answer of [answers], which the claim mod gave the online player. */
    fun assertSameOffline(helper: GameTestHelper, answers: Answers) {
        for ((player, expected) in answers.own) {
            val pasture = LandClaims.ownerMayUse(player.uuid, helper.level, answers.pos)
            val who = name(player)
            val message = "${answers.setup}, after the logout: the pasture check says $pasture for $who, the claim mod said $expected"
            helper.assertTrue(pasture == expected, message)
        }
    }

    private fun name(player: ServerPlayer): String = player.gameProfile.name
}
