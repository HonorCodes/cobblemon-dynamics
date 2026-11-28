package codes.honor.cobblemondynamics.compat.opac

import codes.honor.cobblemondynamics.compat.LandClaimCheck
import codes.honor.cobblemondynamics.compat.LandClaims
import codes.honor.cobblemondynamics.compat.TeamAllies
import codes.honor.cobblemondynamics.compat.TeamAllyCheck
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import xaero.pac.common.server.api.OpenPACServerAPI
import java.util.UUID

/**
 * The Open Parties and Claims checks of Quick Work (CD-QWK-28), pastures (CD-LND-07), and shared pasture parties (CD-TEAM-01 to
 * CD-TEAM-03, CD-TEAM-11). Only [register] is called from outside, behind `ModList.isLoaded("openpartiesandclaims")` (CD-QWK-33).
 */
object OpacCompat {
    fun register() {
        LandClaims.register("openpartiesandclaims", Check)
        TeamAllies.register("openpartiesandclaims", PartyCheck)
    }

    private object PartyCheck : TeamAllyCheck {
        override fun areTeammates(server: MinecraftServer, a: UUID, b: UUID): Boolean {
            val parties = OpenPACServerAPI.get(server).partyManager
            val partyA = parties.getPartyByMember(a) ?: parties.getPartyByOwner(a) ?: return false
            val partyB = parties.getPartyByMember(b) ?: parties.getPartyByOwner(b) ?: return false
            return partyA.id == partyB.id
        }
    }

    private object Check : LandClaimCheck {
        override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = !OpenPACServerAPI.get(level.server)
            .chunkProtection
            .onBlockInteraction(owner, InteractionHand.MAIN_HAND, ItemStack.EMPTY, level, pos, Direction.UP, false, false)

        /** Full access to the claim of the block's chunk, by UUID, which sends nothing (CD-LND-07, CD-LND-10). */
        override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
            val api = OpenPACServerAPI.get(level.server)
            val dimension = level.dimension().location()
            val x = SectionPos.blockToSectionCoord(pos.x)
            val z = SectionPos.blockToSectionCoord(pos.z)
            if (ownerId == null) return api.serverClaimsManager.get(dimension, x, z) == null
            return api.chunkProtection.hasChunkAccess(ownerId, dimension, x, z)
        }

        /** CD-HRV-18: the same interaction with `breaking` true, which asks about a break. Messages false sends nothing. */
        override fun mayBreak(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = !OpenPACServerAPI.get(level.server)
            .chunkProtection
            .onBlockInteraction(owner, InteractionHand.MAIN_HAND, ItemStack.EMPTY, level, pos, Direction.UP, true, false)

        /** CD-HRV-18: full access by UUID covers breaking, so a pasture's break question is its use question. */
        override fun ownerMayBreak(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean = ownerMayUse(ownerId, level, pos)
    }
}
