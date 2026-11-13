package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.compat.ftbchunks.FtbInteractRule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The steps of the right-click check of FTB Chunks, by the placer's UUID (CD-LND-05, CD-LND-06). Names no class of FTB. */
class FtbInteractRuleTest {
    private val stranger = FtbInteractRule.Facts(
        protectionDisabled = false,
        bypass = false,
        claimed = true,
        wildernessClosed = false,
        whitelisted = false,
        mode = FtbInteractRule.Mode.ALLIES,
        ally = false,
        memberOrBetter = false,
        hasOwner = true,
    )

    @Test
    fun `CD-LND-06 disable_protection and the admin bypass allow in every claim`() {
        assertTrue(FtbInteractRule.allows(stranger.copy(protectionDisabled = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(bypass = true)))
    }

    @Test
    fun `CD-LND-06 an unclaimed chunk allows unless the wilderness is closed, and the whitelist still allows there`() {
        assertTrue(FtbInteractRule.allows(stranger.copy(claimed = false)))
        assertFalse(FtbInteractRule.allows(stranger.copy(claimed = false, wildernessClosed = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(claimed = false, wildernessClosed = true, whitelisted = true)))
    }

    @Test
    fun `CD-LND-06 in a claim, the whitelist and then the block interact mode decide`() {
        assertFalse(FtbInteractRule.allows(stranger))
        assertTrue(FtbInteractRule.allows(stranger.copy(whitelisted = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(ally = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.PUBLIC)))
        assertFalse(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.PRIVATE, ally = true)))
        assertTrue(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.PRIVATE, memberOrBetter = true)))
    }

    @Test
    fun `CD-LND-05 a pasture with no placer gets no block of a claim, also a public one`() {
        val none = stranger.copy(hasOwner = false, mode = FtbInteractRule.Mode.PUBLIC)
        assertFalse(FtbInteractRule.allows(none))
        assertFalse(FtbInteractRule.allows(none.copy(whitelisted = true)))
        assertTrue(FtbInteractRule.allows(none.copy(claimed = false)))
    }

    /** The break question fills the same facts with the edit whitelist and the block edit mode (`Protection.EDIT_BLOCK`). */
    @Test
    fun `CD-HRV-17 the edit whitelist and the block edit mode decide a break`() {
        val ally = stranger.copy(ally = true)
        assertFalse(FtbInteractRule.allows(ally.copy(mode = FtbInteractRule.Mode.PRIVATE)), "an ally broke where the edit mode is private")
        assertTrue(FtbInteractRule.allows(ally.copy(mode = FtbInteractRule.Mode.ALLIES)))
        assertTrue(FtbInteractRule.allows(ally.copy(mode = FtbInteractRule.Mode.PRIVATE, whitelisted = true)))
        assertFalse(FtbInteractRule.allows(stranger.copy(mode = FtbInteractRule.Mode.ALLIES)))
    }
}
