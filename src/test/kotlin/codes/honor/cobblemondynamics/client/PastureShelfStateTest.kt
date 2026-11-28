package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.PasturePanelStatePayload
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PastureShelfStateTest {
    @Test
    fun `CD-UI-02 shelf stays hidden before first panel state arrives`() {
        val state = PastureShelfState.from(panel = null, folded = false, watchingArea = false)
        assertFalse(state.visible)
    }

    @Test
    fun `CD-SHF-03 CD-SHF-04 unauthorized viewer on active locked pasture can toggle Show Work Area and sees disabled reasons`() {
        val payload = PasturePanelStatePayload(
            workMode = true,
            canToggle = false,
            entries = emptyList(),
            pos = BlockPos(10, 64, 10),
            locked = true,
            radius = 8,
            maxHorizontalRadius = 16,
            canLock = false,
        )
        val state = PastureShelfState.from(payload, folded = false, watchingArea = true)
        assertTrue(state.visible)
        assertFalse(state.workMode.enabled)
        assertEquals(TextSpec.Key(PastureShelfState.REASON_LOCKED), state.workMode.disabledReason)

        assertFalse(state.workRadius.enabled)
        assertEquals(TextSpec.Key(PastureShelfState.REASON_LOCKED), state.workRadius.disabledReason)

        assertTrue(state.showArea.enabled)
        assertTrue(state.showArea.activeValue)
        assertNull(state.showArea.disabledReason)

        assertFalse(state.accessLock.enabled)
        assertEquals(TextSpec.Key(PastureShelfState.REASON_NOT_OWNER), state.accessLock.disabledReason)
    }

    @Test
    fun `CD-SHF-02 CD-SHF-04 radius stepper caps steps at maxHorizontalRadius and disables Show Work Area when Work Mode is off`() {
        val payload = PasturePanelStatePayload(
            workMode = false,
            canToggle = true,
            entries = emptyList(),
            pos = BlockPos(4, 64, 4),
            locked = false,
            radius = 12,
            maxHorizontalRadius = 12,
            canLock = true,
        )
        val state = PastureShelfState.from(payload, folded = false, watchingArea = true)
        assertEquals(listOf(4, 8, 12), state.allowedRadiusSteps)
        assertEquals(4, state.nextRadius)
        assertTrue(state.workRadius.enabled)

        assertFalse(state.showArea.enabled)
        assertFalse(state.showArea.activeValue)
        assertEquals(TextSpec.Key(PastureShelfState.REASON_WORK_MODE_OFF), state.showArea.disabledReason)

        val cappedAtFour = PastureShelfState.from(payload.copy(radius = 4, maxHorizontalRadius = 4), folded = false, watchingArea = false)
        assertFalse(cappedAtFour.workRadius.enabled)
        assertNotNull(cappedAtFour.workRadius.disabledReason)

        val aboveSingleAllowedStep = PastureShelfState.from(
            payload.copy(radius = 6, maxHorizontalRadius = 6),
            folded = false,
            watchingArea = false,
        )
        assertEquals(listOf(4), aboveSingleAllowedStep.allowedRadiusSteps)
        assertEquals(4, aboveSingleAllowedStep.nextRadius)
        assertTrue(aboveSingleAllowedStep.workRadius.enabled)
        assertNull(aboveSingleAllowedStep.workRadius.disabledReason)
    }

    @Test
    fun `pasture at BlockPos ZERO behaves identically to any other coordinate`() {
        val zeroPayload = PasturePanelStatePayload(
            workMode = true,
            canToggle = true,
            entries = emptyList(),
            pos = BlockPos.ZERO,
            locked = false,
            radius = 8,
            maxHorizontalRadius = 16,
            canLock = true,
        )
        val state = PastureShelfState.from(zeroPayload, folded = false, watchingArea = true)
        assertTrue(state.visible)
        assertTrue(state.showArea.enabled)
        assertTrue(state.showArea.activeValue)
        assertEquals(12, state.nextRadius)
    }
}
