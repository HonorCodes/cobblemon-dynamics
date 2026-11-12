package codes.honor.cobblemondynamics.compat.ftbchunks

/**
 * The right-click rule of FTB Chunks 2101.1.23 for a player who is not a fake player, as RES7 read it from
 * `ClaimedChunkManagerImpl.shouldPreventInteraction` (CD-LND-06). Steps 1, 3, 4 and 5, in their order. Names no FTB class, so a unit
 * test reads it without FTB Chunks.
 */
object FtbInteractRule {
    /** The block interact mode of the team that claims the chunk (`PrivacyMode`). */
    enum class Mode { PUBLIC, ALLIES, PRIVATE }

    /** What the pasture check reads through the APIs of FTB Chunks and FTB Teams, for one placer and one block. */
    data class Facts(
        val protectionDisabled: Boolean,
        val bypass: Boolean,
        val claimed: Boolean,
        val wildernessClosed: Boolean,
        val whitelisted: Boolean,
        val mode: Mode?,
        val ally: Boolean,
        val memberOrBetter: Boolean,
        val hasOwner: Boolean,
    )

    fun allows(f: Facts): Boolean = when {
        f.protectionDisabled || f.bypass -> true

        !f.claimed -> !f.wildernessClosed || f.whitelisted

        // CD-LND-05: a pasture with no placer works no block in a land claim.
        !f.hasOwner -> false

        f.whitelisted -> true

        else -> when (f.mode) {
            Mode.PUBLIC -> true
            Mode.ALLIES -> f.ally
            else -> f.memberOrBetter
        }
    }
}
