package codes.honor.cobblemondynamics.opactest

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import xaero.pac.common.parties.party.member.PartyMemberRank
import xaero.pac.common.server.api.OpenPACServerAPI
import xaero.pac.common.server.parties.party.api.IServerPartyAPI
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions

/** Parties, claims, the player config and the own check of Open Parties and Claims for the test players. */
object OpacFixture {
    private fun api(level: ServerLevel) = OpenPACServerAPI.get(level.server)

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
