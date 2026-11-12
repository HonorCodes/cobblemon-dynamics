package codes.honor.cobblemondynamics.compat.ftbchunks

import codes.honor.cobblemondynamics.compat.LandClaimCheck
import codes.honor.cobblemondynamics.compat.LandClaims
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties
import dev.ftb.mods.ftbchunks.api.FTBChunksTags
import dev.ftb.mods.ftbchunks.api.Protection
import dev.ftb.mods.ftbchunks.util.DimensionFilter
import dev.ftb.mods.ftblibrary.math.ChunkDimPos
import dev.ftb.mods.ftbteams.api.property.PrivacyMode
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import java.util.UUID

/**
 * The FTB Chunks checks of Quick Work (CD-QWK-27) and of pastures (CD-LND-06). Every class that names an FTB type sits in this
 * package, and only [register] is called from outside, behind `ModList.isLoaded("ftbchunks")`, so no FTB class loads without FTB
 * Chunks (CD-QWK-33).
 */
object FtbChunksCompat {
    fun register() = LandClaims.register("ftbchunks", Check)

    private object Check : LandClaimCheck {
        override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean {
            val api = FTBChunksAPI.api()
            // FTB Chunks reads the chunk and the block of player.level(). It shows its own action-bar text when it prevents.
            return owner.level() === level &&
                api.isManagerLoaded &&
                !api.manager.shouldPreventInteraction(owner, InteractionHand.MAIN_HAND, pos, Protection.INTERACT_BLOCK, null)
        }

        /**
         * FTB Chunks has no check by UUID, so this one takes the steps of its player check through the APIs of FTB Chunks and FTB
         * Teams (FtbInteractRule). It calls no check that shows a player its text or logs a prevented access (CD-LND-10).
         */
        override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
            val api = FTBChunksAPI.api()
            if (!api.isManagerLoaded) return false
            val manager = api.manager
            val data = manager.getChunk(ChunkDimPos(level, pos))?.teamData
            val team = data?.team
            return FtbInteractRule.allows(
                FtbInteractRule.Facts(
                    protectionDisabled = FTBChunksWorldConfig.DISABLE_PROTECTION.get(),
                    bypass = ownerId != null && manager.getBypassProtection(ownerId),
                    claimed = data != null,
                    // A player's own check also reads FTB Ranks, which a pasture with no player cannot ask.
                    wildernessClosed = FTBChunksWorldConfig.noWilderness(null) ||
                        DimensionFilter.isNoWildernessDimension(level.dimension()),
                    whitelisted = level.getBlockState(pos).`is`(FTBChunksTags.Blocks.INTERACT_WHITELIST_TAG),
                    mode = team?.getProperty(FTBChunksProperties.BLOCK_INTERACT_MODE)?.let(::mode),
                    ally = ownerId != null && data != null && data.isAlly(ownerId),
                    memberOrBetter = ownerId != null && team != null && team.getRankForPlayer(ownerId).isMemberOrBetter,
                    hasOwner = ownerId != null,
                ),
            )
        }

        /**
         * `PrivacyMode` has a public field `name` that holds the lower-case serialized name, which Kotlin's `name` reads instead of the
         * enum constant's name, so the mode maps by constant.
         */
        private fun mode(mode: PrivacyMode): FtbInteractRule.Mode = when (mode) {
            PrivacyMode.PUBLIC -> FtbInteractRule.Mode.PUBLIC
            PrivacyMode.ALLIES -> FtbInteractRule.Mode.ALLIES
            PrivacyMode.PRIVATE -> FtbInteractRule.Mode.PRIVATE
        }
    }
}
