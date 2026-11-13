package codes.honor.cobblemondynamics.test.claims

import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import java.util.UUID

/**
 * CD-LND-06 to CD-LND-08, CD-HRV-17 to CD-HRV-19: the pasture check by UUID must give the answer of the claim mod's own check of the
 * online player, and the same answer after that player logs out. For the break question, the check of a Quick Worker must also give that
 * answer to the online player. Names no class of a claim mod: the test gives the claim mod's own check as [assertSame]'s `own`.
 */
object PastureOracle {
    /**
     * The question that a test compares with the claim mod: its pasture check by the placer's UUID, and its check of a Quick Worker for
     * the online owner, which is null where the rows compare the pasture check only.
     */
    enum class Question(
        val pasture: (UUID?, ServerLevel, BlockPos) -> Boolean,
        val quickWork: ((ServerPlayer, ServerLevel, BlockPos) -> Boolean)?,
    ) {
        /** The use question of CD-LND-05, which CD-LND-06 to CD-LND-08 compare for pastures only. */
        USE(LandClaims::ownerMayUse, null),

        /** The break question of CD-HRV-16, which CD-HRV-17 to CD-HRV-19 compare for pastures and Quick Workers. */
        BREAK(LandClaims::ownerMayBreak, LandClaims::mayBreak),
    }

    /** The answers of the claim mod's own check in one setup, by player, at [pos], for [question]. */
    class Answers(val setup: String, val pos: BlockPos, val own: Map<ServerPlayer, Boolean>, val question: Question)

    /**
     * For each of [players] at [pos], the pasture check of [question] equals [own], and so does its Quick Work check where it has one.
     * Returns the answers, for [assertSameOffline] after the logout.
     */
    fun assertSame(
        helper: GameTestHelper,
        players: List<ServerPlayer>,
        pos: BlockPos,
        setup: String,
        question: Question = Question.USE,
        own: (ServerPlayer) -> Boolean,
    ): Answers {
        val answers = LinkedHashMap<ServerPlayer, Boolean>()
        for (player in players) {
            val expected = own(player)
            val pasture = question.pasture(player.uuid, helper.level, pos)
            helper.assertTrue(
                pasture == expected,
                "$setup: the pasture check says $pasture for ${name(player)}, the claim mod says $expected",
            )
            val quickWork = question.quickWork?.invoke(player, helper.level, pos)
            if (quickWork != null) {
                helper.assertTrue(
                    quickWork == expected,
                    "$setup: the Quick Work check says $quickWork for ${name(player)}, the claim mod says $expected",
                )
            }
            answers[player] = expected
        }
        return Answers(setup, pos, answers, question)
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
            val pasture = answers.question.pasture(player.uuid, helper.level, answers.pos)
            val who = name(player)
            val message = "${answers.setup}, after the logout: the pasture check says $pasture for $who, the claim mod said $expected"
            helper.assertTrue(pasture == expected, message)
        }
    }

    /**
     * The crop of the break questions (CD-HRV-17 to CD-HRV-19): wheat at age 7 on farmland in the floor, beside [ClaimScenes.A] and in
     * the chunk of A, which the claim or colony of each run holds. Furnace A stays. Returns the wheat's position.
     */
    fun wheat(helper: GameTestHelper): BlockPos {
        val chunk = ChunkPos(helper.absolutePos(ClaimScenes.A))
        val rel = listOf(ClaimScenes.A.east(), ClaimScenes.A.west()).first { ChunkPos(helper.absolutePos(it)) == chunk }
        helper.setBlock(rel.below(), Blocks.FARMLAND)
        helper.setBlock(rel, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7))
        return helper.absolutePos(rel)
    }

    private fun name(player: ServerPlayer): String = player.gameProfile.name
}
