package codes.honor.cobblemondynamics.compat.ftbchunks

import codes.honor.cobblemondynamics.compat.LandClaims
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI
import dev.ftb.mods.ftbchunks.api.Protection
import net.minecraft.world.InteractionHand

/**
 * The FTB Chunks check of Quick Work (CD-QWK-27). Every class that names an FTB type sits in this package, and only [register] is
 * called from outside, behind `ModList.isLoaded("ftbchunks")`, so no FTB class loads without FTB Chunks (CD-QWK-33).
 */
object FtbChunksCompat {
    fun register() = LandClaims.register("ftbchunks") { owner, level, pos ->
        val api = FTBChunksAPI.api()
        // FTB Chunks reads the chunk and the block of player.level(). It shows its own action-bar text when it prevents.
        owner.level() === level &&
            api.isManagerLoaded &&
            !api.manager.shouldPreventInteraction(owner, InteractionHand.MAIN_HAND, pos, Protection.INTERACT_BLOCK, null)
    }
}
