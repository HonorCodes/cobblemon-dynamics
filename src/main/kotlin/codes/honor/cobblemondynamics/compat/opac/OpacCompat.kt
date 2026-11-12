package codes.honor.cobblemondynamics.compat.opac

import codes.honor.cobblemondynamics.compat.LandClaimCheck
import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import xaero.pac.common.server.api.OpenPACServerAPI
import java.util.UUID

/**
 * The Open Parties and Claims checks of Quick Work (CD-QWK-28) and of pastures (CD-LND-07). Only [register] is called from outside,
 * behind `ModList.isLoaded("openpartiesandclaims")` (CD-QWK-33).
 */
object OpacCompat {
    fun register() = LandClaims.register("openpartiesandclaims", Check)

    private object Check : LandClaimCheck {
        // An empty hand with breaking false skips the item checks, and messages false sends nothing (CD-QWK-32).
        override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = !OpenPACServerAPI.get(level.server)
            .chunkProtection
            .onBlockInteraction(owner, InteractionHand.MAIN_HAND, ItemStack.EMPTY, level, pos, Direction.UP, false, false)

        /** Full access to the claim of the block's chunk, by UUID, which sends nothing (CD-LND-07, CD-LND-10). */
        override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
            val api = OpenPACServerAPI.get(level.server)
            val dimension = level.dimension().location()
            val x = SectionPos.blockToSectionCoord(pos.x)
            val z = SectionPos.blockToSectionCoord(pos.z)
            // A pasture with no placer gets no block of a claim (CD-LND-05).
            if (ownerId == null) return api.serverClaimsManager.get(dimension, x, z) == null
            return api.chunkProtection.hasChunkAccess(ownerId, dimension, x, z)
        }
    }
}
