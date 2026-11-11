package codes.honor.cobblemondynamics.compat.opac

import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import xaero.pac.common.server.api.OpenPACServerAPI

/**
 * The Open Parties and Claims check of Quick Work (CD-QWK-28). Only [register] is called from outside, behind
 * `ModList.isLoaded("openpartiesandclaims")` (CD-QWK-33).
 */
object OpacCompat {
    fun register() = LandClaims.register("openpartiesandclaims") { owner, level, pos ->
        // An empty hand with breaking false skips the item checks, and messages false sends nothing (CD-QWK-32).
        !OpenPACServerAPI.get(level.server).chunkProtection
            .onBlockInteraction(owner, InteractionHand.MAIN_HAND, ItemStack.EMPTY, level, pos, Direction.UP, false, false)
    }
}
