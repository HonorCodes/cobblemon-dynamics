package codes.honor.cobblemondynamics.opactest

import codes.honor.cobblemondynamics.test.TestKit
import codes.honor.cobblemondynamics.test.claims.ClaimScenes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import xaero.pac.common.parties.party.member.PartyMemberRank
import xaero.pac.common.server.ServerData
import xaero.pac.common.server.api.OpenPACServerAPI
import xaero.pac.common.server.parties.party.api.IServerPartyAPI
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions
import xaero.pac.common.server.player.data.ServerPlayerData
import xaero.pac.common.server.player.data.api.ServerPlayerDataAPI

/** The login, parties, claims, the player config and the own check of Open Parties and Claims for the test players. */
object OpacFixture {
    private fun api(level: ServerLevel) = OpenPACServerAPI.get(level.server)

    /** An owner of [ClaimScenes.owner] that has logged in to Open Parties and Claims, as every online player of a real server has. */
    fun owner(helper: GameTestHelper, name: String, rel: BlockPos = ClaimScenes.OWNER): TestKit.PlainOwner =
        ClaimScenes.owner(helper, name, rel).also { logIn(it.player) }

    /**
     * Handles [player]'s login as `ServerPlayerDataAPI.from` does for a player whose connection is open. A test player's connection has
     * no channel, so Open Parties and Claims takes it for a connection that is still connecting and never handles the login, and its
     * first sync of a party to that player throws.
     */
    private fun logIn(player: ServerPlayer) {
        val data = ServerPlayerDataAPI.from(player) as ServerPlayerData
        if (data.hasHandledLogin()) return
        data.setHandledLogin(true)
        val serverData = ServerData.from(player.server)
        serverData.playerLoginHandler.handlePreWorldJoin(player, serverData)
        serverData.playerWorldJoinHandler.onWorldJoin(serverData, player.serverLevel(), player)
        serverData.playerLoginHandler.handlePostWorldJoin(player, serverData)
    }

    fun party(owner: ServerPlayer, members: List<ServerPlayer>): IServerPartyAPI {
        val party = api(owner.serverLevel()).partyManager.createPartyForOwner(owner)
            ?: throw GameTestAssertException("Open Parties and Claims made no party for ${owner.gameProfile.name}")
        members.forEach { party.addMember(it.uuid, PartyMemberRank.MEMBER, it.gameProfile.name) }
        return party
    }

    fun removeParty(level: ServerLevel, party: IServerPartyAPI) = api(level).partyManager.removeParty(party)

    /** The sub-config index -1 is the owner's main config (`PlayerConfig.getEffectiveSubConfig(int)`). */
    fun claim(owner: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        val chunk = ChunkPos(pos)
        val claims = api(level).serverClaimsManager
        claims.claim(level.dimension().location(), owner.uuid, -1, chunk.x, chunk.z, false)
        val holder = claims.get(level.dimension().location(), chunk.x, chunk.z)?.playerId
        if (holder != owner.uuid) throw GameTestAssertException("the chunk of $pos is claimed by $holder, not by ${owner.gameProfile.name}")
    }

    fun unclaim(level: ServerLevel, pos: BlockPos) {
        val chunk = ChunkPos(pos)
        api(level).serverClaimsManager.unclaim(level.dimension().location(), chunk.x, chunk.z)
    }

    /** The owner's `claims.protection.exceptions.fullAccess`, `P` by default, which the default player config lets players set. */
    fun fullAccess(owner: ServerPlayer, value: String) {
        val config = api(owner.serverLevel()).playerConfigManager.getLoadedConfig(owner.uuid)
        val result = config.tryToSet(PlayerConfigOptions.FULL_ACCESS, value)
        if (result != IPlayerConfigAPI.SetResult.SUCCESS) throw GameTestAssertException("fullAccess $value: $result")
    }

    /** The check of Open Parties and Claims for a right-click with an empty main hand, with its messages off. */
    fun allowsClick(player: ServerPlayer, pos: BlockPos): Boolean = !api(player.serverLevel()).chunkProtection
        .onBlockInteraction(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, player.serverLevel(), pos, Direction.UP, false, false)
}
