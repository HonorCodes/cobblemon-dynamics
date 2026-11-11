package codes.honor.cobblemondynamics.ftbtest

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties
import dev.ftb.mods.ftbchunks.api.Protection
import dev.ftb.mods.ftblibrary.math.ChunkDimPos
import dev.ftb.mods.ftbteams.api.property.PrivacyMode
import dev.ftb.mods.ftbteams.data.PartyTeam
import dev.ftb.mods.ftbteams.data.TeamManagerImpl
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ChunkPos

/** Parties, claims and the own check of FTB Chunks for the test players. FTB Teams makes a player's own team only at login (RES6). */
object FtbChunksFixture {
    fun login(vararg players: ServerPlayer) = players.forEach { TeamManagerImpl.INSTANCE.playerLoggedIn(it, it.uuid, it.gameProfile.name) }

    fun party(owner: ServerPlayer, members: List<ServerPlayer>, allies: List<ServerPlayer>): PartyTeam {
        val party = TeamManagerImpl.INSTANCE.createParty(owner, "qw_${owner.uuid.toString().take(8)}")
        members.forEach { party.join(it) }
        if (allies.isNotEmpty()) party.addAlly(owner.createCommandSourceStack(), allies.map { it.gameProfile })
        return party
    }

    /** The claim of [player]'s team, which is its party once it joined one. */
    fun claim(player: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        val result = FTBChunksAPI.api().claimAsPlayer(player, level.dimension(), ChunkPos(pos), false)
        if (!result.isSuccess) {
            throw GameTestAssertException(
                "FTB Chunks refused the claim of ${player.gameProfile.name}: ${result.resultId}",
            )
        }
    }

    fun unclaim(level: ServerLevel, pos: BlockPos) {
        FTBChunksAPI.api().manager.getChunk(ChunkDimPos(level, pos))?.unclaim(level.server.createCommandSourceStack(), false)
    }

    fun disband(party: PartyTeam, level: ServerLevel) {
        party.forceDisband(level.server.createCommandSourceStack())
    }

    fun mode(party: PartyTeam, mode: PrivacyMode) = party.setProperty(FTBChunksProperties.BLOCK_INTERACT_MODE, mode)

    /** The check of FTB Chunks for a right-click with an empty main hand, which shows the player its own text when it prevents. */
    fun allowsClick(player: ServerPlayer, pos: BlockPos): Boolean =
        !FTBChunksAPI.api().manager.shouldPreventInteraction(player, InteractionHand.MAIN_HAND, pos, Protection.INTERACT_BLOCK, null)
}
